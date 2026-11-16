package com.sismics.docs.core.dao.dto;

/**
 * Contributor DTO.
 * 
 * @author bgamard
 */
public class ContributorDto {
    /**
     * Username.
     */
    private String username;

    /**
     * Optional display name.
     */
    private String name;
    
    /**
     * Email.
     */
    private String email;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
