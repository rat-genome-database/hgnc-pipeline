package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.NomenclatureEvent;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import javax.rmi.CORBA.Util;
import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MgiManager {
    Dao dao = new Dao();
    String mgiDataFile;
    String version;
    int mgdXdbKey;
    int refKey;
    Logger logger = Logger.getLogger("mgi_logger");
    int nomenEvents = 0, nullSymbol = 0, DNE = 0;

    public void run() throws Exception {
        long startTime = System.currentTimeMillis();

        logger.info(getVersion());
        logger.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logger.info("   started at "+sdt.format(new Date(startTime)));

        String mgiFile = downloadEvaVcfFile(getMgiDataFile());
        logger.info("   -- Parsing data from Informatics MGI file --\n");
        parseFile(mgiFile);

        long endTime = System.currentTimeMillis();
        logger.info("   Ended at "+sdt.format(new Date(endTime)));
        logger.info("   -- HGNC pipeline for MGI data end --");
    }
    void parseFile(String fileName) throws Exception{

        BufferedReader br = Utils.openReader(fileName);
        String lineData;
        String firstLine;
        List<Mgi> allData = new ArrayList<>();
        int i =0;
        firstLine = br.readLine();
        String[] col = firstLine.split("\t");
        while((lineData = br.readLine()) != null) {
            allData.add(createMgi(lineData, col));
//            System.out.println(allData.get(i).toString());
            i++;
            if(i==50000){
                // update db with Marker symbol id different
                checkDatabase(allData);
                i=0;
                allData.clear();
            }
        }
        checkDatabase(allData);
        logger.info('\n');
        logger.info("   Final amount that Do not exist: "+DNE);
        logger.info("   Final amount that do not hae a symbol: "+nullSymbol);
        logger.info("   Nomen Events that changed: "+nomenEvents);
        int total = DNE + nullSymbol+nomenEvents;
        logger.info("   Total amount that differed: "+total);
    }

    String downloadEvaVcfFile(String file) throws Exception{
        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(file);
        downloader.setLocalFile("data/MgiData.rpt");
        downloader.setUseCompression(true);
        downloader.setPrependDateStamp(true);
        return downloader.downloadNew();
    }

    void checkDatabase(List<Mgi> data) throws Exception{
        for(Mgi mgi : data){
            List<Gene> dbGene = dao.getActiveGenesByXdbId(getMgdXdbKey(), mgi.getAccessionId());
            if(dbGene.isEmpty()){
                logger.info("Gene was not found: "+mgi.getAccessionId()+" : "+mgi.getMarkerSymbol());
                DNE++;
            }
            else{
                for(Gene gene: dbGene){
                    if(gene.getSymbol()==null) {
                        logger.info("RGD_ID: "+gene.getRgdId()+", MGI Accession: "+mgi.getAccessionId()+"   Old gene symbol is null -- "+mgi.getMarkerSymbol());
                        nullSymbol++;
                    }
                    else if(!Utils.stringsAreEqual(gene.getSymbol(),mgi.getMarkerSymbol())){  //!gene.getSymbol().equals(mgi.getMarkerSymbol())){
                        logger.info("RGD_ID: "+gene.getRgdId()+", MGI Accession: "+mgi.getAccessionId()+"   Old: "+gene.getSymbol()+" -- New: "+mgi.getMarkerSymbol());
                        if(updateGene(mgi,gene))
                            nomenEvents++;
                    }
                    else { // symbols are the same
                        logger.info("Gene has the same Symbol. RGD_ID: " + gene.getRgdId() + ", MGI Accession: "+mgi.getAccessionId()+"   Gene Symbol: "+gene.getSymbol());
                    }
                }// end gene for
            }
        }// end mgi for
    }

    Mgi createMgi(String lineData, String[] col) throws Exception{
         Mgi data = new Mgi();
         String[] line = lineData.split("\t");
         for (int i = 0;i<col.length;i++){
             switch (col[i]){
                 case "MGI Accession ID":
                     if(!line[i].isEmpty())
                         data.setAccessionId(line[i]);
                     break;
                 case "Chr":
                     if(!line[i].isEmpty())
                         data.setChr(line[i]);
                     break;
                 case "cM Position":
                     if(!line[i].isEmpty())
                        data.setPosition(line[i]);
                     break;
                 case "genome coordinate start":
                     if(!line[i].isEmpty())
                         data.setGnomeStart(line[i]);
                     break;
                 case "genome coordinate end":
                     if(!line[i].isEmpty())
                        data.setGnomeEnd(line[i]);
                     break;
                 case "strand":
                     if(!line[i].isEmpty())
                        data.setStrand(line[i]);
                     break;
                 case "Marker Symbol":
                     if(!line[i].isEmpty())
                         data.setMarkerSymbol(line[i]);
                     break;
                 case "Status":
                     if(!line[i].isEmpty())
                         data.setStatus(line[i]);
                     break;
                 case "Marker Name":
                     if(!line[i].isEmpty())
                         data.setMarkerName(line[i]);
                     break;
                 case "Marker Type":
                     if(!line[i].isEmpty())
                         data.setMarkerType(line[i]);
                     break;
                 case "Feature Type":
                     if(!line[i].isEmpty())
                         data.setFeatureType(line[i]);
                     break;
                 case "Marker Synonyms (pipe-separated)":
                     if(line.length == 12)
                         if(!line[i].isEmpty())
                            data.setSynonyms(line[i]);
                     break;
             }
         }

         return data;
    }

    boolean updateGene(Mgi mgi, Gene gene) throws Exception {
        // change gene symbol to new symbol
        // use GeneDAO to update with new gene, includes new name and symbol
        // description set to symbol updated
        // update nomen_events

        // do a check if event was created recently, to not double up on events
        // grab all based on rgd_id, then compare symbol and name
        // if same, don't add new event

        String prevSymbol = gene.getSymbol(), prevName = gene.getName();
        gene.setSymbol(mgi.getMarkerSymbol());
        gene.setName(mgi.getMarkerName());

        dao.updateGene(gene);

        if(!Utils.stringsAreEqualIgnoreCase(gene.getSymbol(),prevSymbol) ||
                !Utils.stringsAreEqualIgnoreCase(gene.getName(),prevName)){
            NomenclatureEvent change = new NomenclatureEvent();
            change.setOriginalRGDId(gene.getRgdId());
            change.setRgdId(gene.getRgdId());
            change.setName(gene.getName());
            change.setPreviousName(prevName);
            change.setSymbol(gene.getSymbol());
            change.setPreviousSymbol(prevSymbol);
            change.setEventDate(new Date());
            change.setDesc("Symbol and/or name updated");
            change.setNomenStatusType("PROVISIONAL"); // change before production
            change.setRefKey(String.valueOf(getRefKey()));

            dao.insertNomenclatureEvent(change);

            return true;
        }
        return false;
    }

    public void setMgiDataFile(String mgiDataFile) {
        this.mgiDataFile = mgiDataFile;
    }

    public String getMgiDataFile() {
        return mgiDataFile;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setMgdXdbKey(int mgdXdbKey) {this.mgdXdbKey = mgdXdbKey; }

    public int getMgdXdbKey(){return mgdXdbKey;}

    public int getRefKey(){return refKey;}

    public void setRefKey(int refKey){this.refKey=refKey;}
}
