package com.voting.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.voting.data.FileDataStore;
import com.voting.data.IDataStore;
import com.voting.model.Candidate;
import com.voting.model.Voter;
import com.voting.security.SecurityUtil;
import com.voting.service.VotingService;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Built-in JDK HTTP Web Server serving a modern single-page web app and REST API.
 * Runs locally on http://localhost:8080 with zero external framework dependencies.
 */
public class WebServer {

    private final VotingService votingService;
    private final int port;
    private HttpServer server;

    public WebServer(VotingService votingService, int port) {
        this.votingService = votingService;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Serve Static HTML/CSS/JS Single Page Web App
        server.createContext("/", new StaticHandler());

        // REST API endpoints
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/register/send-otp", new SendOtpHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/vote", new VoteHandler());
        server.createContext("/api/results", new ResultsHandler());
        server.createContext("/api/admin/login", new AdminLoginHandler());
        server.createContext("/api/admin/voters", new AdminVotersHandler());
        server.createContext("/api/admin/audit", new AdminAuditHandler());
        server.createContext("/api/admin/candidate/add", new AdminAddCandidateHandler());
        server.createContext("/api/admin/candidate/remove", new AdminRemoveCandidateHandler());
        server.createContext("/api/admin/election/toggle", new AdminToggleElectionHandler());
        server.createContext("/api/admin/unlock", new AdminUnlockHandler());

