package com.voting.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Security utility class providing SHA-256 salted password hashing,
 * constant-time verification, password strength checks, OTP generation, and voter ID masking.
 */
public class SecurityUtil {

    private static final int SALT_BYTE_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Password policy: min 8 chars, at least one uppercase, one lowercase, one digit, one special character
    private static final String PASSWORD_PATTERN =
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$";
    private static final Pattern PASSWORD_REGEX = Pattern.compile(PASSWORD_PATTERN);

    /**
     * Generates a cryptographically secure random 16-byte salt (Base64 encoded).
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with a given salt using SHA-256.
     */
    public static String hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt cannot be null.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            byte[] hashedBytes = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies a raw password against stored SHA-256 hash using constant-time comparison
     * to prevent side-channel timing attacks.
     */
    public static boolean verifyPassword(String rawPassword, String storedHash, String salt) {
        if (rawPassword == null || storedHash == null || salt == null) {
            return false;
        }
        String computedHash = hashPassword(rawPassword, salt);
        byte[] a = Base64.getDecoder().decode(computedHash);
        byte[] b = Base64.getDecoder().decode(storedHash);

        return MessageDigest.isEqual(a, b);
    }

    /**
     * Validates password strength:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character
     */
    public static boolean validatePasswordStrength(String password) {
        if (password == null) {
            return false;
        }
        return PASSWORD_REGEX.matcher(password).matches();
    }

    /**
     * Masks a voter ID for privacy (e.g., "VOT12345" -> "VO****45").
     */
    public static String maskVoterId(String voterId) {
        if (voterId == null || voterId.isEmpty()) {
            return "****";
        }
        int length = voterId.length();
        if (length <= 4) {
            return voterId.charAt(0) + "**" + voterId.charAt(length - 1);
        }
        String prefix = voterId.substring(0, 2);
        String suffix = voterId.substring(length - 2);
        int maskedLength = length - 4;
        StringBuilder masked = new StringBuilder(prefix);
        for (int i = 0; i < maskedLength; i++) {
            masked.append('*');
        }
        masked.append(suffix);
        return masked.toString();
    }

    /**
     * Generates a 6-digit cryptographically secure One-Time Password (OTP).
     */
    public static String generateOTP() {
        int otp = 100000 + SECURE_RANDOM.nextInt(900000);
        return String.valueOf(otp);
    }
}
