package edu.mcw.rgd.pipelines.hgnc;

public class Mgi {
    String accessionId;
    String chr;
    String position; // CM position
    String gnomeStart = null; //genome coordinate start
    String gnomeEnd = null; //genome coordinate end
    String strand = null; //strand
    String markerSymbol = null; // Marker Symbol
    String status = null; //Status
    String markerName = null; // Marker Name
    String markerType = null; //Marker Type
    String featureType = null; //Feature Type
    String synonyms = null; //Marker Synonyms (pipe-separated)

    public String getAccessionId(){return accessionId;}
    public String getChr(){return chr;}
    public String getPosition(){return position;}
    public String getGnomeStart(){return gnomeStart;}
    public String getGnomeEnd(){return gnomeEnd;}
    public String getStrand(){return strand;}
    public String getMarkerSymbol(){return markerSymbol;}
    public String getStatus(){return status;}
    public String getMarkerName(){return markerName;}
    public String getMarkerType(){return markerType;}
    public String getFeatureType(){return featureType;}
    public String getSynonyms(){return synonyms;}

    public void setAccessionId(String accessionId){this.accessionId = accessionId;}
    public void setChr(String chr){this.chr = chr;}
    public void setPosition(String position){this.position = position;}
    public void setGnomeStart(String gnomeStart){this.gnomeStart = gnomeStart;}
    public void setGnomeEnd(String gnomeEnd){this.gnomeEnd = gnomeEnd;}
    public void setStrand(String strand){this.strand = strand;}
    public void setMarkerSymbol(String markerSymbol){this.markerSymbol = markerSymbol;}
    public void setStatus(String status){this.status = status;}
    public void setMarkerName(String markerName){this.markerName = markerName;}
    public void setMarkerType(String markerType){this.markerType = markerType;}
    public void setFeatureType(String featureType){this.featureType = featureType;}
    public void setSynonyms(String synonyms){this.synonyms = synonyms;}

    @Override
    public String toString() {
        return "AccID: "+accessionId+"\tChr: "+chr+"\tCM pos: "+position+"\tGnome Start: "+gnomeStart +
                "\tGnome End: "+gnomeEnd +"\tStrand: "+strand+"\tMarker Sym: "+markerSymbol+"\tStatus: "+status
                +"\tMarker Name: "+markerName+"\tMarker Type: "+markerType+ "\tFeature Type: "+featureType+
                "\tMarker Synonym: "+synonyms;
    }
}
