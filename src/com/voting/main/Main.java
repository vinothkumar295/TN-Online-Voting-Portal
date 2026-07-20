package com.voting.main;

import com.voting.data.FileDataStore;
import com.voting.data.IDataStore;
import com.voting.model.Candidate;
import com.voting.model.Voter;
import com.voting.security.SecurityUtil;
import com.voting.service.VotingService;
import com.voting.util.InputValidator;

import java.io.Console;
import java.util.List;
import java.util.Scanner;

/**
 * Main application entry point providing the interactive console UI.
 */
public class Main {

    private static VotingService votingService;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        IDataStore dataStore = new FileDataStore();
        votingService = new VotingService(dataStore);

        // Start Web UI Server on http://localhost:8080
        try {
            com.voting.web.WebServer webServer = new com.voting.web.WebServer(votingService, 8080);
            webServer.start();
        } catch (Exception e) {
            System.err.println("Notice: Web server could not start on port 8080: " + e.getMessage());
        }

        System.out.println("=================================================");
        System.out.println("     WELCOME TO THE SECURE ONLINE VOTING SYSTEM   ");
        System.out.println("=================================================");

        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = readInput("Enter your choice (1-5): ");
            switch (choice) {
                case "1":
                    handleVoterRegistration();
                    break;
                case "2":
                    handleVoterLogin();
                    break;
                case "3":
                    handleAdminLogin();
                    break;
                case "4":
                    displayLiveResults();
                    break;
                case "5":
                    System.out.println("\nThank you for using the Secure Online Voting System. Goodbye!");
                    running = false;
                    break;
                default:
                    System.out.println("\n[!] Invalid choice. Please select an option between 1 and 5.");
            }
        }
        scanner.close();
    }

    private static void printMainMenu() {
        String status = votingService.isElectionOpen() ? "OPEN" : "CLOSED";
        System.out.println("\n-------------------------------------------------");
        System.out.println(" MAIN MENU | Election Status: [" + status + "]");
        System.out.println("-------------------------------------------------");
        System.out.println(" 1. Register New Voter");
        System.out.println(" 2. Voter Login");
        System.out.println(" 3. Admin Login");
        System.out.println(" 4. View Live Election Results");
        System.out.println(" 5. Exit System");
        System.out.println("-------------------------------------------------");
    }

    // ---------------- VOTER FLOWS ----------------

    private static void handleVoterRegistration() {
        System.out.println("\n--- TAMIL NADU VOTER REGISTRATION (OTP VERIFIED) ---");
        System.out.println("Note: Voter ID / EPIC Number (e.g. TNV1234567 or VOT12345).");
        String voterId = readInput("Enter Voter ID (EPIC): ");
        String fullName = readInput("Enter Full Name: ");
        String email = readInput("Enter Email Address: ");
        String phoneNumber = readInput("Enter Mobile Phone Number: ");
        String district = readInput("Enter District (e.g. Chennai, Madurai, Coimbatore, Salem): ");
        
        System.out.println("Password requirement: Min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special character.");
        String password = readPassword("Enter Password: ");

        // Generate 6-digit OTP
        String generatedOtp = SecurityUtil.generateOTP();
        System.out.println("\n-------------------------------------------------");
        System.out.println(" 📲 [TAMIL NADU SMS GATEWAY SIMULATION]");
        System.out.println(" OTP sent to " + phoneNumber + ": >>> " + generatedOtp + " <<<");
        System.out.println("-------------------------------------------------");

        String userOtp = readInput("Enter the 6-digit OTP received on your mobile: ");

        String result = votingService.registerVoter(voterId, fullName, email, phoneNumber, district, password, userOtp, generatedOtp);
        System.out.println("\n" + result);
    }

    private static void handleVoterLogin() {
        System.out.println("\n--- VOTER LOGIN ---");
        String voterId = readInput("Enter Voter ID: ");
        String password = readPassword("Enter Password: ");

        Voter voter = votingService.loginVoter(voterId, password);
        if (voter == null) {
            System.out.println("\n[!] Login failed. Invalid credentials or account locked after 3 failed attempts.");
            return;
        }

        System.out.println("\n[+] Login successful! Welcome, " + voter.getFullName() + ".");
        runVoterSession(voter);
    }

    private static void runVoterSession(Voter voter) {
        boolean inSession = true;
        while (inSession) {
            String maskedId = SecurityUtil.maskVoterId(voter.getVoterId());
            System.out.println("\n-------------------------------------------------");
            System.out.println(" VOTER DASHBOARD | ID: " + maskedId + " | Voted: " + (voter.isHasVoted() ? "YES" : "NO"));
            System.out.println("-------------------------------------------------");
            System.out.println(" 1. View Candidates & Cast Vote");
            System.out.println(" 2. View Live Results");
            System.out.println(" 3. Logout");
            System.out.println("-------------------------------------------------");

            String choice = readInput("Select option: ");
            switch (choice) {
                case "1":
                    handleCastVote(voter);
                    // Refresh voter state after voting attempt
                    voter = votingService.loginVoter(voter.getVoterId(), ""); 
                    // Note: voter.isHasVoted() state is checked in VotingService
                    break;
                case "2":
                    displayLiveResults();
                    break;
                case "3":
                    System.out.println("\n[+] Logged out of voter session.");
                    inSession = false;
                    break;
                default:
                    System.out.println("\n[!] Invalid choice.");
            }
        }
    }

    private static void handleCastVote(Voter currentVoter) {
        if (!votingService.isElectionOpen()) {
            System.out.println("\n[!] Election is currently CLOSED. Votes are not being accepted.");
            return;
        }

        if (currentVoter.isHasVoted()) {
            System.out.println("\n[!] You have already cast your vote in this election.");
            return;
        }

        List<Candidate> candidates = votingService.getSortedCandidates();
        if (candidates.isEmpty()) {
            System.out.println("\n[!] No candidates currently registered in the election.");
            return;
        }

        System.out.println("\n-------------------------------------------------");
        System.out.println(" OFFICIAL ELECTION BALLOT");
        System.out.println("-------------------------------------------------");
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            System.out.printf(" %d. ID: %-8s | %-20s | Party: %s%n",
                    (i + 1), c.getCandidateId(), c.getName(), c.getParty());
        }
        System.out.println("-------------------------------------------------");

        String choiceStr = readInput("Select candidate number to vote for (or 0 to cancel): ");
        try {
            int choice = Integer.parseInt(choiceStr);
            if (choice == 0) {
                System.out.println("\nVoting cancelled.");
                return;
            }
            if (choice < 1 || choice > candidates.size()) {
                System.out.println("\n[!] Invalid candidate selection.");
                return;
            }

            Candidate chosenCandidate = candidates.get(choice - 1);
            System.out.println("\nCONFIRMATION:");
            System.out.println("Selected Candidate: " + chosenCandidate.getName() + " (" + chosenCandidate.getParty() + ")");
            String confirm = readInput("Are you sure you want to cast your vote? (YES/NO): ");

            if (confirm.equalsIgnoreCase("YES")) {
                String result = votingService.castVote(currentVoter.getVoterId(), chosenCandidate.getCandidateId());
                System.out.println("\n" + result);
                currentVoter.setHasVoted(true);
            } else {
                System.out.println("\nVote cancelled.");
            }

        } catch (NumberFormatException e) {
            System.out.println("\n[!] Invalid input. Please enter a valid number.");
        }
    }

    // ---------------- ADMIN FLOWS ----------------

    private static void handleAdminLogin() {
        System.out.println("\n--- ADMINISTRATOR LOGIN ---");
        String adminId = readInput("Enter Admin ID: ");
        String password = readPassword("Enter Admin Password: ");

        if (!votingService.loginAdmin(adminId, password)) {
            System.out.println("\n[!] Admin login failed. Invalid ID or password.");
            return;
        }

        System.out.println("\n[+] Administrator authentication successful!");
        runAdminSession();
    }

    private static void runAdminSession() {
        boolean inSession = true;
        while (inSession) {
            System.out.println("\n-------------------------------------------------");
            System.out.println(" ADMINISTRATOR CONTROL PANEL");
            System.out.println("-------------------------------------------------");
            System.out.println(" 1. View Live Results");
            System.out.println(" 2. Add New Candidate");
            System.out.println(" 3. Remove Candidate");
            System.out.println(" 4. Open / Close Election");
            System.out.println(" 5. View Registered Voters List (Masked)");
            System.out.println(" 6. Unlock Voter Account");
            System.out.println(" 7. Change Admin Password");
            System.out.println(" 8. View Audit Log");
            System.out.println(" 9. Logout Admin Session");
            System.out.println("-------------------------------------------------");

            String choice = readInput("Select option: ");
            switch (choice) {
                case "1":
                    displayLiveResults();
                    break;
                case "2":
                    handleAddCandidate();
                    break;
                case "3":
                    handleRemoveCandidate();
                    break;
                case "4":
                    handleToggleElection();
                    break;
                case "5":
                    handleViewVoters();
                    break;
                case "6":
                    handleUnlockVoter();
                    break;
                case "7":
                    handleChangeAdminPassword();
                    break;
                case "8":
                    handleViewAuditLog();
                    break;
                case "9":
                    System.out.println("\n[+] Logged out of Admin session.");
                    inSession = false;
                    break;
                default:
                    System.out.println("\n[!] Invalid choice.");
            }
        }
    }

    private static void handleAddCandidate() {
        System.out.println("\n--- ADD NEW CANDIDATE ---");
        String candidateId = readInput("Enter Candidate ID (e.g. CAND-4): ");
        String name = readInput("Enter Candidate Full Name: ");
        String party = readInput("Enter Political Party: ");

        String result = votingService.addCandidate(candidateId, name, party);
        System.out.println("\n" + result);
    }

    private static void handleRemoveCandidate() {
        System.out.println("\n--- REMOVE CANDIDATE ---");
        String candidateId = readInput("Enter Candidate ID to remove: ");
        String result = votingService.removeCandidate(candidateId);
        System.out.println("\n" + result);
    }

    private static void handleToggleElection() {
        System.out.println("\n--- ELECTION STATUS CONTROL ---");
        boolean current = votingService.isElectionOpen();
        System.out.println("Current Election Status: " + (current ? "OPEN" : "CLOSED"));
        String prompt = current ? "Do you want to CLOSE the election? (YES/NO): " : "Do you want to OPEN the election? (YES/NO): ";
        String answer = readInput(prompt);

        if (answer.equalsIgnoreCase("YES")) {
            String result = votingService.setElectionStatus(!current);
            System.out.println("\n" + result);
        } else {
            System.out.println("\nNo change made to election status.");
        }
    }

    private static void handleViewVoters() {
        System.out.println("\n--- REGISTERED VOTERS (PRIVACY MASKED) ---");
        List<String> report = votingService.getMaskedVotersReport();
        if (report.isEmpty()) {
            System.out.println("No voters currently registered.");
        } else {
            for (String line : report) {
                System.out.println(line);
            }
        }
    }

    private static void handleUnlockVoter() {
        System.out.println("\n--- UNLOCK LOCKED VOTER ACCOUNT ---");
        String voterId = readInput("Enter Voter ID to unlock: ");
        String result = votingService.unlockVoterAccount(voterId);
        System.out.println("\n" + result);
    }

    private static void handleChangeAdminPassword() {
        System.out.println("\n--- CHANGE ADMIN PASSWORD ---");
        String oldPassword = readPassword("Enter Current Password: ");
        String newPassword = readPassword("Enter New Password: ");

        String result = votingService.changeAdminPassword(oldPassword, newPassword);
        System.out.println("\n" + result);
    }

    private static void handleViewAuditLog() {
        System.out.println("\n--- SYSTEM AUDIT LOG ---");
        List<String> logs = votingService.getAuditLogs();
        if (logs.isEmpty()) {
            System.out.println("Audit log is empty.");
        } else {
            for (String entry : logs) {
                System.out.println(entry);
            }
        }
    }

    // ---------------- COMMON UTILITIES ----------------

    private static void displayLiveResults() {
        System.out.println("\n=================================================");
        System.out.println("            LIVE ELECTION RESULTS                ");
        System.out.println("=================================================");
        List<Candidate> candidates = votingService.getSortedCandidates();
        int totalVotes = votingService.getTotalVotesCast();

        System.out.println("Total Votes Cast: " + totalVotes);
        System.out.println("Election Status : " + (votingService.isElectionOpen() ? "OPEN" : "CLOSED"));
        System.out.println("-------------------------------------------------");

        if (candidates.isEmpty()) {
            System.out.println("No candidates found.");
        } else {
            System.out.printf("%-10s | %-20s | %-15s | %-8s | %-8s%n",
                    "ID", "Candidate Name", "Party", "Votes", "Percent");
            System.out.println("------------------------------------------------------------------");
            for (Candidate c : candidates) {
                double percentage = totalVotes == 0 ? 0.0 : ((double) c.getVoteCount() / totalVotes) * 100;
                System.out.printf("%-10s | %-20s | %-15s | %-8d | %6.2f%%%n",
                        c.getCandidateId(), c.getName(), c.getParty(), c.getVoteCount(), percentage);
            }
        }
        System.out.println("=================================================");
    }

    private static String readInput(String prompt) {
        System.out.print(prompt);
        return InputValidator.sanitizeInput(scanner.nextLine());
    }

    private static String readPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] passwordArray = console.readPassword(prompt);
            return new String(passwordArray);
        } else {
            // Fallback for IDE terminals or non-interactive environments
            System.out.print(prompt);
            return scanner.nextLine().trim();
        }
    }
}
