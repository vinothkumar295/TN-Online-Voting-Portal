package com.voting.model;

import java.io.Serializable;

/**
 * Represents the administrator account.
 * Implements Serializable for persistence.
 */
public class AdminAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String adminId;
    private String passwordHash;
    private String salt;

    public AdminAccount(String adminId, String passwordHash, String salt) {
        this.adminId = adminId;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public String getAdminId() {
        return adminId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }
}
