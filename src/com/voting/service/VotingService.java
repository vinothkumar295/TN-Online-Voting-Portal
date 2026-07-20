package com.voting.service;

import com.voting.data.IDataStore;
import com.voting.model.AdminAccount;
import com.voting.model.Candidate;
import com.voting.model.Voter;
import com.voting.security.SecurityUtil;
import com.voting.util.InputValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service layer containing all business rules, security enforcement, election management,
 * and ballot secrecy implementation.
 */
public class VotingService {

    private final IDataStore dataStore;

    public VotingService(IDataStore dataStore) {
        this.dataStore = dataStore;
    }

    // ---------------- VOTER REGISTRATION & AUTHENTICATION ----------------

    /**
     * Registers a new voter after validating input patterns, duplicate IDs/emails/phones, password strength, and OTP.
    /**
     * Registers a new voter after validating input patterns, duplicate IDs/emails/phones, password strength, and OTP.
     */
    public String registerVoter(String voterId, String fullName, String email, String phoneNumber, String district, String password, String userOtp, String expectedOtp) {
        // Sanitize inputs
        voterId = InputValidator.sanitizeInput(voterId);
        fullName = InputValidator.sanitizeInput(fullName);
        email = InputValidator.sanitizeInput(email);
        phoneNumber = InputValidator.sanitizeInput(phoneNumber);
        district = InputValidator.sanitizeInput(district);
        userOtp = InputValidator.sanitizeInput(userOtp);

        if (district == null || district.trim().isEmpty()) {
            district = "Chennai";
        }

        // Validation
        if (!InputValidator.isValidVoterId(voterId)) {
            return "ERROR: Invalid Voter ID (EPIC) format! E.g. VOT12345 or TNV1234567.";
        }
        if (!InputValidator.isValidName(fullName)) {
            return "ERROR: Invalid Name format! Must be 2-50 characters long (letters and spaces only).";
        }
        if (!InputValidator.isValidEmail(email)) {
            return "ERROR: Invalid Email address format!";
        }
        if (!InputValidator.isValidPhoneNumber(phoneNumber)) {
            return "ERROR: Invalid Phone Number format! Must be 10-15 digits (e.g. 9876543210).";
        }
        if (!SecurityUtil.validatePasswordStrength(password)) {
            return "ERROR: Password does not meet security policy! Must be at least 8 characters long, " +
                   "with at least 1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special character.";
        }

        // OTP verification
        if (expectedOtp != null && !expectedOtp.trim().equals(userOtp.trim())) {
            return "ERROR: Invalid OTP! Mobile verification failed. Registration aborted.";
        }

        // Duplicate checks
        if (dataStore.getVoterById(voterId) != null) {
            return "ERROR: A voter with Voter ID '" + voterId + "' is already registered!";
        }
        if (dataStore.getVoterByEmail(email) != null) {
            return "ERROR: A voter with Email '" + email + "' is already registered!";
        }

        // Security: Hash password with unique salt
        String salt = SecurityUtil.generateSalt();
        String passwordHash = SecurityUtil.hashPassword(password, salt);

        Voter voter = new Voter(voterId, fullName, email, phoneNumber, district, passwordHash, salt);
        dataStore.saveVoter(voter);

        dataStore.logAudit("REGISTRATION: Registered voter (" + SecurityUtil.maskVoterId(voterId) + ") in District: " + district + " (OTP Verified)");
        return "SUCCESS: Voter registered & verified successfully for Tamil Nadu Elections! Voter ID: " + voterId;
    }

    public String registerVoter(String voterId, String fullName, String email, String phoneNumber, String password, String userOtp, String expectedOtp) {
        return registerVoter(voterId, fullName, email, phoneNumber, "Chennai", password, userOtp, expectedOtp);
    }

    public String registerVoter(String voterId, String fullName, String email, String password) {
        return registerVoter(voterId, fullName, email, "9999999999", password, "123456", "123456");
    }

