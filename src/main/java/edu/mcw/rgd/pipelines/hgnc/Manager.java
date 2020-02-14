package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.pipelines.PipelineManager;
import edu.mcw.rgd.process.PipelineLogFlagManager;
import edu.mcw.rgd.process.PipelineLogger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

/**
 * @author mtutaj
 * @since 12/22/11
 * Load gene families from HGNC site
 */
public class Manager {

    private PreProcessor preProcessor;
    private QCProcessor qcProcessor;
    private LoadProcessor loadProcessor;
    private Dao dao;

    private PipelineLogger dbLogger = PipelineLogger.getInstance();
    private PipelineLogFlagManager dbFlagManager = new PipelineLogFlagManager(dbLogger);
    private String version;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        try {

            // process cmdline params
            for( int i=0; i<args.length; i++ ) {
                switch(args[i]) {
                    case "--processObsoleteHgncIds": {
                        ObsoleteHgncIdManager manager = (ObsoleteHgncIdManager) (bf.getBean("obsoleteHgncIdManager"));
                        manager.run();
                        return;
                    }
                    case "--processHgncIds": {
                        HgncIdManager manager = (HgncIdManager) (bf.getBean("hgncIdManager"));
                        manager.run(SpeciesType.HUMAN);
                        manager.run(SpeciesType.DOG);
                        manager.run(SpeciesType.PIG);
                        return;
                    }
                }
            }

            Manager manager = (Manager) (bf.getBean("manager"));
            manager.run();
        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    void run() throws Exception {

        System.out.println(this.getVersion());

        dbLogger.init(SpeciesType.HUMAN, "download+process", "HGNC");

        qcProcessor.setDao(dao);
        qcProcessor.setDbFlagManager(dbFlagManager);
        qcProcessor.registerDbLogFlags();

        loadProcessor.setDao(dao);

        // load number of xdb ids loaded so far by PharmGKB pipeline
        java.util.Date now = new java.util.Date();
        int xdbIdCount = dao.getCountOfXdbIdsModifiedBefore(XdbId.XDB_KEY_PHARMGKB, "PharmGKB", now);
        System.out.println("count of PharmGKB IDs in the database: "+xdbIdCount);

        PipelineManager manager = new PipelineManager();
        manager.addPipelineWorkgroup(preProcessor, "PP", 1, 1000);
        manager.addPipelineWorkgroup(qcProcessor, "QC", 5, 1000);
        manager.addPipelineWorkgroup(loadProcessor, "DL", 1, 1000);

        try {
            manager.run();

            // dump counter statistics
            for( String counter: manager.getSession().getCounters() ) {
                int count = manager.getSession().getCounterValue(counter);
                if( count>0 ) {
                    System.out.println(counter+": "+count);
                    dbLogger.log(counter, Integer.toString(count), PipelineLogger.TOTAL);
                }
            }

            int xdbIdCountForDelete = dao.getCountOfXdbIdsModifiedBefore(XdbId.XDB_KEY_PHARMGKB, "PharmGKB", now);
            System.out.println("count of PharmGKB IDs to be deleted: "+xdbIdCountForDelete);

            // if count of rows to be deleted is greater than 10% of existing rows, that means trouble
            if( xdbIdCountForDelete > xdbIdCount/10 ) {
                System.out.println("***** count of PharmGKB IDs to be deleted is more than 10% of PharmGKB ids -- REVIEW recommended");
            }
            else if( xdbIdCountForDelete>0 ) {
                int count = dao.deleteXdbIdsModifiedBefore(XdbId.XDB_KEY_PHARMGKB, "PharmGKB", now);
                System.out.println("deleted "+count+" PharmGKB IDs");
                dbLogger.log("DELETED_FROM_RGD", Integer.toString(count), PipelineLogger.TOTAL);
            }

            dbLogger.getPipelineLog().setSuccess("OK");
            dbLogger.close(true);

            System.out.println("--SUCCESS--");
        }
        catch(Exception e) {
            e.printStackTrace();
            dbLogger.getPipelineLog().setSuccess(e.getMessage());
            dbLogger.close(false);

            // rethrow the exception
            throw e;
        }
    }

    public PreProcessor getPreProcessor() {
        return preProcessor;
    }

    public void setPreProcessor(PreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }

    public QCProcessor getQcProcessor() {
        return qcProcessor;
    }

    public void setQcProcessor(QCProcessor qcProcessor) {
        this.qcProcessor = qcProcessor;
    }

    public LoadProcessor getLoadProcessor() {
        return loadProcessor;
    }

    public void setLoadProcessor(LoadProcessor loadProcessor) {
        this.loadProcessor = loadProcessor;
    }

    public Dao getDao() {
        return dao;
    }

    public void setDao(Dao dao) {
        this.dao = dao;
    }


    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
