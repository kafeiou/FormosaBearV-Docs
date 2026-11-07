package com.sismics.docs.core.dao.dto;

import java.util.Date;

/**
 * Tag DTO.
 *
 * @author bgamard 
 */
public class TagDto {
    /**
     * Tag ID.
     */
    private String id;
    
    /**
     * Name.
     */
    private String name;
    
    /**
     * Color.
     */
    private String color;
    
    /**
     * Parent ID.
     */
    private String parentId;

    /**
     * Creator.
     */
    private String creator;

    /**
     * Display order.
     */
    private Integer order;

    /**
     * Creation date.
     */
    private Date createDate;

    /**
     * Whether this tag inherits permissions from ancestor tags.
     */
    private boolean inheritAcl = true;

    /**
     * Whether this tag is displayed only as a path container.
     */
    private boolean container = false;

    public String getId() {
        return id;
    }

    public TagDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public TagDto setName(String name) {
        this.name = name;
        return this;
    }

    public String getColor() {
        return color;
    }

    public TagDto setColor(String color) {
        this.color = color;
        return this;
    }
    
    public String getParentId() {
        return parentId;
    }

    public TagDto setParentId(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public String getCreator() {
        return creator;
    }

    public TagDto setCreator(String creator) {
        this.creator = creator;
        return this;
    }

    public Integer getOrder() {
        return order;
    }

    public TagDto setOrder(Integer order) {
        this.order = order;
        return this;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public TagDto setCreateDate(Date createDate) {
        this.createDate = createDate;
        return this;
    }

    public boolean isInheritAcl() {
        return inheritAcl;
    }

    public TagDto setInheritAcl(boolean inheritAcl) {
        this.inheritAcl = inheritAcl;
        return this;
    }

    public boolean isContainer() {
        return container;
    }

    public TagDto setContainer(boolean container) {
        this.container = container;
        return this;
    }
}