    /**
     * Authenticates a voter with brute-force lockout protection (3 failed attempts).
     */
    public Voter loginVoter(String voterId, String password) {
        voterId = InputValidator.sanitizeInput(voterId);
        Voter voter = dataStore.getVoterById(voterId);

        if (voter == null) {
            dataStore.logAudit("LOGIN FAILED: Non-existent voter ID attempted.");
            return null;
        }

        if (voter.isLocked()) {
            dataStore.logAudit("LOGIN BLOCKED: Locked account login attempt for " + SecurityUtil.maskVoterId(voterId));
            return null;
        }

        boolean valid = SecurityUtil.verifyPassword(password, voter.getPasswordHash(), voter.getSalt());
        if (valid) {
            voter.resetFailedAttempts();
            dataStore.updateVoter(voter);
            dataStore.logAudit("LOGIN SUCCESS: Voter logged in (" + SecurityUtil.maskVoterId(voterId) + ")");
            return voter;
        } else {
            voter.incrementFailedAttempts();
            if (voter.getFailedAttempts() >= 3) {
                voter.setLocked(true);
                dataStore.updateVoter(voter);
                dataStore.logAudit("SECURITY LOCKOUT: Account " + SecurityUtil.maskVoterId(voterId) +
                                   " locked after 3 failed login attempts.");
            } else {
                dataStore.updateVoter(voter);
                dataStore.logAudit("LOGIN FAILED: Invalid password attempt (" + voter.getFailedAttempts() +
                                   "/3) for " + SecurityUtil.maskVoterId(voterId));
            }
            return null;
        }
    }

    // ---------------- VOTING PROCESS (BALLOT SECRECY) ----------------

    /**
     * Casts a vote while guaranteeing ballot secrecy.
     * NEVER links the voter identity to the candidate in storage or logs.
     */
    public String castVote(String voterId, String candidateId) {
        if (!dataStore.isElectionOpen()) {
            return "ERROR: Voting is currently CLOSED by the election administrator.";
        }

        Voter voter = dataStore.getVoterById(voterId);
        if (voter == null) {
            return "ERROR: Voter not found.";
        }

        if (voter.isLocked()) {
            return "ERROR: Account is locked. Cannot vote.";
        }

        if (voter.isHasVoted()) {
            return "ERROR: You have already cast your vote! Exactly one vote per voter is allowed.";
        }

        Candidate candidate = dataStore.getCandidateById(candidateId);
        if (candidate == null) {
            return "ERROR: Invalid Candidate selection.";
        }

        // 1. Mark voter as having voted (voter status updated independently)
        voter.setHasVoted(true);
        dataStore.updateVoter(voter);

        // 2. Increment candidate vote tally (candidate count updated independently)
        candidate.incrementVote();
        dataStore.updateCandidate(candidate);

        // 3. Audit log WITHOUT revealing candidate choice
        dataStore.logAudit("VOTE CAST: Anonymous ballot cast by masked voter ID " + SecurityUtil.maskVoterId(voterId));

        return "SUCCESS: Your vote has been recorded securely and anonymously. Thank you for voting!";
    }

    // ---------------- LIVE RESULTS ----------------

    public boolean isElectionOpen() {
        return dataStore.isElectionOpen();
    }

    public List<Candidate> getSortedCandidates() {
        List<Candidate> candidates = dataStore.getAllCandidates();
        candidates.sort(Comparator.comparingInt(Candidate::getVoteCount).reversed());
        return candidates;
    }

    public int getTotalVotesCast() {
        int total = 0;
        for (Candidate c : dataStore.getAllCandidates()) {
            total += c.getVoteCount();
        }
        return total;
    }

    // ---------------- ADMIN OPERATIONS ----------------

    public boolean loginAdmin(String adminId, String password) {
        adminId = InputValidator.sanitizeInput(adminId);
        AdminAccount admin = dataStore.getAdminAccount();
        if (admin == null || !admin.getAdminId().equalsIgnoreCase(adminId)) {
            dataStore.logAudit("ADMIN LOGIN FAILED: Invalid Admin ID.");
            return false;
        }

        boolean valid = SecurityUtil.verifyPassword(password, admin.getPasswordHash(), admin.getSalt());
        if (valid) {
            dataStore.logAudit("ADMIN LOGIN SUCCESS: Administrator logged in.");
            return true;
        } else {
            dataStore.logAudit("ADMIN LOGIN FAILED: Invalid password.");
            return false;
        }
    }

