package com.sismics.docs.core.model.jpa;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.google.common.base.MoreObjects;

/**
 * ACL target used to share a document.
 * Can only be used on a single ACL
 * 
 * @author bgamard
 */
@Entity
@Table(name = "T_SHARE")
public class Share {
    /**
     * Share ID.
     */
    @Id
    @Column(name = "SHA_ID_C", length = 36)
    private String id;

    @Column(name = "SHA_NAME_C", length = 36)
    private String name;
    
    /**
     * Creation date.
     */
    @Column(name = "SHA_CREATEDATE_D", nullable = false)
    private Date createDate;
    
    /**
     * Deletion date.
     */
    @Column(name = "SHA_DELETEDATE_D")
    private Date deleteDate;

    /**
     * Allow export / download through this share link (default false: preview only).
     */
    @Column(name = "SHA_ALLOWEXPORT_B", nullable = false)
    private boolean allowExport = false;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Date getDeleteDate() {
        return deleteDate;
    }

    public void setDeleteDate(Date deleteDate) {
        this.deleteDate = deleteDate;
    }

    public boolean isAllowExport() {
        return allowExport;
    }

    public void setAllowExport(boolean allowExport) {
        this.allowExport = allowExport;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .toString();
    }
}
