package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.SpeciesType;

/**
 * gene information as parsed from the income HGNC/VGNC file
 */
public class HgncGene {

    public int taxonId;
    public int speciesTypeKey;
    public String hgncId; // or vgncId
    public String symbol;
    public String name;
    public String ncbiId;
    public String ensemblId;

    public Gene gene; // matching gene in rgd
    public String matchBy; // how the match was made
}
