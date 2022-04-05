package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
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

    static Logger logger = LogManager.getLogger("status");

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
                        manager.run();
                        return;
                    }
                    case "--processMgiData": {
                        MgiManager manager = (MgiManager) (bf.getBean( "MgiManager"));
                        manager.run();
                        return;
                    }
                }
            }

        } catch(Exception e) {
            Utils.printStackTrace(e, logger);
            throw e;
        }
    }
}
