package edu.mcw.rgd.pipelines.hgnc;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.pipelines.PipelineRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nu.xom.*;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Apr 29, 2011
 * Time: 8:45:07 AM
 * custom data, both read from incoming data and from rgd database
 */
public class HgncRecord extends PipelineRecord {

    // incoming data
    // URL	Gene Family Tag	Gene Family Description	Symbol	HGNC ID
    private String externalUrl;
    private String familySymbol;
    private String familyName;
    private Map<Integer, String> mapHgncIdToGeneSymbol = new HashMap<>();

    // matching active genes
    private List<Gene> genesInRgdMatchingByGeneId;
    private List<Gene> genesInRgdMatchingByEnsemblId;
    private List<Gene> genesInRgdMatchingByUniProtId;

    private int matchingRgdId; // matching rgd id of an active gene

    // LOADING
    private XdbId xdbIdForUpdate; // MODIFICATION_DATE is to be updated
    private XdbId xdbIdForInsert; // to be inserted into RGD

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getFamilySymbol() {
        return familySymbol;
    }

    public void setFamilySymbol(String familySymbol) {
        this.familySymbol = familySymbol;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public Map<Integer, String> getMapHgncIdToGeneSymbol() {
        return mapHgncIdToGeneSymbol;
    }

    public void setMapHgncIdToGeneSymbol(Map<Integer, String> mapHgncIdToGeneSymbol) {
        this.mapHgncIdToGeneSymbol = mapHgncIdToGeneSymbol;
    }

    public List<Gene> getGenesInRgdMatchingByGeneId() {
        return genesInRgdMatchingByGeneId;
    }

    public void setGenesInRgdMatchingByGeneId(List<Gene> genesInRgdMatchingByGeneId) {
        this.genesInRgdMatchingByGeneId = genesInRgdMatchingByGeneId;
    }

    public List<Gene> getGenesInRgdMatchingByEnsemblId() {
        return genesInRgdMatchingByEnsemblId;
    }

    public void setGenesInRgdMatchingByEnsemblId(List<Gene> genesInRgdMatchingByEnsemblId) {
        this.genesInRgdMatchingByEnsemblId = genesInRgdMatchingByEnsemblId;
    }

    public List<Gene> getGenesInRgdMatchingByUniProtId() {
        return genesInRgdMatchingByUniProtId;
    }

    public void setGenesInRgdMatchingByUniProtId(List<Gene> genesInRgdMatchingByUniProtId) {
        this.genesInRgdMatchingByUniProtId = genesInRgdMatchingByUniProtId;
    }

    public int getMatchingRgdId() {
        return matchingRgdId;
    }

    public void setMatchingRgdId(int matchingRgdId) {
        this.matchingRgdId = matchingRgdId;
    }

    public XdbId getXdbIdForUpdate() {
        return xdbIdForUpdate;
    }

    public void setXdbIdForUpdate(XdbId xdbIdForUpdate) {
        this.xdbIdForUpdate = xdbIdForUpdate;
    }

    public XdbId getXdbIdForInsert() {
        return xdbIdForInsert;
    }

    public void setXdbIdForInsert(XdbId xdbIdForInsert) {
        this.xdbIdForInsert = xdbIdForInsert;
    }

    public String toXml() {

        Element root = new Element("rec");

        // incoming data
        Element hgnc = new Element("hgnc");
        root.appendChild(hgnc);

        hgnc.addAttribute(new Attribute("recno", Integer.toString(this.getRecNo())));

        Element el = new Element("familySymbol");
        el.appendChild(this.getFamilySymbol());
        hgnc.appendChild(el);

        el = new Element("familyName");
        el.appendChild(this.getFamilyName());
        hgnc.appendChild(el);

        el = new Element("externalUrl");
        el.appendChild(this.getExternalUrl());
        hgnc.appendChild(el);

        el = new Element("genes");
        for( Map.Entry<Integer, String> entry: this.getMapHgncIdToGeneSymbol().entrySet() ) {

            Element child = new Element("gene");
            child.addAttribute(new Attribute("symbol", entry.getValue()));
            child.addAttribute(new Attribute("hgncId", Integer.toString(entry.getKey())));
            el.appendChild(child);
        }
        hgnc.appendChild(el);

        // rgd
        Element rgd = new Element("rgd");
        root.appendChild(rgd);

        el = new Element("matchingRgdId");
        el.appendChild(Integer.toString(this.matchingRgdId));
        rgd.appendChild(el);
        return root.toXML();
    }

    public void appendGeneList(Element el, List<Gene> geneList, String elName) {

        Element el2 = new Element(elName);
        el.appendChild(el2);

        for( Gene gene: geneList ) {
            Element el3 = new Element("gene");
            el3.addAttribute(new Attribute("rgdId", Integer.toString(gene.getRgdId())));
            el3.addAttribute(new Attribute("symbol", gene.getSymbol()));
            el2.appendChild(el3);
        }
    }
}
