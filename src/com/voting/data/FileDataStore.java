package com.voting.data;

import com.voting.model.AdminAccount;
import com.voting.model.Candidate;
import com.voting.model.Voter;
import com.voting.security.SecurityUtil;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * File-based persistence implementation using Java Object Serialization.
 * Manages thread-safe storage in the 'datastore/' directory.
 * Automatically seeds default Admin account and initial sample candidates on first launch.
 */
public class FileDataStore implements IDataStore {

    private static final String DATA_DIR = "datastore";
    private static final String VOTERS_FILE = DATA_DIR + File.separator + "voters.dat";
    private static final String CANDIDATES_FILE = DATA_DIR + File.separator + "candidates.dat";
    private static final String ADMIN_FILE = DATA_DIR + File.separator + "admin.dat";
    private static final String ELECTION_FILE = DATA_DIR + File.separator + "election.dat";
    private static final String AUDIT_LOG_FILE = DATA_DIR + File.separator + "audit.log";

    private final Map<String, Voter> votersMap = new HashMap<>();
    private final Map<String, Candidate> candidatesMap = new LinkedHashMap<>();
    private AdminAccount adminAccount;
    private boolean electionOpen = true;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FileDataStore() {
        initDataStore();
    }

    private synchronized void initDataStore() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        loadAdmin();
        loadCandidates();
        loadVoters();
        loadElectionState();
    }

    // ---------------- ADMIN ACCOUNT PERSISTENCE ----------------
    private void loadAdmin() {
        File file = new File(ADMIN_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                adminAccount = (AdminAccount) ois.readObject();
            } catch (Exception e) {
                System.err.println("Warning: Error reading admin.dat, recreating default admin account.");
                seedDefaultAdmin();
            }
        } else {
            seedDefaultAdmin();
        }
    }

    private void seedDefaultAdmin() {
        String defaultSalt = SecurityUtil.generateSalt();
        String defaultHash = SecurityUtil.hashPassword("Admin@123", defaultSalt);
        adminAccount = new AdminAccount("admin", defaultHash, defaultSalt);
        saveAdminAccount(adminAccount);
        logAudit("SYSTEM: Initialized default admin account.");
    }

    @Override
    public synchronized AdminAccount getAdminAccount() {
        return adminAccount;
    }

    @Override
    public synchronized void saveAdminAccount(AdminAccount admin) {
        this.adminAccount = admin;
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ADMIN_FILE))) {
            oos.writeObject(admin);
        } catch (IOException e) {
            System.err.println("Error saving admin data: " + e.getMessage());
        }
    }

    // ---------------- CANDIDATES PERSISTENCE ----------------
    @SuppressWarnings("unchecked")
    private void loadCandidates() {
        File file = new File(CANDIDATES_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                List<Candidate> list = (List<Candidate>) ois.readObject();
                candidatesMap.clear();
                for (Candidate c : list) {
                    candidatesMap.put(c.getCandidateId(), c);
                }
            } catch (Exception e) {
                System.err.println("Warning: Error reading candidates.dat, re-seeding candidates.");
                seedSampleCandidates();
            }
        } else {
            seedSampleCandidates();
        }
    }

    private void seedSampleCandidates() {
        candidatesMap.clear();
        Candidate c1 = new Candidate("TN-CAND-01", "M. K. Stalin (மு.க. ஸ்டாலின்)", "DMK - திமுக (உதயசூரியன் ☀️)");
        Candidate c2 = new Candidate("TN-CAND-02", "Edappadi K. Palaniswami (எடப்பாடி பழனிசாமி)", "AIADMK - அதிமுக (இரட்டை இலை 🍃)");
        Candidate c3 = new Candidate("TN-CAND-03", "Vijay (விஜய்)", "TVK - தவேக (வெற்றி சின்னம் ⭐)");
        Candidate c4 = new Candidate("TN-CAND-04", "Seeman (சீமான்)", "NTK - நாம் தமிழர் (விவசாயி 🌾)");
        Candidate c5 = new Candidate("TN-CAND-05", "K. Annamalai (கே. அண்ணாமலை)", "BJP - பாஜக (தாமரை 🪷)");

        candidatesMap.put(c1.getCandidateId(), c1);
        candidatesMap.put(c2.getCandidateId(), c2);
        candidatesMap.put(c3.getCandidateId(), c3);
        candidatesMap.put(c4.getCandidateId(), c4);
        candidatesMap.put(c5.getCandidateId(), c5);

        persistCandidates();
        logAudit("SYSTEM: Seeded Tamil Nadu election candidates (DMK, AIADMK, TVK, NTK, BJP).");
    }

    private void persistCandidates() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CANDIDATES_FILE))) {
            oos.writeObject(new ArrayList<>(candidatesMap.values()));
        } catch (IOException e) {
            System.err.println("Error saving candidates data: " + e.getMessage());
        }
    }

    @Override
    public synchronized Candidate getCandidateById(String candidateId) {
        return candidatesMap.get(candidateId);
    }

    @Override
    public synchronized List<Candidate> getAllCandidates() {
        return new ArrayList<>(candidatesMap.values());
    }

    @Override
    public synchronized void saveCandidate(Candidate candidate) {
        candidatesMap.put(candidate.getCandidateId(), candidate);
        persistCandidates();
    }

    @Override
    public synchronized void updateCandidate(Candidate candidate) {
        saveCandidate(candidate);
    }

    @Override
    public synchronized void deleteCandidate(String candidateId) {
        candidatesMap.remove(candidateId);
        persistCandidates();
    }

    // ---------------- VOTERS PERSISTENCE ----------------
    @SuppressWarnings("unchecked")
    private void loadVoters() {
        File file = new File(VOTERS_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                List<Voter> list = (List<Voter>) ois.readObject();
                votersMap.clear();
                for (Voter v : list) {
                    votersMap.put(v.getVoterId(), v);
                }
            } catch (Exception e) {
                System.err.println("Warning: Error reading voters.dat, initializing empty voter registry.");
            }
        }
    }

    private void persistVoters() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(VOTERS_FILE))) {
            oos.writeObject(new ArrayList<>(votersMap.values()));
        } catch (IOException e) {
            System.err.println("Error saving voters data: " + e.getMessage());
        }
    }

    @Override
    public synchronized Voter getVoterById(String voterId) {
        return votersMap.get(voterId);
    }

    @Override
    public synchronized Voter getVoterByEmail(String email) {
        for (Voter v : votersMap.values()) {
            if (v.getEmail().equalsIgnoreCase(email)) {
                return v;
            }
        }
        return null;
    }

    @Override
    public synchronized List<Voter> getAllVoters() {
        return new ArrayList<>(votersMap.values());
    }

    @Override
    public synchronized void saveVoter(Voter voter) {
        votersMap.put(voter.getVoterId(), voter);
        persistVoters();
    }

    @Override
    public synchronized void updateVoter(Voter voter) {
        saveVoter(voter);
    }

    // ---------------- ELECTION STATE ----------------
    private void loadElectionState() {
        File file = new File(ELECTION_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                electionOpen = ois.readBoolean();
            } catch (Exception e) {
                electionOpen = true;
            }
        } else {
            electionOpen = true;
            persistElectionState();
        }
    }

    private void persistElectionState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ELECTION_FILE))) {
            oos.writeBoolean(electionOpen);
        } catch (IOException e) {
            System.err.println("Error saving election state: " + e.getMessage());
        }
    }

    @Override
    public synchronized boolean isElectionOpen() {
        return electionOpen;
    }

    @Override
    public synchronized void setElectionOpen(boolean open) {
        this.electionOpen = open;
        persistElectionState();
    }

    // ---------------- AUDIT LOG ----------------
    @Override
    public synchronized void logAudit(String action) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String entry = String.format("[%s] %s", timestamp, action);

        try (PrintWriter writer = new PrintWriter(new FileWriter(AUDIT_LOG_FILE, true))) {
            writer.println(entry);
        } catch (IOException e) {
            System.err.println("Error writing to audit log: " + e.getMessage());
        }
    }

    @Override
    public synchronized List<String> getAuditLogs() {
        List<String> logs = new ArrayList<>();
        File file = new File(AUDIT_LOG_FILE);
        if (!file.exists()) {
            return logs;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading audit log: " + e.getMessage());
        }
        return logs;
    }
}
