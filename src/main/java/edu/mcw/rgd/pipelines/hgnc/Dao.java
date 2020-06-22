package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.GeneQuery;
import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.NomenclatureEvent;
import edu.mcw.rgd.datamodel.XdbId;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
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

    Logger logAliases = Logger.getLogger("aliases");
    Logger logNomenEvents = Logger.getLogger("nomen_events");

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

    public List<XdbId> getXdbIds(XdbId xdbId, int speciesTypeKey) throws Exception {
        return xdbIdDAO.getXdbIds(xdbId, speciesTypeKey);
    }

    public void insertNomenclatureEvent(NomenclatureEvent event) throws Exception {
        nomenclatureDAO.createNomenEvent(event);
        logNomenEvents.info("INSERTED "
            +" KEY:"+event.getNomenEventKey()
            +" REF_KEY:"+event.getRefKey()
            +" EVENT_DATE:"+new Timestamp(event.getEventDate().getTime()).toString()
            +" STATUS:"+event.getNomenStatusType()
            +" DESC:"+event.getDesc()
            +" RGD:"+event.getRgdId()
            +" SYMBOL:"+event.getSymbol()
            +" NAME:"+event.getName()
            +" OLD_RGD:"+event.getOriginalRGDId()
            +" OLD_SYMBOL:"+event.getPreviousSymbol()
            +" OLD_NAME:"+event.getPreviousName()
            +" NOTES:"+event.getNotes()
        +"");
    }

    public void updateGene(Gene gene) throws Exception {
        geneDAO.updateGene(gene);
    }

    public void insertAlias(Alias alias) throws Exception {
        aliasDAO.insertAlias(alias);
        logAliases.debug("INSERT "+alias.dump("|"));
    }

    public List<Gene> getActiveGenesByNcbiId(String accId) throws Exception {
        return getActiveGenesById(accId, XdbId.XDB_KEY_NCBI_GENE, "ENTREZGENE");
    }

    public List<Gene> getActiveGenesByEnsemblId(String accId) throws Exception {
        return getActiveGenesById(accId, XdbId.XDB_KEY_ENSEMBL_GENES, "Ensembl");
    }

    List<Gene> getActiveGenesById(String accId, int xdbKey, String srcPipeline) throws Exception {
        String sql = "SELECT DISTINCT g.*, r.species_type_key FROM genes g, rgd_ids r, rgd_acc_xdb x WHERE r.rgd_id=g.rgd_id AND x.rgd_id=g.rgd_id " +
                "AND r.object_key = 1 AND x.XDB_KEY=? AND x.ACC_ID=? AND r.OBJECT_STATUS='ACTIVE' AND x.src_pipeline=?";
        return  GeneQuery.execute(this,sql, xdbKey, accId, srcPipeline);
    }

    public List<XdbId> getXdbIdsByRgdId(int xdbKey, int rgdId) throws Exception {
        return xdbIdDAO.getXdbIdsByRgdId(xdbKey,rgdId);
    }
}
