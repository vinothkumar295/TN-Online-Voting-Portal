package com.voting.data;

import com.voting.model.AdminAccount;
import com.voting.model.Candidate;
import com.voting.model.Voter;

import java.util.List;

/**
 * Data access abstraction interface.
 * Decouples VotingService business logic from the underlying storage mechanism
 * (e.g. File Serialization, JDBC MySQL, In-Memory).
 */
public interface IDataStore {

    Voter getVoterById(String voterId);

    Voter getVoterByEmail(String email);

    List<Voter> getAllVoters();

    void saveVoter(Voter voter);

    void updateVoter(Voter voter);

    Candidate getCandidateById(String candidateId);

    List<Candidate> getAllCandidates();

    void saveCandidate(Candidate candidate);

    void updateCandidate(Candidate candidate);

    void deleteCandidate(String candidateId);

    AdminAccount getAdminAccount();

    void saveAdminAccount(AdminAccount admin);

    boolean isElectionOpen();

    void setElectionOpen(boolean open);

    void logAudit(String action);

    List<String> getAuditLogs();
}
