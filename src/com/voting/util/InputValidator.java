package com.voting.util;

import java.util.regex.Pattern;

/**
 * Utility class for validating user input formats and sanitizing strings against injection attacks.
 */
public class InputValidator {

    // 3-6 letters + 4-10 digits (e.g. VOT12345, ELECT987654)
    private static final String VOTER_ID_REGEX = "^[A-Za-z]{3,6}\\d{4,10}$";
    private static final Pattern VOTER_ID_PATTERN = Pattern.compile(VOTER_ID_REGEX);

    // Full name: 2-50 characters, letters, dots and spaces (e.g. K. Vinothkumar, M. K. Stalin)
    private static final String NAME_REGEX = "^[a-zA-Z.\\s]{2,50}$";
    private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

    // Standard email regex
    private static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    // Phone number regex: 10 to 15 digits, optional + prefix
    private static final String PHONE_REGEX = "^\\+?[0-9]{10,15}$";
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /**
     * Validates Voter ID format (3-6 letters followed by 4-10 digits).
     */
    public static boolean isValidVoterId(String voterId) {
        if (voterId == null) return false;
        return VOTER_ID_PATTERN.matcher(voterId.trim()).matches();
    }

    /**
     * Validates full name (letters and spaces, 2 to 50 characters).
     */
    public static boolean isValidName(String name) {
        if (name == null) return false;
        return NAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * Validates email address format.
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validates phone number format (10-15 digits).
     */
    public static boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * Sanitizes input string against injection characters (<, >, ;, ', ", `).
     * Prevents XSS/command/query injection vectors in free text inputs.
     */
    public static String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("[<>;'\"`]", "").trim();
    }
}
