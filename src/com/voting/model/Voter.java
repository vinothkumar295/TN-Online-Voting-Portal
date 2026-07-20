package com.voting.model;

import java.io.Serializable;

/**
 * Represents a registered voter in the voting system.
 * Implements Serializable for secure file persistence.
 */
public class Voter implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String voterId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String district;
    private String passwordHash;
    private String salt;
    private boolean hasVoted;
    private boolean isLocked;
    private int failedAttempts;

    public Voter(String voterId, String fullName, String email, String phoneNumber, String district, String passwordHash, String salt) {
        this.voterId = voterId;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.district = district;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.hasVoted = false;
        this.isLocked = false;
        this.failedAttempts = 0;
    }

    public Voter(String voterId, String fullName, String email, String phoneNumber, String passwordHash, String salt) {
        this(voterId, fullName, email, phoneNumber, "Chennai", passwordHash, salt);
    }

    public Voter(String voterId, String fullName, String email, String passwordHash, String salt) {
        this(voterId, fullName, email, "", "Chennai", passwordHash, salt);
    }

    public String getVoterId() {
        return voterId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getDistrict() {
        return district == null || district.isEmpty() ? "Chennai" : district;
    }

    public void setDistrict(String district) {
        this.district = district;
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

    public boolean isHasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
    }
}
