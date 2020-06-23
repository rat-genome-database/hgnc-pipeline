package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author mtutaj
 * @since 10/7/2016
 * <p>
 * Downloads a file with obsoleted HGNC ids from HGNC FTP site.
 * If a processed HGNC id is withdrawn, remove it from xdbs id of genes in RGD.
 * If a processed HGNC id is replaced by another HGNC id, update it accordingly in genes xdb ids.
 */
public class ObsoleteHgncIdManager {
    private String version;
    private String obsoleteHgncIdFile;
    private Dao dao = new Dao();

    int hgncIdsDeletedInRgd = 0;
    int hgncIdsReplacedInRgd = 0;

    Logger logDb = Logger.getLogger("obsolete_hgnc_ids");

    public void run() throws Exception {

        long time0 = System.currentTimeMillis();

        logDb.info(getVersion());
        logDb.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        logDb.info("   started at "+sdt.format(new Date(time0)));

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getObsoleteHgncIdFile());
        downloader.setLocalFile("data/obsolete_hgnc_ids.txt.gz");
        downloader.setPrependDateStamp(true);
        downloader.setUseCompression(true);
        String localFile = downloader.downloadNew();

        int obsoleteHgncIdsProcessed = 0;
        int withdrawnHgncIdsProcessed = 0;
        int mergedHgncIdsProcessed = 0;

        BufferedReader reader = Utils.openReader(localFile);
        String line = reader.readLine(); // skip header line
        while( (line=reader.readLine())!=null ) {
            obsoleteHgncIdsProcessed++;

            String[] cols = line.split("[\\t]", -1);
            String obsoleteHgncId = cols[0];
            String status = cols[1];
            String withdrawnSymbol = cols[2];
            String mergedIntoReport = cols[3];

            updateObsoleteHgncIdsTable(obsoleteHgncId, status, withdrawnSymbol, mergedIntoReport);

            String mergedToHgncId = null;
            if( !Utils.isStringEmpty(mergedIntoReport) ) {
                String[] mergeInfo = mergedIntoReport.split("[\\|]", -1);
                mergedToHgncId = mergeInfo[0];
                mergedHgncIdsProcessed++;
            } else {
                withdrawnHgncIdsProcessed++;
            }

            updateRgdDb(obsoleteHgncId, mergedToHgncId);
        }
        reader.close();

        logDb.info("Processed obsolete HGNC ids: "+obsoleteHgncIdsProcessed);
        logDb.info(" -- withdrawn HGNC ids: "+withdrawnHgncIdsProcessed);
        logDb.info(" -- merged HGNC ids: "+mergedHgncIdsProcessed);

        if( hgncIdsDeletedInRgd!=0 ) {
            logDb.info("HGNC ID deleted from RGD db: " + hgncIdsDeletedInRgd);
        }
        if( hgncIdsReplacedInRgd!=0 ) {
            logDb.info("HGNC ID replaced in RGD db: " + hgncIdsReplacedInRgd);
        }
        logDb.info("Processing of obsolete HGNC ids complete: "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    void updateRgdDb(String obsoleteHgncId, String mergedToHgncId) throws Exception {

        XdbId filter = new XdbId();
        filter.setAccId(obsoleteHgncId);
        filter.setXdbKey(XdbId.XDB_KEY_HGNC);

        List<XdbId> xdbIds = dao.getXdbIds(filter, SpeciesType.HUMAN);
        for( XdbId id: xdbIds ) {

            if( mergedToHgncId!=null ) {
                // QC: replace HGNC id only if there is no other gene having the same target HGNC ID
                filter.setAccId(mergedToHgncId);
                List<XdbId> genesWithMergedToHgncId = dao.getXdbIds(filter, SpeciesType.HUMAN);
                Gene otherActiveGeneWithSameTargetHgncId = null;
                for( XdbId mergedToXdbId: genesWithMergedToHgncId ) {
                    if( mergedToXdbId.getRgdId()!=id.getRgdId() ) {
                        // check if the object with merged-to-HGNC-ID is an active gene
                        List<Gene> genes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_HGNC, mergedToHgncId);
                        for( Gene g: genes ) {
                            if( g.getRgdId()==mergedToXdbId.getRgdId() ) {
                                otherActiveGeneWithSameTargetHgncId = g;
                                break;
                            }
                        }
                    }
                }
                if( otherActiveGeneWithSameTargetHgncId!=null ) {
                    // there is another active gene (gene different than gene with source HGNC ID)
                    // having the same merged-to HGNC ID; that means a conflict
                    // and thus we remove the obsolete HGNC ID to avoid having two active genes with the same HGNC ID
                    Gene g = otherActiveGeneWithSameTargetHgncId;
                    logDb.info("CONFLICT: obsolete "+obsoleteHgncId+" may not be replaced with "+mergedToHgncId);
                    logDb.info("   for gene RGD:"+id.getRgdId()+" because there is another active gene "+g.getSymbol()+" (RGD:"+ g.getRgdId()+")");
                    logDb.info("   already associated with "+mergedToHgncId);
                    mergedToHgncId = null;
                }
            }

            if( mergedToHgncId!=null ) {
                // replace HGNC id
                id.setAccId(mergedToHgncId);
                id.setLinkText(null);
                id.setModificationDate(new Date());

                String notes = "replaced "+obsoleteHgncId+" with "+mergedToHgncId;
                if( id.getNotes()!=null ) {
                    notes = id.getNotes()+"; "+notes;
                }
                id.setNotes(notes);

                dao.updateByKey(id);

                logDb.info("MERGE RGD:"+id.getRgdId()+" "+obsoleteHgncId+"==>"+mergedToHgncId);
                hgncIdsReplacedInRgd++;
            } else {
                dao.deleteXdbId(id);

                logDb.info("DROP RGD:"+id.getRgdId()+" "+obsoleteHgncId);
                hgncIdsDeletedInRgd++;
            }

            // create alias of type 'old_hgnc_id'
            Alias alias = new Alias();
            alias.setRgdId(id.getRgdId());
            alias.setTypeName("old_hgnc_id");
            alias.setValue(obsoleteHgncId);
            alias.setNotes("created by ObsoleteHgncId pipeline");
            dao.insertAlias(alias);
        }
    }

    void updateObsoleteHgncIdsTable(String obsoleteHgncId, String status, String withdrawnSymbol, String mergedIntoReport) throws Exception {

        ObsoleteHgncId o = dao.getObsoleteHgncId(obsoleteHgncId);
        if( o==null ) {
            dao.insertObsoleteHgncId(obsoleteHgncId, status, withdrawnSymbol, mergedIntoReport);
        } else {
            // check if any properties changed
            boolean matches = Utils.stringsAreEqual(o.getStatus(), status) &&
                    Utils.stringsAreEqual(o.getWithdrawnSymbol(), withdrawnSymbol) &&
                    Utils.stringsAreEqual(o.getMergedIntoReport(), mergedIntoReport);
            if( !matches ) {
                dao.updateObsoleteHgncId(obsoleteHgncId, status, withdrawnSymbol, mergedIntoReport);
            }
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setObsoleteHgncIdFile(String obsoleteHgncIdFile) {
        this.obsoleteHgncIdFile = obsoleteHgncIdFile;
    }

    public String getObsoleteHgncIdFile() {
        return obsoleteHgncIdFile;
    }
}
