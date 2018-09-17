package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.PipelineLog;
import edu.mcw.rgd.pipelines.RecordPreprocessor;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.PipelineLogger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Apr 28, 2011
 * Time: 5:30:25 PM
 *
 * download genes file from PharmGKB website, process it and break into lines
 */
public class PreProcessor extends RecordPreprocessor {

    private String genesFile;
    private PipelineLogger dbLogger = PipelineLogger.getInstance();

    /** genefam_list.pl format:<pre>
URL	Gene Family Tag	Gene Family Description	Symbol	HGNC ID
http://www.genenames.org/genefamiles/AKR	AKR	Aldo-keto reductases	AKR1A1	380
http://www.genenames.org/genefamiles/AKR	AKR	Aldo-keto reductases	AKR1B1	381
http://www.genenames.org/genefamiles/AKR	AKR	Aldo-keto reductases	AKR1B10	382
http://www.genenames.org/genefamilies/5HTR	5HTR	5-hydroxytryptamine receptors	HTR1A	5286
     * </pre>
     * Please note, one gene could have multiple lines because it could have multiple hgnc ids
     * @throws Exception
     */
    @Override
    public void process() throws Exception {

        // download the file to a local folder
        System.out.println("downloading file "+genesFile);
        String fileName = downloadFile();
        BufferedReader reader = new BufferedReader(new FileReader(fileName));

        int recNo = 0;
        HgncRecord rec = null;
        String prevFamilyTag = "", newFamilyTag;

        // skip first header line
        reader.readLine();

        String line;
        while( (line=reader.readLine())!=null ) {

            // split line into words
            String[] words = line.split("\t", -1);
            if( words.length<5 )
                continue; // there must be at least 5 columns present

            // check if we have a new record
            newFamilyTag = words[1];
            if( !newFamilyTag.equals(prevFamilyTag) ) {
                // new family
                if( rec!=null ) {
                    this.getSession().putRecordToFirstQueue(rec);
                }

                // add family-specific properties
                rec = new HgncRecord();
                rec.setRecNo(++recNo);
                rec.setExternalUrl(words[0]);
                rec.setFamilySymbol(newFamilyTag);
                rec.setFamilyName(words[2]);
            }

            // parse the data
            int hgncId = Integer.parseInt(words[4]);
            rec.getMapHgncIdToGeneSymbol().put(hgncId, words[3]);
        }

        // save the last record
        if( rec!=null ) {
            this.getSession().putRecordToFirstQueue(rec);
        }

        // cleanup
        reader.close();

        dbLogger.log(null, Integer.toString(recNo), PipelineLog.LOGPROP_RECCOUNT);
    }

    /**
     * download genes.zip file, save it to a local directory
     * @return the name of the local copy of the file
     */
    private String downloadFile() throws Exception {

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getGenesFile());
        downloader.setLocalFile("data/genefam_list.tsv");
        downloader.setPrependDateStamp(true); // prefix downloaded files with the current date
        return downloader.download();
    }

    public String getGenesFile() {
        return genesFile;
    }

    public void setGenesFile(String genesFile) {
        this.genesFile = genesFile;
    }
}
