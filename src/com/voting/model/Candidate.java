package com.voting.model;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an election candidate.
 * Uses AtomicInteger for thread-safe vote tally increments.
 */
public class Candidate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String candidateId;
    private String name;
    private String party;
    private final AtomicInteger voteCount;

    public Candidate(String candidateId, String name, String party) {
        this.candidateId = candidateId;
        this.name = name;
        this.party = party;
        this.voteCount = new AtomicInteger(0);
    }

    public Candidate(String candidateId, String name, String party, int initialVotes) {
        this.candidateId = candidateId;
        this.name = name;
        this.party = party;
        this.voteCount = new AtomicInteger(initialVotes);
    }

    public String getCandidateId() {
        return candidateId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParty() {
        return party;
    }

    public void setParty(String party) {
        this.party = party;
    }

    public int getVoteCount() {
        return voteCount.get();
    }

    public void incrementVote() {
        this.voteCount.incrementAndGet();
    }
}
