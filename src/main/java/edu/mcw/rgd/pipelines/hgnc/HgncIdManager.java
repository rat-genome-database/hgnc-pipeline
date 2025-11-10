package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map;

/**
 * @author hsnalabolu
 * @since 07/2/2020
 */
public class HgncIdManager {
    private String version;
    private String hgncIdFile;
    private String vgncIdFile;
    private String dogVgncIdFile;
    private String pigVgncIdFile;
    private int refKey;
    private Dao dao;
    Logger logDb = LogManager.getLogger("hgnc_ids");
    Logger logNoMatch = LogManager.getLogger("no_match");
    Logger logMultiMatch = LogManager.getLogger("multi_match");

    static private final String NOMEN_SOURCE = "HGNC";

    private boolean CLEAR_NOMEN_SOURCE_FOR_ORPHANS = false;

    public void run( Dao dao ) throws Exception {

        this.dao = dao;

        long startTime = System.currentTimeMillis();

        logDb.info(getVersion());
        logDb.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logDb.info("   started at "+sdt.format(new Date(startTime)));

        if( dao.isReadOnlyMode() ) {
            logDb.warn("WARNING! dao operations are performed in read-only mode");
        }

        run( 0 );
        run( SpeciesType.HUMAN );

        logDb.info("");
        logDb.info("=== OK ===      elapsed "+Utils.formatElapsedTime(System.currentTimeMillis(), startTime));
    }

    public void run(int speciesTypeKey) throws Exception {

        String speciesName = SpeciesType.getCommonName(speciesTypeKey);

        List<HgncGene> hgncGenes;
        if( speciesTypeKey == SpeciesType.HUMAN ) {
            hgncGenes = parseHgncInputFile();
        } else {
            hgncGenes = parseVgncInputFile();
        }

        HashMap<Integer,List<HgncGene>> geneMap = qc(hgncGenes);

        showMatchCounts(geneMap);

        int nomenEvents = 0;
        int genesModified = 0;

        for( int geneRgdId: geneMap.keySet() ) {

            List<HgncGene> genes = geneMap.get(geneRgdId);
            if( genes.size()>1 ) {
                String accIds = genes.get(0).hgncId;
                for( int i=1; i<genes.size(); i++ ) {
                    accIds += ", " + genes.get(i).hgncId;
                }
                logDb.warn("conflict: single gene RGD:"+geneRgdId+" matches multiple ids: "+accIds);
                continue;
            }

            HgncGene g = genes.get(0);
            String previousSymbol = g.gene.getSymbol();
            String previousName = g.gene.getName();

            if( Utils.stringsAreEqual(g.symbol, previousSymbol)
                    && Utils.stringsAreEqual(g.name, previousName)
                    && Utils.stringsAreEqual(g.gene.getNomenSource(), NOMEN_SOURCE) ) {

                // everything up-to-date: continue to next line
                continue;
            }

            g.gene.setSymbol(g.symbol);
            g.gene.setName(g.name);
            g.gene.setNomenSource(NOMEN_SOURCE);
            if( updateGene(g.gene, previousSymbol, previousName, g.matchBy) ) {
                nomenEvents++;
            }
            genesModified++;
        }

        logDb.info("   Number of "+ speciesName+" Genes Updated: "+ genesModified);
        logDb.info("   Number of "+ speciesName+" Nomen Events created: "+ nomenEvents);


        Set<Integer> geneRgdIdsWithHgncNomenSource = dao.getGeneRgdIdsForNomenSource(speciesTypeKey, NOMEN_SOURCE);
        Set<Integer> orphanedRgdIdsWithHgncNomenSource = new HashSet<>(geneRgdIdsWithHgncNomenSource);
        orphanedRgdIdsWithHgncNomenSource.removeAll( geneMap.keySet() );
        logDb.info("   Orphaned RGD IDs with HGNC nomen source: "+orphanedRgdIdsWithHgncNomenSource.size() );
        if( CLEAR_NOMEN_SOURCE_FOR_ORPHANS ) {
            int counfOfNomenSourceCleared = 0;
            for (Integer rgdId : orphanedRgdIdsWithHgncNomenSource) {
                if (dao.clearNomenSourceForGene(rgdId, NOMEN_SOURCE)) {
                    counfOfNomenSourceCleared++;
                }
            }
            logDb.info("   Orphaned RGD IDs with HGNC nomen source cleared (set to NULL): " + counfOfNomenSourceCleared);
        } else {
            if( orphanedRgdIdsWithHgncNomenSource.size()>0 ) {
                logDb.info("   WARN! SUPPRESSED CLEARING OF NOMEN SOURCE FOR ORPHANS!");
            }
        }
    }