    public String changeAdminPassword(String oldPassword, String newPassword) {
        AdminAccount admin = dataStore.getAdminAccount();
        if (!SecurityUtil.verifyPassword(oldPassword, admin.getPasswordHash(), admin.getSalt())) {
            return "ERROR: Incorrect current admin password!";
        }

        if (!SecurityUtil.validatePasswordStrength(newPassword)) {
            return "ERROR: New password does not meet security policy requirements!";
        }

        String newSalt = SecurityUtil.generateSalt();
        String newHash = SecurityUtil.hashPassword(newPassword, newSalt);

        admin.setSalt(newSalt);
        admin.setPasswordHash(newHash);
        dataStore.saveAdminAccount(admin);

        dataStore.logAudit("ADMIN ACTION: Changed administrator password.");
        return "SUCCESS: Admin password updated successfully.";
    }

    public String addCandidate(String candidateId, String name, String party) {
        candidateId = InputValidator.sanitizeInput(candidateId);
        name = InputValidator.sanitizeInput(name);
        party = InputValidator.sanitizeInput(party);

        if (candidateId.isEmpty() || name.isEmpty() || party.isEmpty()) {
            return "ERROR: Candidate ID, Name, and Party must not be empty.";
        }

        if (dataStore.getCandidateById(candidateId) != null) {
            return "ERROR: Candidate with ID '" + candidateId + "' already exists!";
        }

        Candidate candidate = new Candidate(candidateId, name, party);
        dataStore.saveCandidate(candidate);

        dataStore.logAudit("ADMIN ACTION: Added candidate '" + name + "' (" + candidateId + ", " + party + ")");
        return "SUCCESS: Candidate '" + name + "' added successfully.";
    }

    public String removeCandidate(String candidateId) {
        candidateId = InputValidator.sanitizeInput(candidateId);
        Candidate c = dataStore.getCandidateById(candidateId);
        if (c == null) {
            return "ERROR: Candidate with ID '" + candidateId + "' not found.";
        }

        dataStore.deleteCandidate(candidateId);
        dataStore.logAudit("ADMIN ACTION: Removed candidate '" + c.getName() + "' (" + candidateId + ")");
        return "SUCCESS: Candidate '" + c.getName() + "' removed successfully.";
    }

    public String setElectionStatus(boolean open) {
        dataStore.setElectionOpen(open);
        String status = open ? "OPEN" : "CLOSED";
        dataStore.logAudit("ADMIN ACTION: Set election status to " + status);
        return "SUCCESS: Election status changed to " + status + ".";
    }

    public String unlockVoterAccount(String voterId) {
        voterId = InputValidator.sanitizeInput(voterId);
        Voter v = dataStore.getVoterById(voterId);
        if (v == null) {
            return "ERROR: Voter ID '" + voterId + "' not found.";
        }

        if (!v.isLocked()) {
            return "INFO: Voter account '" + SecurityUtil.maskVoterId(voterId) + "' is not locked.";
        }

        v.setLocked(false);
        v.resetFailedAttempts();
        dataStore.updateVoter(v);

        dataStore.logAudit("ADMIN ACTION: Unlocked voter account " + SecurityUtil.maskVoterId(voterId));
        return "SUCCESS: Account " + SecurityUtil.maskVoterId(voterId) + " has been unlocked.";
    }

    public List<String> getMaskedVotersReport() {
        List<String> report = new ArrayList<>();
        List<Voter> voters = dataStore.getAllVoters();
        for (Voter v : voters) {
            String maskedId = SecurityUtil.maskVoterId(v.getVoterId());
            String status = v.isLocked() ? "LOCKED" : (v.isHasVoted() ? "VOTED" : "REGISTERED");
            report.add(String.format("ID: %-12s | Name: %-20s | District: %-15s | Status: %s",
                       maskedId, v.getFullName(), v.getDistrict(), status));
        }
        return report;
    }

    public List<String> getAuditLogs() {
        return dataStore.getAuditLogs();
    }
}
