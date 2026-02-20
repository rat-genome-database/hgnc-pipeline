package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.dao.AbstractDAO;
import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.IntListQuery;
import edu.mcw.rgd.datamodel.Alias;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.HgncFamily;
import edu.mcw.rgd.datamodel.NomenclatureEvent;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.MappingSqlQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;

/**
 * @author mtutaj
 * @since 12/22/11
 * Dao to handle database access
 */
public class Dao extends AbstractDAO{

    private boolean readOnlyMode = false;
    private XdbIdDAO xdbIdDAO = new XdbIdDAO();
    private NomenclatureDAO nomenclatureDAO = new NomenclatureDAO();
    private AliasDAO aliasDAO = new AliasDAO();
    private GeneDAO geneDAO = new GeneDAO();
    private HgncDAO hgncDAO = new HgncDAO();

    Logger logAliases = LogManager.getLogger("aliases");
    Logger logNomenEvents = LogManager.getLogger("nomen_events");
    Logger logNomenSource = LogManager.getLogger("nomen_source");
    Logger logGeneFamilies = LogManager.getLogger("gene_families");

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

    public Set<Integer> getGeneRgdIdsForNomenSource(int speciesTypeKey, String nomenSource ) throws Exception {

        String sql = "SELECT g.rgd_id FROM genes g,rgd_ids i WHERE i.rgd_id=g.rgd_id AND species_type_key=? AND nomen_source=? AND object_status='ACTIVE'";
        List<Integer> list = IntListQuery.execute(geneDAO, sql, speciesTypeKey, nomenSource);
        return new HashSet<>(list);
    }

    public boolean clearNomenSourceForGene( int geneRgdId, String oldNomenSource ) throws Exception {

        if( isReadOnlyMode() ) {
            return true;
        }

        String sql = "UPDATE genes SET nomen_source=NULL WHERE rgd_id=? AND nomen_source=?";
        int r = geneDAO.update(sql, geneRgdId, oldNomenSource);
        if( r!=0 ) {
            logNomenSource.info("NOMEN_SOURCE cleared for gene RGD:"+geneRgdId);
        } else {
            logNomenSource.warn("*** UNEXPECTED! failed to clear NOMEN_SOURCE for gene RGD:"+geneRgdId);
        }
        return r!=0;
    }

    /**
     * update properties of this row by ACC_XDB_KEY
     * @param xdbId object with data to be updated
     * @return 1 if row have been changed, or 0 if left unchanged
     * @throws Exception when something went wrong in spring framework
     */
    public int updateByKey(XdbId xdbId) throws Exception {
        if( isReadOnlyMode() ) {
            return 1;
        }
        return xdbIdDAO.updateByKey(xdbId);
    }

    /**
     * delete an XdbId object
     * @param xdbId XdbId object to be deleted
     * @return rows deleted
     * @throws Exception when something went wrong in spring framework
     */
    public int deleteXdbId(XdbId xdbId) throws Exception {
        if( isReadOnlyMode() ) {
            return 1;
        }

        List<XdbId> xdbIds = new ArrayList<>(1);
        xdbIds.add(xdbId);
        return xdbIdDAO.deleteXdbIds(xdbIds);
    }

    public List<XdbId> getXdbIds(XdbId xdbId, int speciesTypeKey) throws Exception {
        return xdbIdDAO.getXdbIds(xdbId, speciesTypeKey);
    }

    public void insertNomenclatureEvent(NomenclatureEvent event) throws Exception {
        if( !isReadOnlyMode() ) {
            nomenclatureDAO.createNomenEvent(event);
        }
        logNomenEvents.info("INSERTED"
            +"\n KEY:"+event.getNomenEventKey()
            +"\n REF_KEY:"+event.getRefKey()
            +"\n EVENT_DATE:"+new Timestamp(event.getEventDate().getTime()).toString()
            +"\n STATUS:"+event.getNomenStatusType()
            +"\n DESC:"+event.getDesc()
            +"\n RGD:"+event.getRgdId()+"   OLD_RGD:"+event.getOriginalRGDId()
            +"\n SYMBOL:["+event.getSymbol()+"]   OLD_SYMBOL:["+event.getPreviousSymbol()+"]"
            +"\n NAME:["+event.getName()+"]   OLD_NAME:["+event.getPreviousName()+"]"
            +"\n NOTES:"+event.getNotes()
        +"");
    }

