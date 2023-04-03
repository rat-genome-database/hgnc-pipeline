package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.dao.impl.GeneDAO;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.MappedGene;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    /*
    static void test() throws Exception {

        BufferedReader in = Utils.openReader("/tmp/snpEff_genes_joint72.txt");
        BufferedWriter out = Utils.openWriter("/tmp/snpEff_genes_joint72.out");

        GeneDAO dao = new GeneDAO();

        int lines = 0;
        String line;
        while( (line=in.readLine())!=null ) {
            lines++;
            int tabPos = line.indexOf('\t');
            if( tabPos > 0 ) {
                String chr = "";
                String geneSymbol = line.substring(0, tabPos);
                System.out.println(lines+". "+geneSymbol);
                List<String> geneSymbols = new ArrayList<>(1);
                geneSymbols.add(geneSymbol);
                List<MappedGene> genes = dao.getActiveMappedGenes(372, geneSymbols);
                if( genes.isEmpty() ) {
                    System.out.println("Cannot resolve symbol: "+geneSymbol);
                } else if( genes.size()>1 ) {
                    System.out.println("Multiple genes: "+geneSymbol);
                } else {
                    chr = genes.get(0).getChromosome();
                }
                out.write(chr);
                out.write('\t');
                out.write(line);
                out.write("\n");
            }
            else {
                out.write(line);
                out.write("\n");
            }
        }

        in.close();
        out.close();

        System.exit(0);
    }
    */
}
