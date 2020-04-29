package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

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
        int hgncIdsProcessed = 0;
        int conflictCount = 0;
        int nomenEvents = 0;

        BufferedReader reader = Utils.openReader(localFile);
        String line = reader.readLine(); // skip header line
        while( (line=reader.readLine())!=null ) {
            hgncIdsProcessed++;

            String[] cols = line.split("[\\t]", -1);
            String hgncId = cols[0].substring(5);
            String symbol = cols[1];
            String name = cols[2];
            String ncbiId = cols[18];
            String ensemblId = cols[19];

            List<Gene> existingGenes;
            if( speciesTypeKey==SpeciesType.HUMAN ) {
                existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_HGNC, hgncId);
            } else {
                existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_VGNC, hgncId);
            }
            if( existingGenes.size() != 1 ) {
                if( ncbiId != null ) {
                    existingGenes = dao.getActiveGenesByNcbiId(ncbiId);
                }
                if( existingGenes.size() != 1 && ensemblId != null)
                    existingGenes = dao.getActiveGenesByEnsemblId(ensemblId);
            }

            if( existingGenes.size() != 1 ){
                String acc = (speciesTypeKey==SpeciesType.HUMAN ? "HGNC:" : "VGNC:") + hgncId;
                logDb.debug("   Genes not found/ Found with multiple Rgd IDs for " + acc);
                conflictCount++;
            } else {
                String previousSymbol = existingGenes.get(0).getSymbol();
                String previousName = existingGenes.get(0).getName();

                Gene g = existingGenes.get(0);
                g.setSymbol(symbol);
                g.setName(name);
                g.setNomenSource("HGNC");
                if( updateGene(g,previousSymbol,previousName) ) {
                    nomenEvents++;
                }
            }
        }

        reader.close();

        logDb.info("   Number of HGNC/VGNC ids in "+ speciesName+" the file: "+ hgncIdsProcessed);
        logDb.info("      out of which "+conflictCount+" did not match a single gene in RGD");
        logDb.info("   Number of "+ speciesName+" Genes Updated: "+ nomenEvents);
    }

    /** return true if a nomen event has been generated
     */
    boolean updateGene(Gene gene, String oldSymbol, String oldName) throws Exception {

        dao.updateGene(gene);

        if(!oldSymbol.equalsIgnoreCase(gene.getSymbol()) || (oldName != null && !oldName.equalsIgnoreCase(gene.getName()))) {
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