    void showMatchCounts( HashMap<Integer,List<HgncGene>> geneMap ) {

        Map<String, Integer> matchCounts = new TreeMap<>();

        for( List<HgncGene> list: geneMap.values() ) {

            for( HgncGene g: list ) {

                // match tier 1, by HGNC:9999: 1
                // match tier 2, by NCBI:100130620 (HGNC:55137): 1
                // match tier 3, by ENSEMBL:xxxxxx (HGNC:55137): 1
                int colonPos = g.matchBy.indexOf(':');
                String matchBy = g.matchBy.substring(0, colonPos);

                Integer hitCount = matchCounts.get(matchBy);
                if( hitCount==null ) {
                    hitCount = 1;
                } else {
                    hitCount++;
                }
                matchCounts.put(matchBy, hitCount);
            }
        }

        for( Map.Entry<String, Integer> entry: matchCounts.entrySet() ) {
            logDb.info("   "+entry.getKey()+": "+entry.getValue());
        }
    }

    public List<HgncGene> parseHgncInputFile() throws Exception {

        String speciesName = SpeciesType.getCommonName(SpeciesType.HUMAN);
        int humanTaxonId = SpeciesType.getTaxonomicId(SpeciesType.HUMAN);

        logDb.info("");
        logDb.info(speciesName.toUpperCase()+" HGNC file processing ...");

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getHgncIdFile());

        downloader.setLocalFile("data/hgnc_ids.txt");
        downloader.setPrependDateStamp(true);
        downloader.setUseCompression(true);
        String localFile = downloader.downloadNew();

        logDb.info("   file downloaded to "+localFile);

        List<HgncGene> hgncGenes = new ArrayList<>();

        Collection<String> dataLines = getUniqueDataLines(localFile);
        for( String line: dataLines ) {

            String[] cols = line.split("[\\t]", -1);
            HgncGene hgncGene = new HgncGene();
            hgncGene.taxonId = humanTaxonId;
            hgncGene.hgncId = cols[0].trim();
            hgncGene.symbol = cols[1].trim();
            hgncGene.name = cols[2].trim();
            hgncGene.ncbiId = cols[18].trim();
            hgncGene.ensemblId = cols[19].trim();

            hgncGenes.add(hgncGene);
        }

