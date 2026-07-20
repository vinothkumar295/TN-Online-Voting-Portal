# 🏛️ Tamil Nadu Online Voting Portal (தமிழ்நாடு தேர்தல் ஆணையம்)

A complete, production-quality, zero-dependency console & web application built using Core Java (JDK 8+). Tailored specifically for **Tamil Nadu Election Registration**, featuring EPIC Voter ID verification, District selection (சென்னை, கோவை, மதுரை, திருச்சி, etc.), mobile SMS OTP verification, official TN political candidates, salted SHA-256 security, and live results.

---

## 🌟 Feature Overview

### Tamil Nadu Voter Features
1. **TN Voter Registration with Mobile OTP**: Accepts EPIC Voter ID (e.g. `TNV1234567`), Full Name, Email, Mobile Number, District Selection, and Password.
2. **Tamil Nadu District Allocation**: Supports registration across 13 major TN districts (Chennai, Coimbatore, Madurai, Trichy, Salem, Tirunelveli, Erode, Vellore, Thanjavur, Kanchipuram, Cuddalore, Dindigul, Kanyakumari).
3. **Official TN Political Candidates**: Default seed candidates representing major Tamil Nadu parties (DMK, AIADMK, TVK, NTK, BJP).
2. **Salted Password Authentication**: Authenticates voters against SHA-256 salted hashes using constant-time comparison.
3. **Brute-Force Account Protection**: Automatically locks voter accounts after 3 failed login attempts.
4. **Anonymous Vote Casting**: Enforces exactly one vote per voter while ensuring complete ballot secrecy.
5. **Live Results View**: Displays candidate tallies, vote leaderboards, percentages, and total votes cast.

### Admin Features
1. **Administrator Control Panel**: Default seed admin (`admin` / `Admin@123`) created on first launch with support for changing admin credentials.
2. **Candidate Management**: Add new candidates or remove existing candidates on demand.
3. **Election Lifecycle Control**: Toggle election status between **OPEN** and **CLOSED**.
4. **Privacy-Preserving Voter Audit**: View all registered voters with masked Voter IDs (e.g. `VO****34`), displaying voting and lockout status without revealing candidate choice.
5. **Account Unlocking**: Unlock voter accounts locked by brute-force protection.
6. **Plaintext Audit Log**: Timestamped, readable log of system actions (registrations, logins, lockouts, admin actions) with zero exposure of voter-candidate links.

---

## 🔒 Security Measures Table

| Security Aspect | Implementation Detail | Mitigation / Risk Addressed |
| :--- | :--- | :--- |
| **Password Storage** | SHA-256 + 16-byte random Base64 salt (`SecurityUtil`) | Prevents plaintext leaks and rainbow table attacks. |
| **Password Policy** | Regex matching: Min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char | Eliminates weak/guessable user passwords. |
| **Hash Verification** | Constant-time `MessageDigest.isEqual` comparison | Protects against side-channel timing attack exploits. |
| **Brute-Force Protection**| Account locked after 3 failed login attempts (`VotingService`) | Prevents dictionary and credential stuffing attacks. |
| **Ballot Secrecy** | Independent `voter.hasVoted` flag & candidate counter; no mapping stored anywhere | Guarantees anonymous voter privacy. |
| **Input Sanitization** | Regex stripping of `< > ; ' " \`` in `InputValidator` | Prevents command, query, and XSS-style injection attacks. |
| **Thread Safety** | `AtomicInteger` for tallies & `synchronized` file storage methods | Prevents race conditions during concurrent voting. |
| **Storage Abstraction**| `IDataStore` interface decoupling storage from business logic | Enables seamless future migration to JDBC/MySQL DAO. |

---

## 📁 Project Structure Tree

```
c:/Users/vinot/Downloads/OOS/
├── README.md
├── src/
│   └── com/
│       └── voting/
│           ├── model/
│           │   ├── Voter.java
│           │   ├── Candidate.java
│           │   └── AdminAccount.java
│           ├── security/
│           │   └── SecurityUtil.java
│           ├── util/
│           │   └── InputValidator.java
│           ├── data/
│           │   ├── IDataStore.java
│           │   └── FileDataStore.java
│           ├── service/
│           │   └── VotingService.java
│           └── main/
│               └── Main.java
└── datastore/                     (Auto-created on first run)
    ├── admin.dat
    ├── candidates.dat
    ├── voters.dat
    ├── election.dat
    └── audit.log
```

---

## 🚀 Compilation & Running Instructions

### Prerequisites
- Java Development Kit (JDK 8 or higher)
- PowerShell, Command Prompt, or Terminal

### 1. Compile Source Files
From the project root directory (`c:\Users\vinot\Downloads\OOS`), execute:

```bash
javac -d bin src/com/voting/model/*.java src/com/voting/security/*.java src/com/voting/util/*.java src/com/voting/data/*.java src/com/voting/service/*.java src/com/voting/main/*.java
```

### 2. Run Application
Run the compiled main application:

```bash
java -cp bin com.voting.main.Main
```

---

## 📖 Sample System Walkthrough

### 1. Initial Launch & Seed Verification
When launched for the first time, `FileDataStore` automatically creates the `datastore/` directory and seeds:
- Default Admin Account: Username: `admin` | Password: `Admin@123`
- Default Candidates:
  - `CAND-1`: Alice Smith (Democratic Unity)
  - `CAND-2`: Bob Jones (Reform Party)
  - `CAND-3`: Charlie Brown (Independent)

### 2. Voter Registration
1. From Main Menu, select `1. Register New Voter`.
2. Input Voter ID: `VOT12345`
3. Input Full Name: `Jane Doe`
4. Input Email: `jane.doe@example.com`
5. Input Password: `SecurePass@2026`
6. System outputs: `SUCCESS: Voter registered successfully! Voter ID: VOT12345`

### 3. Voter Login & Vote Casting
1. Select `2. Voter Login` from Main Menu.
2. Login with Voter ID `VOT12345` and Password `SecurePass@2026`.
3. In Voter Dashboard, select `1. View Candidates & Cast Vote`.
4. Select Candidate #1 (`Alice Smith`).
5. Confirm selection (`YES`).
6. System updates voter `hasVoted = true` and increments candidate tally anonymously.
7. Attempting to vote a second time returns: `ERROR: You have already cast your vote!`

### 4. Viewing Live Results
Select `4. View Live Election Results` from Main Menu:
```
=================================================
            LIVE ELECTION RESULTS                
=================================================
Total Votes Cast: 1
Election Status : OPEN
-------------------------------------------------
ID         | Candidate Name       | Party           | Votes    | Percent 
------------------------------------------------------------------
CAND-1     | Alice Smith          | Democratic Unity| 1        | 100.00%
CAND-2     | Bob Jones            | Reform Party    | 0        |   0.00%
CAND-3     | Charlie Brown        | Independent     | 0        |   0.00%
=================================================
```

### 5. Admin Panel Operations
1. Select `3. Admin Login` from Main Menu.
2. Login with Admin ID `admin` and Password `Admin@123`.
3. Option `2`: Add Candidate `CAND-4` (Diana Prince - Green Forward).
4. Option `5`: View Registered Voters List (shows ID: `VO****45`, Email: `jane.doe@example.com`, Status: `VOTED`).
5. Option `8`: View Audit Log (shows timestamps for registration, login, vote cast, admin actions without exposing candidate choices).
