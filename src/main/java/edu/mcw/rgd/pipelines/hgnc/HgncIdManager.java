package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import sun.swing.BakedArrayList;

import java.io.BufferedReader;
import java.util.ArrayList;
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
    private int nomenEvents = 0;
    Logger logDb = Logger.getLogger("hgnc_ids");
    public void run(int speciesTypeKey) throws Exception {



        logDb.info("Running "+ SpeciesType.getCommonName(speciesTypeKey)+" HGNC file");

        FileDownloader downloader = new FileDownloader();
        if(speciesTypeKey == SpeciesType.DOG)
            downloader.setExternalFile(getDogVgncIdFile());
        else if(speciesTypeKey == SpeciesType.PIG)
            downloader.setExternalFile(getPigVgncIdFile());
        else downloader.setExternalFile(getHgncIdFile());

        downloader.setLocalFile("data/hgnc_ids.txt");
        downloader.setPrependDateStamp(true);
        String localFile = downloader.downloadNew();

        System.out.println("Completed file download");
        int hgncIdsProcessed = 0;

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

            List<Gene> existingGenes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_HGNC,hgncId);
            if(existingGenes.isEmpty() || existingGenes.size() != 1){
                if(ncbiId != null )
                    existingGenes = dao.getActiveGenesByNcbiId(ncbiId);
                if((existingGenes.isEmpty() || existingGenes.size() != 1) && ensemblId != null)
                    existingGenes = dao.getActiveGenesByEnsemblId(ensemblId);
            }

            if(existingGenes.isEmpty() || existingGenes.size() != 1){
                logDb.info("Genes not found/ Found with multiple Rgd IDs :" + hgncId);
            } else {
                String previousSymbol = existingGenes.get(0).getSymbol();
                String previousName = existingGenes.get(0).getName();

                Gene g = existingGenes.get(0);
                g.setSymbol(symbol);
                g.setName(name);
                g.setNomenSource("HGNC");
                updateGene(g,previousSymbol,previousName);
            }

        }

        reader.close();
        logDb.info("Number of Genes in "+ SpeciesType.getCommonName(speciesTypeKey)+" HGNC file: "+ hgncIdsProcessed);
        logDb.info("Number of "+ SpeciesType.getCommonName(speciesTypeKey)+" Genes Updated: "+ nomenEvents);

    }

    void updateGene(Gene gene,String oldSymbol,String oldName) throws Exception {

        dao.updateGene(gene);

        if(!oldSymbol.equalsIgnoreCase(gene.getSymbol()) || (oldName != null || !oldName.equalsIgnoreCase(gene.getName()))) {
            nomenEvents++;


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

        }

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
