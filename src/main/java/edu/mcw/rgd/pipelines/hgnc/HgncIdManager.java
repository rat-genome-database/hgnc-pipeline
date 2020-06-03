package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author hsnalabolu
 * @since 07/2/2020
 */
public class HgncIdManager {
    private String version;
    private String hgncIdFile;
    private String dogVgncIdFile;
    private String pigVgncIdFile;
    private int refKey;
    private Dao dao = new Dao();
    Logger logDb = Logger.getLogger("hgnc_ids");
    Logger logNoMatch = Logger.getLogger("no_match");
    Logger logMultiMatch = Logger.getLogger("multi_match");

    public void run() throws Exception {

        long startTime = System.currentTimeMillis();

        logDb.info(getVersion());
        logDb.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logDb.info("   started at "+sdt.format(new Date(startTime)));

        run(SpeciesType.HUMAN);
        run(SpeciesType.DOG);
        run(SpeciesType.PIG);

        logDb.info("");
        logDb.info("=== OK ===      elapsed "+Utils.formatElapsedTime(System.currentTimeMillis(), startTime));
    }

    public void run(int speciesTypeKey) throws Exception {

        String speciesName = SpeciesType.getCommonName(speciesTypeKey);

        List<HgncGene> hgncGenes = parseInputFile(speciesTypeKey);

        HashMap<Integer,List<HgncGene>> geneMap = qc(speciesTypeKey, hgncGenes);

        int nomenEvents = 0;
        int genesModified = 0;

        for( int geneRgdId: geneMap.keySet() ) {

            List<HgncGene> genes = geneMap.get(geneRgdId);
            if( genes.size()>1 ) {
                String accIds = genes.get(0).getFullAcc(speciesTypeKey);
                for( int i=1; i<genes.size(); i++ ) {
                    accIds += ", " + genes.get(i).getFullAcc(speciesTypeKey);
                }
                logDb.warn("conflict: single gene RGD:"+geneRgdId+" matches multiple ids: "+accIds);
                continue;
            }

            HgncGene g = genes.get(0);
            String previousSymbol = g.gene.getSymbol();
            String previousName = g.gene.getName();

            if( Utils.stringsAreEqual(g.symbol, previousSymbol)
                && Utils.stringsAreEqual(g.name, previousName)
                && Utils.stringsAreEqual(g.gene.getNomenSource(), "HGNC") ) {

                // everything up-to-date: continue to next line
                continue;
            }

            g.gene.setSymbol(g.symbol);
            g.gene.setName(g.name);
            g.gene.setNomenSource("HGNC");
            if( updateGene(g.gene, previousSymbol, previousName, g.matchBy) ) {
                nomenEvents++;
            }
            genesModified++;
        }

        logDb.info("   Number of "+ speciesName+" Genes Updated: "+ genesModified);
        logDb.info("   Number of "+ speciesName+" Nomen Events created: "+ nomenEvents);
    }

    public List<HgncGene> parseInputFile(int speciesTypeKey) throws Exception {

        String speciesName = SpeciesType.getCommonName(speciesTypeKey);
        logDb.info("");
        logDb.info(speciesName.toUpperCase()+" HGNC file processing ...");

        FileDownloader downloader = new FileDownloader();
        if(speciesTypeKey == SpeciesType.DOG)
            downloader.setExternalFile(getDogVgncIdFile());
        else if(speciesTypeKey == SpeciesType.PIG)
            downloader.setExternalFile(getPigVgncIdFile());
        else downloader.setExternalFile(getHgncIdFile());

        downloader.setLocalFile("data/"+speciesName+"_hgnc_ids.txt");
        downloader.setPrependDateStamp(true);
        downloader.setUseCompression(true);
        String localFile = downloader.downloadNew();

        logDb.info("   file downloaded to "+localFile);

        List<HgncGene> hgncGenes = new ArrayList<>();

        Collection<String> dataLines = getUniqueDataLines(localFile);
        for( String line: dataLines ) {

            String[] cols = line.split("[\\t]", -1);
            HgncGene hgncGene = new HgncGene();
            hgncGene.hgncId = cols[0].substring(5);
            hgncGene.symbol = cols[1];
            hgncGene.name = cols[2];
            hgncGene.ncbiId = cols[18];
            hgncGene.ensemblId = cols[19];

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

    HashMap<Integer,List<HgncGene>> qc(int speciesTypeKey, List<HgncGene> hgncGenes) throws Exception {

        HashMap<Integer, List<HgncGene>> resultMap = new HashMap<>();

        String speciesName = SpeciesType.getCommonName(speciesTypeKey);

        int conflictCount = 0;

        for( HgncGene g: hgncGenes ) {

            String acc = g.getFullAcc(speciesTypeKey);

            g.matchBy = "";
            List<Gene> existingGenes;
            if( speciesTypeKey==SpeciesType.HUMAN ) {
                existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_HGNC, g.hgncId);
            } else {
                existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_VGNC, g.hgncId);
            }
            if( existingGenes.size() == 1 ) {
                g.matchBy = "match by "+acc;
            } else {
                if( g.ncbiId != null ) {
                    existingGenes = dao.getActiveGenesByNcbiId(g.ncbiId);
                    if( existingGenes.size()==1 ) {
                        g.matchBy = "match by NCBI:"+g.ncbiId+" ("+acc+")";
                    }
                }
                if( existingGenes.size() != 1 && g.ensemblId != null ) {
                    existingGenes = dao.getActiveGenesByEnsemblId(g.ensemblId);
                    if (existingGenes.size() == 1) {
                        g.matchBy = "match by " + g.ensemblId+" ("+acc+")";
                    }
                }
            }

            if( existingGenes.size() != 1 ){
                String msg = acc+" "+g.symbol+" ["+g.name+"]";
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
            }
        }

        logDb.info("   Number of HGNC/VGNC ids in "+ speciesName+" the file: "+ hgncGenes.size());
        logDb.info("      out of which "+conflictCount+" did not match a single gene in RGD");

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

    public int getRefKey() {
        return refKey;
    }

    public void setRefKey(int refKey) {
        this.refKey = refKey;
    }
}
