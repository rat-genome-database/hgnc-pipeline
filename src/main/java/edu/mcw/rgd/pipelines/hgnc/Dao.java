package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.XdbId;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mtutaj
 * @since 12/22/11
 * Dao to handle database access
 */
public class Dao {

    private XdbIdDAO xdbIdDAO = new XdbIdDAO();

    public Dao() {
        System.out.println(xdbIdDAO.getConnectionInfo());
    }

    /**
     * get active genes with given external id
     * @param xdbKey - external db key
     * @param accId - external id to be looked for
     * @return list of Gene objects
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<Gene> getActiveGenesByXdbId(int xdbKey, String accId) throws Exception {
        return xdbIdDAO.getActiveGenesByXdbId(xdbKey, accId);
    }

    /**
     * update properties of this row by ACC_XDB_KEY
     * @param xdbId object with data to be updated
     * @return 1 if row have been changed, or 0 if left unchanged
     * @throws Exception when something went wrong in spring framework
     */
    public int updateByKey(XdbId xdbId) throws Exception {
        return xdbIdDAO.updateByKey(xdbId);
    }

    /**
     * delete an XdbId object
     * @param xdbId XdbId object to be deleted
     * @return rows deleted
     * @throws Exception when something went wrong in spring framework
     */
    public int deleteXdbId(XdbId xdbId) throws Exception {
        List<XdbId> xdbIds = new ArrayList<>(1);
        xdbIds.add(xdbId);
        return xdbIdDAO.deleteXdbIds(xdbIds);
    }

    /**
     * insert an XdbId; duplicate entries are not inserted (with same RGD_ID,XDB_KEY,ACC_ID,SRC_PIPELINE)
     * @param xdb XdbId object to be inserted
     * @return number of actually inserted rows (0 or 1)
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int insertXdb(XdbId xdb) throws Exception {
        return xdbIdDAO.insertXdb(xdb);
    }

    /**
     * return external ids for any combination of parameters;
     * if given parameter is null or 0, it means, that any value of this parameter could be accepted
     *
     * @param xdbId - acc_id,xdb_id,rgd_id and src_pipeline are checked
     * @return list of external ids
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<XdbId> getXdbIds(XdbId xdbId) throws Exception {
        return xdbIdDAO.getXdbIds(xdbId);
    }

    /**
     * return count of rows for given xdb and pipeline modified before given date
     *
     * @param xdbKey xdb key
     * @param srcPipeline source
     * @param modDate modification date
     * @return count of rows for given xdb and pipeline modified before given date
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int getCountOfXdbIdsModifiedBefore(int xdbKey, String srcPipeline, java.util.Date modDate) throws Exception {

        return xdbIdDAO.getCountOfXdbIdsModifiedBefore(xdbKey, srcPipeline, modDate);
    }

    /**
     * delete entries for given xdb and pipeline modified before given date
     *
     * @param xdbKey xdb key
     * @param srcPipeline source
     * @param modDate modification date
     * @return count of rows deleted
     * @throws Exception when unexpected error in spring framework occurs
     */
    public int deleteXdbIdsModifiedBefore(int xdbKey, String srcPipeline, java.util.Date modDate) throws Exception {

        return xdbIdDAO.deleteXdbIdsModifiedBefore(xdbKey, srcPipeline, modDate);
    }
}
