package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.GeneQuery;
import edu.mcw.rgd.dao.spring.StringListQuery;
import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.NomenclatureEvent;
import edu.mcw.rgd.datamodel.XdbId;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mtutaj
 * @since 12/22/11
 * Dao to handle database access
 */
public class Dao extends AbstractDAO{

    private XdbIdDAO xdbIdDAO = new XdbIdDAO();
    private NomenclatureDAO nomenclatureDAO = new NomenclatureDAO();
    private AliasDAO aliasDAO = new AliasDAO();
    private GeneDAO geneDAO = new GeneDAO();

    public Dao() {
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

    public void insertNomenclatureEvent(NomenclatureEvent event) throws Exception {
        nomenclatureDAO.createNomenEvent(event);
    }
    public void updateGene(Gene gene) throws Exception {
        geneDAO.updateGene(gene);
    }
    public void insertAlias(Alias alias) throws Exception {
        aliasDAO.insertAlias(alias);
    }

    public List<Gene> getActiveGenesByNcbiId(String Acc_id) throws Exception{
        String sql = "SELECT DISTINCT g.*, r.species_type_key FROM genes g, rgd_ids r, rgd_acc_xdb x where r.RGD_ID=g.RGD_ID AND x.RGD_ID=g.RGD_ID " +
                "AND r.object_key = 1 AND x.XDB_KEY=? AND x.ACC_ID=? AND r.OBJECT_STATUS='ACTIVE' AND x.src_pipeline = 'ENTREZGENE'";
        return  GeneQuery.execute(this,sql,XdbId.XDB_KEY_NCBI_GENE,Acc_id);
    }
    public List<Gene> getActiveGenesByEnsemblId(String Acc_id) throws Exception{
        String sql = " SELECT DISTINCT g.*, r.species_type_key FROM genes g, rgd_ids r, rgd_acc_xdb x where r.RGD_ID=g.RGD_ID AND x.RGD_ID=g.RGD_ID " +
                "AND r.object_key = 1 AND x.XDB_KEY=? AND x.ACC_ID=? AND r.OBJECT_STATUS='ACTIVE' AND x.src_pipeline = 'Ensembl'";
        return  GeneQuery.execute(this,sql,XdbId.XDB_KEY_ENSEMBL_GENES,Acc_id);
    }
}