        return hgncGenes;
    }

    public List<HgncGene> parseVgncInputFile() throws Exception {

        logDb.info("");
        logDb.info("VGNC file processing ...");

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getVgncIdFile());

        downloader.setLocalFile("data/vgnc_ids.txt");
        downloader.setPrependDateStamp(true);
        downloader.setUseCompression(true);
        String localFile = downloader.downloadNew();

        logDb.info("   file downloaded to "+localFile);

        List<HgncGene> hgncGenes = new ArrayList<>();

        Collection<String> dataLines = getUniqueDataLines(localFile);
        for( String line: dataLines ) {

            // columns in the file, as of Jan 2025
            // 0. taxon_id
            // 1. vgnc_id
            // 2. symbol
            // 3. name
            // 4. locus_group
            // 5. locus_type
            // 6. status
            // 7. location
            // 8. location_sortable:
            // 9. alias_symbol
            // 10. alias_name
            // 11. prev_symbol
            // 12. prev_name
            // 13. gene_family
            // 14. gene_family_id
            // 15. date_approved_reserved
            // 16. date_symbol_changed
            // 17. date_name_changed
            // 18. date_modified
            // 19. ncbi_id
            // 20. ensembl_gene_id
            // 21. uniprot_ids
            // 22. pubmed_id
            // 23. horde_id
            // 24. hgnc_orthologs
            String[] cols = line.split("[\\t]", -1);
            HgncGene hgncGene = new HgncGene();
            hgncGene.taxonId = Integer.parseInt(cols[0].trim());
            hgncGene.hgncId = cols[1].trim();
            hgncGene.symbol = cols[2].trim();
            hgncGene.name = cols[3].trim();
            hgncGene.ncbiId = cols[19].trim();
            hgncGene.ensemblId = cols[20].trim();

            hgncGenes.add(hgncGene);
        }

        return hgncGenes;
    }

    Collection<String> getUniqueDataLines(String fileName) throws IOException {

        int dataLinesRead = 0;

        Set<String> uniqueDataLines = new HashSet<>();

        BufferedReader reader = Utils.openReader(fileName);
        String line = reader.readLine(); // skip header line
        while( (line=reader.readLine())!=null ) {
            uniqueDataLines.add(line);
            dataLinesRead++;
        }

        reader.close();

        if( dataLinesRead != uniqueDataLines.size() ) {
            logDb.info("  removed "+(dataLinesRead-uniqueDataLines.size())+" duplicate lines from input file");
        }
        return uniqueDataLines;
    }

    HashMap<Integer,List<HgncGene>> qc(List<HgncGene> hgncGenes) throws Exception {

        HashMap<Integer, List<HgncGene>> resultMap = new HashMap<>();

        Map<Integer, Integer> taxonId2SpeciesTypeKeyMap = new HashMap<>();
        for( int sp: SpeciesType.getSpeciesTypeKeys() ) {
            if( SpeciesType.isSearchable(sp) ) {
                taxonId2SpeciesTypeKeyMap.put( SpeciesType.getTaxonomicId(sp), sp );
            }
        }
        Map<Integer, Integer> speciesTypeKeyCounts = new HashMap<>();

        int nonRgdSpecies = 0;
        int conflictCount = 0;

        for( HgncGene g: hgncGenes ) {

            Integer sp = taxonId2SpeciesTypeKeyMap.get(g.taxonId);
            if( sp != null ) {
                g.speciesTypeKey = sp;
            } else {
                nonRgdSpecies++;
                continue;
            }

            g.matchBy = "";
            List<Gene> existingGenes;
            if( g.speciesTypeKey==SpeciesType.HUMAN ) {
                existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_HGNC, g.hgncId);
            } else {
                existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_VGNC, g.hgncId);
            }
            if( existingGenes.size() == 1 ) {
                g.matchBy = "match tier 1, by "+g.hgncId;
            } else {
                if( g.ncbiId != null ) {
                    existingGenes = dao.getActiveGenesByNcbiId(g.ncbiId);
                    if( existingGenes.size()==1 ) {
                        g.matchBy = "match tier 2, by NCBI:"+g.ncbiId+" ("+g.hgncId+")";
                    }
                }
                if( existingGenes.size() != 1 && g.ensemblId != null ) {
                    existingGenes = dao.getActiveGenesByEnsemblId(g.ensemblId);
                    if (existingGenes.size() == 1) {
                        g.matchBy = "match tier 3, by ENSEMBL:" + g.ensemblId+" ("+g.hgncId+")";
                    }
                }
            }

            if( existingGenes.size() != 1 ){
                String msg = g.hgncId+" "+g.symbol+" ["+g.name+"]";
                if( g.ncbiId!=null ) {
                    msg += " NCBI:"+g.ncbiId;
                }
                if( g.ensemblId!=null ) {
                    msg += " Ensembl:"+g.ensemblId;
                }
                if( existingGenes.isEmpty() ) {
                    logNoMatch.debug(msg);
                } else {
                    logMultiMatch.debug(msg);
                    for( Gene gg: existingGenes ) {
                        logMultiMatch.debug("    RGD:"+gg.getRgdId()+" "+gg.getSymbol()+" ["+gg.getName()+"]");
                    }
                }
                conflictCount++;
            } else {
                Gene gene = existingGenes.get(0);
                g.gene = gene;
                List<HgncGene> genes = resultMap.get(gene.getRgdId());
                if( genes==null ) {
                    genes = new ArrayList<>();
                    resultMap.put(gene.getRgdId(), genes);
                }
                genes.add(g);

                Integer count = speciesTypeKeyCounts.get(g.speciesTypeKey);
                if( count == null ) {
                    count = 1;
                } else {
                    count++;
                }
                speciesTypeKeyCounts.put( g.speciesTypeKey, count );
            }
        }

        logDb.info("   Lines with non-RGD species: "+nonRgdSpecies);
        logDb.info("   Lines with RGD species that did not match a single gene in RGD: "+conflictCount);

        for( Map.Entry<Integer, Integer> entry: speciesTypeKeyCounts.entrySet() ) {
            String speciesName = SpeciesType.getCommonName(entry.getKey());
            logDb.info("   Lines for species  "+speciesName+": "+entry.getValue());
        }

        return resultMap;
    }

    /** return true if a nomen event has been generated
     */
    boolean updateGene(Gene gene, String oldSymbol, String oldName, String notes) throws Exception {

        dao.updateGene(gene);

        if( !Utils.stringsAreEqualIgnoreCase(gene.getSymbol(), oldSymbol) || !Utils.stringsAreEqualIgnoreCase(oldName, gene.getName()) ) {
            NomenclatureEvent event = new NomenclatureEvent();
            event.setRgdId(gene.getRgdId());
            event.setSymbol(gene.getSymbol());
            event.setName(gene.getName());
            event.setRefKey(String.valueOf(getRefKey()));
            event.setNomenStatusType("PROVISIONAL");
            event.setDesc("Symbol and/or name change");
            event.setEventDate(new Date());
            event.setOriginalRGDId(gene.getRgdId());
            event.setPreviousName(oldName);
            event.setPreviousSymbol(oldSymbol);
            event.setNotes(notes);
            dao.insertNomenclatureEvent(event);

            Alias aliasData = new Alias();
            aliasData.setNotes("Added by HGNC pipeline");
            aliasData.setRgdId(gene.getRgdId());
            if (!oldSymbol.equalsIgnoreCase(gene.getSymbol())) {
                aliasData.setValue(oldSymbol);
                aliasData.setTypeName("old_gene_symbol");
                dao.insertAlias(aliasData);
            }
            if (oldName != null && !oldName.equalsIgnoreCase(gene.getName())) {
                aliasData.setValue(oldName);
                aliasData.setTypeName("old_gene_name");
                dao.insertAlias(aliasData);
            }
            return true;
        }
        return false;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setHgncIdFile(String hgncIdFile) {
        this.hgncIdFile = hgncIdFile;
    }

    public String getHgncIdFile() {
        return hgncIdFile;
    }

    public String getDogVgncIdFile() {
        return dogVgncIdFile;
    }

    public void setDogVgncIdFile(String dogVgncIdFile) {
        this.dogVgncIdFile = dogVgncIdFile;
    }

    public String getPigVgncIdFile() {
        return pigVgncIdFile;
    }

    public void setPigVgncIdFile(String pigVgncIdFile) {
        this.pigVgncIdFile = pigVgncIdFile;
    }

    public String getVgncIdFile() {
        return vgncIdFile;
    }

    public void setVgncIdFile(String vgncIdFile) {
        this.vgncIdFile = vgncIdFile;
    }

    public int getRefKey() {
        return refKey;
    }

    public void setRefKey(int refKey) {
        this.refKey = refKey;
    }
}