    public void updateGene(Gene gene) throws Exception {
        if( !isReadOnlyMode() ) {
            geneDAO.updateGene(gene);
        }
    }

    public void insertAlias(Alias alias) throws Exception {
        if( !isReadOnlyMode() ) {
            aliasDAO.insertAlias(alias);
        }
        logAliases.debug("INSERT "+alias.dump("|"));
    }

    public List<Gene> getActiveGenesByNcbiId(String accId) throws Exception {
        return getActiveGenesById(accId, XdbId.XDB_KEY_NCBI_GENE, "ENTREZGENE");
    }

    public List<Gene> getActiveGenesByEnsemblId(String accId) throws Exception {
        return getActiveGenesById(accId, XdbId.XDB_KEY_ENSEMBL_GENES, "Ensembl");
    }

    List<Gene> getActiveGenesById(String accId, int xdbKey, String srcPipeline) throws Exception {
        return xdbIdDAO.getActiveGenesByXdbId(xdbKey, accId, srcPipeline);
    }

    public List<XdbId> getXdbIdsByRgdId(int xdbKey, int rgdId) throws Exception {
        return xdbIdDAO.getXdbIdsByRgdId(xdbKey,rgdId);
    }

    public ObsoleteHgncId getObsoleteHgncId(String hgncId) throws Exception {

        String sql = "SELECT * FROM obsolete_hgnc_ids WHERE hgnc_id=?";
        MappingSqlQuery q = new MappingSqlQuery(xdbIdDAO.getDataSource(), sql) {
            @Override
            protected Object mapRow(ResultSet rs, int rowNum) throws SQLException {
                ObsoleteHgncId o = new ObsoleteHgncId();
                o.setHgncId(rs.getString("hgnc_id"));
                o.setStatus(rs.getString("status"));
                o.setWithdrawnSymbol(rs.getString("withdrawn_symbol"));
                o.setMergedIntoReport(rs.getString("merged_into_report"));
                o.setCreatedDate(rs.getDate("created_date"));
                o.setLastModifiedDate(rs.getDate("last_modified_date"));
                return o;
            }
        };
        q.declareParameter(new SqlParameter(Types.VARCHAR));
        q.compile();
        List<ObsoleteHgncId> results = q.execute(hgncId);
        return results.isEmpty() ? null : results.get(0);
    }

    public void insertObsoleteHgncId(String obsoleteHgncId, String status, String withdrawnSymbol, String mergedIntoReport) throws Exception {

        if( isReadOnlyMode() ) {
            return;
        }
        String sql = "INSERT INTO obsolete_hgnc_ids (hgnc_id,status,withdrawn_symbol,merged_into_report) VALUES(?,?,?,?)";
        xdbIdDAO.update(sql, obsoleteHgncId, status, withdrawnSymbol, mergedIntoReport);
    }

    public void updateObsoleteHgncId(String obsoleteHgncId, String status, String withdrawnSymbol, String mergedIntoReport) throws Exception {

        if( isReadOnlyMode() ) {
            return;
        }
        String sql = "UPDATE obsolete_hgnc_ids SET last_modified_date=SYSDATE,status=?,withdrawn_symbol=?,merged_into_report=? WHERE hgnc_id=?";
        xdbIdDAO.update(sql, status, withdrawnSymbol, mergedIntoReport, obsoleteHgncId);
    }

    /// gene family methods ///

