package edu.mcw.rgd.pipelines.hgnc;

import java.util.Date;

/**
 * represents a row in OBSOLETE_HGNC_IDS table
 */
public class ObsoleteHgncId {

    private String hgncId;
    private String status;
    private String withdrawnSymbol;
    private String mergedIntoReport;
    private Date createdDate;
    private Date lastModifiedDate;

    public String getHgncId() {
        return hgncId;
    }

    public void setHgncId(String hgncId) {
        this.hgncId = hgncId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWithdrawnSymbol() {
        return withdrawnSymbol;
    }

    public void setWithdrawnSymbol(String withdrawnSymbol) {
        this.withdrawnSymbol = withdrawnSymbol;
    }

    public String getMergedIntoReport() {
        return mergedIntoReport;
    }

    public void setMergedIntoReport(String mergedIntoReport) {
        this.mergedIntoReport = mergedIntoReport;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Date lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
