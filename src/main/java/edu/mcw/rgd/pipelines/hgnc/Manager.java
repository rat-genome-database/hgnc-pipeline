package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

/**
 * @author mtutaj
 * @since 12/22/11
 * Load gene families from HGNC site
 */
public class Manager {

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));

        Logger logger = null;

        boolean readOnlyMode = false;
        // preprocess cmdline params
        for( String arg : args ) {
            switch (arg) {
                case "--readOnlyMode" -> {
                    readOnlyMode = true;
                }
            }
        }
        Dao dao = new Dao();
        if( readOnlyMode ) {
            dao.setReadOnlyMode(true);
        }

        try {
            // process cmdline params
            for( String arg : args ) {
                switch (arg) {
                    case "--processObsoleteHgncIds" -> {
                        ObsoleteHgncIdManager manager = (ObsoleteHgncIdManager) (bf.getBean("obsoleteHgncIdManager"));
                        logger = manager.logDb;
                        manager.run();
                    }
                    case "--processHgncIds" -> {
                        HgncIdManager manager = (HgncIdManager) (bf.getBean("hgncIdManager"));
                        logger = manager.logDb;
                        manager.run(dao);
                    }
                    case "--processMgiData" -> {
                        MgiManager manager = (MgiManager) (bf.getBean("MgiManager"));
                        logger = manager.logger;
                        manager.run();
                    }
                }
            }

        } catch(Exception e) {
            if( logger != null ) {
                Utils.printStackTrace(e, logger);
            } else {
                e.printStackTrace();
            }
            throw e;
        }
    }

}