    public Map<Integer, HgncFamily> getAllFamiliesAsMap() throws Exception {
        List<HgncFamily> families = hgncDAO.getAllFamilies();
        Map<Integer, HgncFamily> map = new HashMap<>();
        for (HgncFamily f : families) {
            map.put(f.getFamilyId(), f);
        }
        logGeneFamilies.info("families loaded from database: " + map.size());
        return map;
    }

    public void insertFamily(HgncFamily f) throws Exception {
        if (!isReadOnlyMode()) {
            hgncDAO.insertFamily(f);
        }
        logGeneFamilies.debug("INSERT " + f.dump("|"));
    }

    public void updateFamily(HgncFamily existing, HgncFamily incoming) throws Exception {
        if (!isReadOnlyMode()) {
            hgncDAO.updateFamily(incoming);
        }
        logGeneFamilies.debug("UPDATE OLD " + existing.dump("|"));
        logGeneFamilies.debug("UPDATE NEW " + incoming.dump("|"));
    }

    /// gene-to-family mapping methods ///

    /**
     * load all HGNC id to gene RGD id mappings from RGD_ACC_XDB table
     * @return map of HGNC accession id (e.g. "HGNC:123") to gene RGD id
     */
    public Map<String, Integer> getHgncIdToRgdIdMap() throws Exception {
        List<XdbId> xdbIds = xdbIdDAO.getActiveXdbIds(XdbId.XDB_KEY_HGNC, RgdId.OBJECT_KEY_GENES);
        Map<String, Integer> map = new HashMap<>();
        for (XdbId x : xdbIds) {
            map.put(x.getAccId(), x.getRgdId());
        }
        logGeneFamilies.info("HGNC-to-RGD-ID mappings loaded: " + map.size());
        return map;
    }

    /**
     * load all existing gene-to-family mappings from hgnc_family_to_genes table
     * @return map of "hgnc_id|family_id" to rgd_id (null if not set)
     */
    public Map<String, Integer> getAllGeneFamilyMappings() throws Exception {
        String sql = "SELECT hgnc_id, family_id, rgd_id FROM hgnc_family_to_genes";
        Map<String, Integer> mappings = new HashMap<>();
        try (java.sql.Connection conn = getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString(1) + "|" + rs.getInt(2);
                int rgdId = rs.getInt(3);
                mappings.put(key, rs.wasNull() ? null : rgdId);
            }
        }
        logGeneFamilies.info("gene-to-family mappings loaded from database: " + mappings.size());
        return mappings;
    }

    public void insertGeneFamilyMapping(String hgncId, int familyId, Integer rgdId) throws Exception {
        if (!isReadOnlyMode()) {
            String sql = "INSERT INTO hgnc_family_to_genes (hgnc_id, family_id, rgd_id) VALUES (?, ?, ?)";
            update(sql, hgncId, familyId, rgdId);
        }
        logGeneFamilies.debug("INSERT gene-family hgnc_id=" + hgncId + " family_id=" + familyId + " rgd_id=" + rgdId);
    }

    public void updateGeneFamilyRgdId(String hgncId, int familyId, Integer rgdId) throws Exception {
        if (!isReadOnlyMode()) {
            String sql = "UPDATE hgnc_family_to_genes SET rgd_id=? WHERE hgnc_id=? AND family_id=?";
            update(sql, rgdId, hgncId, familyId);
        }
        logGeneFamilies.debug("UPDATE gene-family hgnc_id=" + hgncId + " family_id=" + familyId + " rgd_id=" + rgdId);
    }

    public void deleteGeneFamilyMapping(String hgncId, int familyId) throws Exception {
        if (!isReadOnlyMode()) {
            String sql = "DELETE FROM hgnc_family_to_genes WHERE hgnc_id=? AND family_id=?";
            update(sql, hgncId, familyId);
        }
        logGeneFamilies.debug("DELETE gene-family hgnc_id=" + hgncId + " family_id=" + familyId);
    }

    public boolean isReadOnlyMode() {
        return readOnlyMode;
    }

    public void setReadOnlyMode(boolean readOnlyMode) {
        this.readOnlyMode = readOnlyMode;
    }
}
