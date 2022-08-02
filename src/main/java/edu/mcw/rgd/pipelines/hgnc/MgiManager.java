package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.NomenclatureEvent;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.*;

public class MgiManager {
    Dao dao = new Dao();
    String mgiDataFile;
    String version;
    int mgdXdbKey;
    int refKey;
    Logger logger = LogManager.getLogger("mgi_logger");
    int nomenEvents = 0, nullSymbol = 0, mgiNotInRgd = 0, noChange = 0, conTotal = 0;
    Map<String,Gene> conflicts = new HashMap<>();

    public void run() throws Exception {
        long startTime = System.currentTimeMillis();

        logger.info("");
        logger.info(getVersion());
        logger.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logger.info("   started at "+sdt.format(new Date(startTime)));

        String mgiFile = downloadMgiFile(getMgiDataFile());
        logger.info("   -- Parsing data from Informatics MGI file --\n");
        parseFile(mgiFile);

        long endTime = System.currentTimeMillis();
        logger.info("Ended at "+sdt.format(new Date(endTime))+",   elapsed "+Utils.formatElapsedTime(startTime, endTime));
        logger.info("-- HGNC pipeline for MGI data end --");
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
            i++;
            if(i==50000){
                checkDatabase(allData);
                i=0;
                allData.clear();
            }
        }
        checkDatabase(allData);
        logger.info('\n');
        logger.info("   Lines with MGI ids not in RGD: " + mgiNotInRgd);
        logger.info("   Final amount that do not have a symbol: " + nullSymbol);
        logger.info("   Nomen Events that changed: " + nomenEvents);
        logger.info("   Total Conflicts found: " + conTotal);
        logger.info("   Amount that have not changed: " + noChange);

    }

    String downloadMgiFile(String file) throws Exception{
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
            if(dbGene.isEmpty())
                mgiNotInRgd++;
            else{
                for(Gene gene: dbGene){
                    if(gene.getSymbol()==null)
                        nullSymbol++;
                    else if(!Utils.stringsAreEqual(gene.getSymbol(),mgi.getMarkerSymbol())){
                        List<XdbId> list =  dao.getXdbIdsByRgdId(mgdXdbKey,gene.getRgdId());
                        int conflict = list.size();
                        if(conflict < 2 && updateGene(mgi,gene))
                            nomenEvents++;
                        else {
                            for(XdbId xdb : list){
                                if(!Utils.stringsAreEqualIgnoreCase(xdb.getAccId(),mgi.getAccessionId()))
                                    conflicts.put(xdb.getAccId(),gene);
                            }
                            logger.info("Conflict with RGD_ID: "+gene.getRgdId()+", MGI Accession: "+mgi.getAccessionId()+"   Gene: "+gene.getSymbol()+"; "+gene.getName()+
                                    " -- Mgi: "+mgi.getMarkerSymbol()+"; "+mgi.getMarkerName());
                            conTotal++;
                        }
                    }
                    else { // symbols are the same
                         noChange++;
                    }
                }// end gene for
            }
            if(conflictMgi(mgi.getAccessionId())) {
                logger.info("Conflict with RGD_ID: " + conflicts.get(mgi.getAccessionId()).getRgdId() + ", MGI Accession: " + mgi.getAccessionId() +
                        "   Gene: " + conflicts.get(mgi.getAccessionId()).getSymbol() + "; " + conflicts.get(mgi.getAccessionId()).getName()
                        + " -- Mgi: " + mgi.getMarkerSymbol() + "; " + mgi.getMarkerName());
                conTotal++;
            }
        }// end mgi for
    }

    Mgi createMgi(String lineData, String[] col) {
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
        logger.info("RGD_ID: "+gene.getRgdId()+", MGI Accession: "+mgi.getAccessionId()+"   Old: "+gene.getSymbol()+" -- New: "+mgi.getMarkerSymbol());
        String prevSymbol = gene.getSymbol(), prevName = gene.getName();
        gene.setSymbol(mgi.getMarkerSymbol());
        gene.setName(mgi.getMarkerName());
        gene.setNomenSource("MGI");
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
            change.setNomenStatusType("PROVISIONAL");
            change.setRefKey(String.valueOf(getRefKey()));

            dao.insertNomenclatureEvent(change);

            return true;
        }
        return false;
    }
    boolean conflictMgi(String accId) {
           if(conflicts.get(accId) != null)
                return true;
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