        server.setExecutor(null); // default executor
        server.start();
        System.out.println("=================================================");
        System.out.println(" [WEB SERVER RUNNING ON LOCALHOST]");
        System.out.println(" Open Web Interface: http://localhost:" + port);
        System.out.println("=================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Helper to read request body JSON / form payload
    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // Simple JSON key-value parser for basic payloads
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("^\"|\"$", "");
                String value = kv[1].trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
        }
        return map;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void sendHtmlResponse(HttpExchange exchange, String htmlContent) throws IOException {
        byte[] responseBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    // Static Web Page Handler
    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }
            String html = getWebUIHtml();
            sendHtmlResponse(exchange, html);
        }
    }

    // REST Handlers
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean open = votingService.isElectionOpen();
            int totalVotes = votingService.getTotalVotesCast();
            sendJsonResponse(exchange, 200, String.format("{\"open\":%b, \"totalVotes\":%d}", open, totalVotes));
        }
    }

    private class SendOtpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }
            String otp = SecurityUtil.generateOTP();
            sendJsonResponse(exchange, 200, String.format("{\"success\":true, \"otp\":\"%s\", \"message\":\"OTP generated successfully\"}", otp));
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }
            Map<String, String> p = parseJson(readBody(exchange));
            String result = votingService.registerVoter(
                    p.get("voterId"), p.get("fullName"), p.get("email"),
                    p.get("phone"), p.get("district"), p.get("password"),
                    p.get("userOtp"), p.get("expectedOtp")
            );
            boolean success = result.startsWith("SUCCESS");
            sendJsonResponse(exchange, 200, String.format("{\"success\":%b, \"message\":\"%s\"}", success, escapeJson(result)));
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }
            Map<String, String> p = parseJson(readBody(exchange));
            Voter v = votingService.loginVoter(p.get("voterId"), p.get("password"));
            if (v != null) {
                String maskedId = SecurityUtil.maskVoterId(v.getVoterId());
                sendJsonResponse(exchange, 200, String.format(
                        "{\"success\":true, \"voterId\":\"%s\", \"maskedId\":\"%s\", \"fullName\":\"%s\", \"hasVoted\":%b}",
                        v.getVoterId(), maskedId, escapeJson(v.getFullName()), v.isHasVoted()
                ));
            } else {
                sendJsonResponse(exchange, 200, "{\"success\":false, \"message\":\"Login failed. Invalid credentials or account locked.\"}");
            }
        }
    }

    private class VoteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }
            Map<String, String> p = parseJson(readBody(exchange));
            String result = votingService.castVote(p.get("voterId"), p.get("candidateId"));
            boolean success = result.startsWith("SUCCESS");
            sendJsonResponse(exchange, 200, String.format("{\"success\":%b, \"message\":\"%s\"}", success, escapeJson(result)));
        }
    }

    private class ResultsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Candidate> candidates = votingService.getSortedCandidates();
            int totalVotes = votingService.getTotalVotesCast();
            boolean open = votingService.isElectionOpen();

            StringBuilder sb = new StringBuilder();
            sb.append("{\"open\":").append(open).append(",\"totalVotes\":").append(totalVotes).append(",\"candidates\":[");
            for (int i = 0; i < candidates.size(); i++) {
                Candidate c = candidates.get(i);
                double pct = totalVotes == 0 ? 0 : ((double) c.getVoteCount() / totalVotes) * 100;
                sb.append(String.format("{\"id\":\"%s\",\"name\":\"%s\",\"party\":\"%s\",\"votes\":%d,\"pct\":%.2f}",
                        c.getCandidateId(), escapeJson(c.getName()), escapeJson(c.getParty()), c.getVoteCount(), pct));
                if (i < candidates.size() - 1) sb.append(",");
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class AdminLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 200, "{}");
                return;
            }
            Map<String, String> p = parseJson(readBody(exchange));
            boolean success = votingService.loginAdmin(p.get("adminId"), p.get("password"));
            sendJsonResponse(exchange, 200, String.format("{\"success\":%b}", success));
        }
    }

    private class AdminVotersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<String> report = votingService.getMaskedVotersReport();
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < report.size(); i++) {
                sb.append("\"").append(escapeJson(report.get(i))).append("\"");
                if (i < report.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class AdminAuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<String> logs = votingService.getAuditLogs();
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < logs.size(); i++) {
                sb.append("\"").append(escapeJson(logs.get(i))).append("\"");
                if (i < logs.size() - 1) sb.append(",");
            }
            sb.append("]");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class AdminAddCandidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> p = parseJson(readBody(exchange));
            String res = votingService.addCandidate(p.get("id"), p.get("name"), p.get("party"));
            sendJsonResponse(exchange, 200, String.format("{\"success\":%b, \"message\":\"%s\"}", res.startsWith("SUCCESS"), escapeJson(res)));
        }
    }

    private class AdminRemoveCandidateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> p = parseJson(readBody(exchange));
            String res = votingService.removeCandidate(p.get("id"));
            sendJsonResponse(exchange, 200, String.format("{\"success\":%b, \"message\":\"%s\"}", res.startsWith("SUCCESS"), escapeJson(res)));
        }
    }

    private class AdminToggleElectionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean current = votingService.isElectionOpen();
            String res = votingService.setElectionStatus(!current);
            sendJsonResponse(exchange, 200, String.format("{\"success\":true, \"open\":%b, \"message\":\"%s\"}", !current, escapeJson(res)));
        }
    }

    private class AdminUnlockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> p = parseJson(readBody(exchange));
            String res = votingService.unlockVoterAccount(p.get("voterId"));
            sendJsonResponse(exchange, 200, String.format("{\"success\":%b, \"message\":\"%s\"}", res.startsWith("SUCCESS"), escapeJson(res)));
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // HTML / CSS / JS Single Page Application Definition
    private String getWebUIHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>தமிழ்நாடு தேர்தல் ஆணையம் - Online Voting Portal</title>\n" +
                "    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --bg-gradient: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #0f172a 100%);\n" +
                "            --glass-bg: rgba(30, 41, 59, 0.75);\n" +
                "            --glass-border: rgba(255, 255, 255, 0.12);\n" +
                "            --primary: #6366f1;\n" +
                "            --accent: #10b981;\n" +
                "            --gold: #fbbf24;\n" +
                "            --text: #f8fafc;\n" +
                "            --text-muted: #94a3b8;\n" +
                "        }\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Inter', sans-serif; }\n" +
                "        body { background: var(--bg-gradient); color: var(--text); min-height: 100vh; display: flex; flex-direction: column; }\n" +
                "        header {\n" +
                "            background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(12px); border-bottom: 1px solid var(--glass-border);\n" +
                "            padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center;\n" +
                "        }\n" +
                "        .logo { font-size: 1.3rem; font-weight: 700; background: linear-gradient(90deg, #fbbf24, #34d399); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }\n" +
                "        .status-badge { padding: 0.4rem 0.8rem; border-radius: 20px; font-size: 0.85rem; font-weight: 600; text-transform: uppercase; }\n" +
                "        .badge-open { background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid #10b981; }\n" +
                "        .container { flex: 1; max-width: 1000px; width: 100%; margin: 2rem auto; padding: 0 1rem; }\n" +
                "        .glass-card {\n" +
                "            background: var(--glass-bg); backdrop-filter: blur(16px); border: 1px solid var(--glass-border);\n" +
                "            border-radius: 16px; padding: 2rem; box-shadow: 0 20px 40px rgba(0,0,0,0.3); margin-bottom: 2rem;\n" +
                "        }\n" +
                "        .nav-tabs { display: flex; gap: 1rem; margin-bottom: 2rem; flex-wrap: wrap; }\n" +
                "        .tab-btn {\n" +
                "            background: transparent; border: 1px solid var(--glass-border); color: var(--text-muted);\n" +
                "            padding: 0.75rem 1.5rem; border-radius: 10px; cursor: pointer; font-weight: 600; transition: all 0.3s;\n" +
                "        }\n" +
                "        .tab-btn.active, .tab-btn:hover { background: var(--primary); color: #fff; border-color: var(--primary); }\n" +
                "        .form-group { margin-bottom: 1.25rem; }\n" +
                "        label { display: block; font-size: 0.875rem; color: var(--text-muted); margin-bottom: 0.5rem; }\n" +
                "        input, select { width: 100%; padding: 0.75rem 1rem; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--glass-border); border-radius: 8px; color: #fff; font-size: 1rem; outline: none; }\n" +
                "        select option { background: #1e293b; color: #fff; }\n" +
                "        input:focus, select:focus { border-color: var(--primary); box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2); }\n" +
                "        .btn {\n" +
                "            width: 100%; padding: 0.85rem; background: linear-gradient(135deg, #6366f1, #4f46e5); color: white;\n" +
                "            border: none; border-radius: 8px; font-weight: 600; font-size: 1rem; cursor: pointer; transition: transform 0.2s;\n" +
                "        }\n" +
                "        .btn:hover { transform: translateY(-2px); box-shadow: 0 10px 20px rgba(99, 102, 241, 0.3); }\n" +
                "        .alert { padding: 1rem; border-radius: 8px; margin-bottom: 1rem; font-size: 0.9rem; display: none; }\n" +
                "        .alert-success { background: rgba(16, 185, 129, 0.2); border: 1px solid #10b981; color: #34d399; }\n" +
                "        .alert-error { background: rgba(239, 68, 68, 0.2); border: 1px solid #ef4444; color: #f87171; }\n" +
                "        .candidate-card {\n" +
                "            background: rgba(15, 23, 42, 0.5); border: 1px solid var(--glass-border); border-radius: 12px;\n" +
                "            padding: 1.25rem; margin-bottom: 1rem; display: flex; justify-content: space-between; align-items: center;\n" +
                "        }\n" +
                "        .progress-bar-bg { background: rgba(255,255,255,0.1); height: 12px; border-radius: 6px; overflow: hidden; margin-top: 0.5rem; }\n" +
                "        .progress-bar-fill { background: linear-gradient(90deg, #fbbf24, #10b981); height: 100%; transition: width 0.5s; }\n" +
                "        .modal { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.7); backdrop-filter: blur(8px); justify-content: center; align-items: center; z-index: 100; }\n" +
                "        .modal-content { background: #1e293b; padding: 2rem; border-radius: 16px; max-width: 400px; width: 90%; text-align: center; border: 1px solid var(--glass-border); }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <header>\n" +
                "        <div class=\"logo\">🏛️ தமிழ்நாடு தேர்தல் ஆணையம் | TN Online Voting Portal</div>\n" +
                "        <div id=\"headerStatus\" class=\"status-badge badge-open\">Election: OPEN</div>\n" +
                "    </header>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"nav-tabs\">\n" +
                "            <button class=\"tab-btn active\" onclick=\"switchTab('registerTab')\">TN Voter Registration</button>\n" +
                "            <button class=\"tab-btn\" onclick=\"switchTab('loginTab')\">Voter Login</button>\n" +
                "            <button class=\"tab-btn\" onclick=\"switchTab('resultsTab')\">Live Results</button>\n" +
                "            <button class=\"tab-btn\" onclick=\"switchTab('adminTab')\">Admin Portal</button>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Alert Box -->\n" +
                "        <div id=\"globalAlert\" class=\"alert\"></div>\n" +
                "\n" +
                "        <!-- REGISTRATION TAB -->\n" +
                "        <div id=\"registerTab\" class=\"glass-card\">\n" +
                "            <h2>தமிழ்நாடு வாக்காளர் பதிவு (Voter Registration with OTP)</h2>\n" +
                "            <p style=\"color: var(--text-muted); margin-bottom: 1.5rem;\">Enter your EPIC Voter ID and Tamil Nadu district details to register.</p>\n" +
                "            <form id=\"regForm\" onsubmit=\"handleSendOtp(event)\">\n" +
                "                <div class=\"form-group\"><label>Voter ID / EPIC No. (e.g. TNV1234567 or VOT12345)</label><input type=\"text\" id=\"regVoterId\" required></div>\n" +
                "                <div class=\"form-group\"><label>Full Name</label><input type=\"text\" id=\"regName\" required></div>\n" +
                "                <div class=\"form-group\"><label>Email Address</label><input type=\"email\" id=\"regEmail\" required></div>\n" +
                "                <div class=\"form-group\"><label>Mobile Phone Number</label><input type=\"tel\" id=\"regPhone\" required></div>\n" +
                "                <div class=\"form-group\"><label>மாவட்டம் தேர்ந்தெடுக்கவும் (Select Tamil Nadu District)</label>\n" +
                "                    <select id=\"regDistrict\" required>\n" +
                "                        <option value=\"Ariyalur\">Ariyalur (அரியலூர்)</option>\n" +
                "                        <option value=\"Chengalpattu\">Chengalpattu (செங்கல்பட்டு)</option>\n" +
                "                        <option value=\"Chennai\" selected>Chennai (சென்னை)</option>\n" +
                "                        <option value=\"Coimbatore\">Coimbatore (கோயம்புத்தூர்)</option>\n" +
                "                        <option value=\"Cuddalore\">Cuddalore (கடலூர்)</option>\n" +
                "                        <option value=\"Dharmapuri\">Dharmapuri (தர்மபுரி)</option>\n" +
                "                        <option value=\"Dindigul\">Dindigul (திண்டுக்கல்)</option>\n" +
                "                        <option value=\"Erode\">Erode (ஈரோடு)</option>\n" +
                "                        <option value=\"Kallakurichi\">Kallakurichi (கள்ளக்குறிச்சி)</option>\n" +
                "                        <option value=\"Kanchipuram\">Kanchipuram (காஞ்சிபுரம்)</option>\n" +
                "                        <option value=\"Kanyakumari\">Kanyakumari (கன்னியாகுமரி)</option>\n" +
                "                        <option value=\"Karur\">Karur (கரூர்)</option>\n" +
                "                        <option value=\"Krishnagiri\">Krishnagiri (கிருஷ்ணகிரி)</option>\n" +
                "                        <option value=\"Madurai\">Madurai (மதுரை)</option>\n" +
                "                        <option value=\"Mayiladuthurai\">Mayiladuthurai (மயிலாடுதுறை)</option>\n" +
                "                        <option value=\"Nagapattinam\">Nagapattinam (நாகப்பட்டினம்)</option>\n" +
                "                        <option value=\"Namakkal\">Namakkal (நாமக்கல்)</option>\n" +
                "                        <option value=\"Nilgiris\">Nilgiris (நீலகிரி)</option>\n" +
                "                        <option value=\"Perambalur\">Perambalur (பெரம்பலூர்)</option>\n" +
                "                        <option value=\"Pudukkottai\">Pudukkottai (புதுக்கோட்டை)</option>\n" +
                "                        <option value=\"Ramanathapuram\">Ramanathapuram (இராமநாதபுரம்)</option>\n" +
                "                        <option value=\"Ranipet\">Ranipet (ராணிப்பேட்டை)</option>\n" +
                "                        <option value=\"Salem\">Salem (சேலம்)</option>\n" +
                "                        <option value=\"Sivaganga\">Sivaganga (சிவகங்கை)</option>\n" +
                "                        <option value=\"Tenkasi\">Tenkasi (தென்காசி)</option>\n" +
                "                        <option value=\"Thanjavur\">Thanjavur (தஞ்சாவூர்)</option>\n" +
                "                        <option value=\"Theni\">Theni (தேனி)</option>\n" +
                "                        <option value=\"Thoothukudi\">Thoothukudi (தூத்துக்குடி)</option>\n" +
                "                        <option value=\"Tiruchirappalli\">Tiruchirappalli (திருச்சிராப்பள்ளி)</option>\n" +
                "                        <option value=\"Tirunelveli\">Tirunelveli (திருநெல்வேலி)</option>\n" +
                "                        <option value=\"Tirupathur\">Tirupathur (திருப்பத்தூர்)</option>\n" +
                "                        <option value=\"Tiruppur\">Tiruppur (திருப்பூர்)</option>\n" +
                "                        <option value=\"Tiruvallur\">Tiruvallur (திருவள்ளூர்)</option>\n" +
                "                        <option value=\"Tiruvannamalai\">Tiruvannamalai (திருவண்ணாமலை)</option>\n" +
                "                        <option value=\"Tiruvarur\">Tiruvarur (திருவாரூர்)</option>\n" +
                "                        <option value=\"Vellore\">Vellore (வேலூர்)</option>\n" +
                "                        <option value=\"Viluppuram\">Viluppuram (விழுப்புரம்)</option>\n" +
                "                        <option value=\"Virudhunagar\">Virudhunagar (விருதுநகர்)</option>\n" +
                "                    </select>\n" +
                "                </div>\n" +
                "                <div class=\"form-group\"><label>Password (Min 8 chars, 1 Upper, 1 Lower, 1 Digit, 1 Special)</label><input type=\"password\" id=\"regPassword\" required></div>\n" +
                "                <button type=\"submit\" class=\"btn\">Send Mobile OTP & Verify Registration</button>\n" +
                "            </form>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- LOGIN TAB -->\n" +
                "        <div id=\"loginTab\" class=\"glass-card\" style=\"display:none;\">\n" +
                "            <h2>வாக்காளர் உள்நுழைவு (Voter Authentication)</h2>\n" +
                "            <form onsubmit=\"handleLogin(event)\">\n" +
                "                <div class=\"form-group\"><label>Voter ID (EPIC)</label><input type=\"text\" id=\"loginVoterId\" required></div>\n" +
                "                <div class=\"form-group\"><label>Password</label><input type=\"password\" id=\"loginPassword\" required></div>\n" +
                "                <button type=\"submit\" class=\"btn\">Login to Ballot Dashboard</button>\n" +
                "            </form>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- VOTER DASHBOARD -->\n" +
                "        <div id=\"voterDashboardTab\" class=\"glass-card\" style=\"display:none;\">\n" +
                "            <h2>தமிழ்நாடு சட்டசபை தேர்தல் வாக்குச்சீட்டு (Official Ballot)</h2>\n" +
                "            <p id=\"voterWelcomeMsg\" style=\"color: var(--accent); margin-bottom: 1.5rem;\"></p>\n" +
                "            <div id=\"ballotCandidatesList\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- RESULTS TAB -->\n" +
                "        <div id=\"resultsTab\" class=\"glass-card\" style=\"display:none;\">\n" +
                "            <h2>தேர்தல் முடிவுகள் (Tamil Nadu Election Live Results)</h2>\n" +
                "            <p id=\"resultsTotalVotes\" style=\"color: var(--text-muted); margin-bottom: 1.5rem;\"></p>\n" +
                "            <div id=\"resultsList\"></div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- ADMIN TAB -->\n" +
                "        <div id=\"adminTab\" class=\"glass-card\" style=\"display:none;\">\n" +
                "            <h2>தேர்தல் அதிகாரி பிரிவு (Administrator Portal)</h2>\n" +
                "            <div id=\"adminLoginForm\">\n" +
                "                <form onsubmit=\"handleAdminLogin(event)\">\n" +
                "                    <div class=\"form-group\"><label>Admin ID</label><input type=\"text\" id=\"adminId\" value=\"admin\" required></div>\n" +
                "                    <div class=\"form-group\"><label>Admin Password</label><input type=\"password\" id=\"adminPass\" value=\"Admin@123\" required></div>\n" +
                "                    <button type=\"submit\" class=\"btn\">Login as Administrator</button>\n" +
                "                </form>\n" +
                "            </div>\n" +
                "            <div id=\"adminDashboard\" style=\"display:none;\">\n" +
                "                <div style=\"display:flex; gap:1rem; margin-bottom:1.5rem;\">\n" +
                "                    <button class=\"btn\" style=\"background:#10b981;\" onclick=\"toggleElection()\">Toggle Election Open/Closed</button>\n" +
                "                    <button class=\"btn\" style=\"background:#ef4444;\" onclick=\"logoutAdmin()\">Logout Admin</button>\n" +
                "                </div>\n" +
                "                <h3 style=\"margin-top:1.5rem;\">System Audit Log</h3>\n" +
                "                <pre id=\"adminAuditLog\" style=\"background:#0f172a; padding:1rem; border-radius:8px; max-height:250px; overflow-y:auto; font-size:0.85rem; color:#34d399;\"></pre>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- OTP Modal -->\n" +
                "    <div id=\"otpModal\" class=\"modal\">\n" +
                "        <div class=\"modal-content\">\n" +
                "            <h3>📲 Mobile OTP Verification</h3>\n" +
                "            <p style=\"color:var(--text-muted); font-size:0.9rem; margin:1rem 0;\">Simulated TN SMS Gateway OTP sent to mobile:</p>\n" +
                "            <h2 id=\"simulatedOtp\" style=\"color:#34d399; letter-spacing:4px;\"></h2>\n" +
                "            <div class=\"form-group\" style=\"margin-top:1rem;\">\n" +
                "                <input type=\"text\" id=\"userOtpInput\" placeholder=\"Enter 6-digit OTP\">\n" +
                "            </div>\n" +
                "            <button class=\"btn\" onclick=\"submitRegistrationWithOtp()\">Verify OTP & Register</button>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        let currentOtp = '';\n" +
                "        let activeVoter = null;\n" +
                "\n" +
                "        function showAlert(msg, isSuccess) {\n" +
                "            const box = document.getElementById('globalAlert');\n" +
                "            box.className = 'alert ' + (isSuccess ? 'alert-success' : 'alert-error');\n" +
                "            box.innerText = msg;\n" +
                "            box.style.display = 'block';\n" +
                "            setTimeout(() => box.style.display = 'none', 5000);\n" +
                "        }\n" +
                "\n" +
                "        function switchTab(tabId) {\n" +
                "            ['registerTab', 'loginTab', 'voterDashboardTab', 'resultsTab', 'adminTab'].forEach(t => {\n" +
                "                document.getElementById(t).style.display = (t === tabId) ? 'block' : 'none';\n" +
                "            });\n" +
                "            if (tabId === 'resultsTab') loadResults();\n" +
                "        }\n" +
                "\n" +
                "        async function handleSendOtp(e) {\n" +
                "            e.preventDefault();\n" +
                "            const res = await fetch('/api/register/send-otp', {method: 'POST'});\n" +
                "            const data = await res.json();\n" +
                "            currentOtp = data.otp;\n" +
                "            document.getElementById('simulatedOtp').innerText = currentOtp;\n" +
                "            document.getElementById('otpModal').style.display = 'flex';\n" +
                "        }\n" +
                "\n" +
                "        async function submitRegistrationWithOtp() {\n" +
                "            const payload = {\n" +
                "                voterId: document.getElementById('regVoterId').value,\n" +
                "                fullName: document.getElementById('regName').value,\n" +
                "                email: document.getElementById('regEmail').value,\n" +
                "                phone: document.getElementById('regPhone').value,\n" +
                "                district: document.getElementById('regDistrict').value,\n" +
                "                password: document.getElementById('regPassword').value,\n" +
                "                userOtp: document.getElementById('userOtpInput').value,\n" +
                "                expectedOtp: currentOtp\n" +
                "            };\n" +
                "            document.getElementById('otpModal').style.display = 'none';\n" +
                "            const res = await fetch('/api/register', {method: 'POST', body: JSON.stringify(payload)});\n" +
                "            const data = await res.json();\n" +
                "            showAlert(data.message, data.success);\n" +
                "            if (data.success) {\n" +
                "                document.getElementById('regForm').reset();\n" +
                "                switchTab('loginTab');\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        async function handleLogin(e) {\n" +
                "            e.preventDefault();\n" +
                "            const payload = {\n" +
                "                voterId: document.getElementById('loginVoterId').value,\n" +
                "                password: document.getElementById('loginPassword').value\n" +
                "            };\n" +
                "            const res = await fetch('/api/login', {method: 'POST', body: JSON.stringify(payload)});\n" +
                "            const data = await res.json();\n" +
                "            if (data.success) {\n" +
                "                activeVoter = data;\n" +
                "                showAlert('Welcome ' + data.fullName, true);\n" +
                "                loadBallot();\n" +
                "                switchTab('voterDashboardTab');\n" +
                "            } else {\n" +
                "                showAlert(data.message, false);\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        async function loadBallot() {\n" +
                "            document.getElementById('voterWelcomeMsg').innerText = `Welcome, ${activeVoter.fullName} (${activeVoter.maskedId}) | Has Voted: ${activeVoter.hasVoted ? 'YES' : 'NO'}`;\n" +
                "            const res = await fetch('/api/results');\n" +
                "            const data = await res.json();\n" +
                "            const container = document.getElementById('ballotCandidatesList');\n" +
                "            container.innerHTML = '';\n" +
                "            if (activeVoter.hasVoted) {\n" +
                "                container.innerHTML = '<p style=\"color:#34d399; font-size:1.1rem;\">✅ Your vote has been recorded securely in the Tamil Nadu Election system. Thank you!</p>';\n" +
                "                return;\n" +
                "            }\n" +
                "            data.candidates.forEach(c => {\n" +
                "                container.innerHTML += `\n" +
                "                    <div class=\"candidate-card\">\n" +
                "                        <div>\n" +
                "                            <h3 style=\"color:#fbbf24;\">${c.name}</h3>\n" +
                "                            <span style=\"color:var(--text-muted); font-size:0.9rem;\">Party: ${c.party}</span>\n" +
                "                        </div>\n" +
                "                        <button class=\"btn\" style=\"width:auto; padding:0.6rem 1.8rem; background:linear-gradient(135deg,#10b981,#059669);\" onclick=\"castVote('${c.id}')\">Cast Vote</button>\n" +
                "                    </div>\n" +
                "                `;\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        async function castVote(candidateId) {\n" +
                "            if (!confirm('Confirm vote submission for Tamil Nadu Election?')) return;\n" +
                "            const res = await fetch('/api/vote', {method: 'POST', body: JSON.stringify({voterId: activeVoter.voterId, candidateId})});\n" +
                "            const data = await res.json();\n" +
                "            showAlert(data.message, data.success);\n" +
                "            if (data.success) {\n" +
                "                activeVoter.hasVoted = true;\n" +
                "                loadBallot();\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        async function loadResults() {\n" +
                "            const res = await fetch('/api/results');\n" +
                "            const data = await res.json();\n" +
                "            document.getElementById('resultsTotalVotes').innerText = `Total Votes Cast: ${data.totalVotes} | Election Status: ${data.open ? 'OPEN' : 'CLOSED'}`;\n" +
                "            const container = document.getElementById('resultsList');\n" +
                "            container.innerHTML = '';\n" +
                "            data.candidates.forEach(c => {\n" +
                "                container.innerHTML += `\n" +
                "                    <div class=\"candidate-card\" style=\"flex-direction:column; align-items:stretch;\">\n" +
                "                        <div style=\"display:flex; justify-content:space-between;\">\n" +
                "                            <strong style=\"font-size:1.05rem; color:#fbbf24;\">${c.name} (${c.party})</strong>\n" +
                "                            <span style=\"font-weight:600;\">${c.votes} votes (${c.pct}%)</span>\n" +
                "                        </div>\n" +
                "                        <div class=\"progress-bar-bg\">\n" +
                "                            <div class=\"progress-bar-fill\" style=\"width: ${c.pct}%;\"></div>\n" +
                "                        </div>\n" +
                "                    </div>\n" +
                "                `;\n" +
                "            });\n" +
                "        }\n" +
                "\n" +
                "        async function handleAdminLogin(e) {\n" +
                "            e.preventDefault();\n" +
                "            const res = await fetch('/api/admin/login', {method: 'POST', body: JSON.stringify({\n" +
                "                adminId: document.getElementById('adminId').value,\n" +
                "                password: document.getElementById('adminPass').value\n" +
                "            })});\n" +
                "            const data = await res.json();\n" +
                "            if (data.success) {\n" +
                "                document.getElementById('adminLoginForm').style.display = 'none';\n" +
                "                document.getElementById('adminDashboard').style.display = 'block';\n" +
                "                loadAdminAudit();\n" +
                "            } else showAlert('Invalid admin login', false);\n" +
                "        }\n" +
                "\n" +
                "        async function loadAdminAudit() {\n" +
                "            const res = await fetch('/api/admin/audit');\n" +
                "            const logs = await res.json();\n" +
                "            document.getElementById('adminAuditLog').innerText = logs.join('\\n');\n" +
                "        }\n" +
                "\n" +
                "        async function toggleElection() {\n" +
                "            const res = await fetch('/api/admin/election/toggle', {method:'POST'});\n" +
                "            const data = await res.json();\n" +
                "            showAlert(data.message, true);\n" +
                "            loadAdminAudit();\n" +
                "        }\n" +
                "\n" +
                "        function logoutAdmin() {\n" +
                "            document.getElementById('adminLoginForm').style.display = 'block';\n" +
                "            document.getElementById('adminDashboard').style.display = 'none';\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
