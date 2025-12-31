package com.urlshortener.service;

import org.springframework.stereotype.Component;

/**
 * Base62 encoder for generating short codes from numeric IDs.
 * 
 * Uses characters: 0-9, A-Z, a-z (62 characters total)
 * This allows for 62^7 = 3.5 trillion unique codes with 7 characters
 */
@Component
public class Base62Encoder {

    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;
    private static final int SHORT_CODE_LENGTH = 7;

    /**
     * Encodes a numeric ID to a Base62 string
     */
    public String encode(long num) {
        if (num == 0) {
            return padToLength(String.valueOf(BASE62_CHARS.charAt(0)));
        }

        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62_CHARS.charAt((int) (num % BASE)));
            num /= BASE;
        }

        String encoded = sb.reverse().toString();

        // Limit to SHORT_CODE_LENGTH characters (take last N chars for better
        // distribution)
        if (encoded.length() > SHORT_CODE_LENGTH) {
            encoded = encoded.substring(encoded.length() - SHORT_CODE_LENGTH);
        }

        return padToLength(encoded);
    }

    /**
     * Decodes a Base62 string back to numeric ID
     */
    public long decode(String str) {
        long num = 0;
        for (int i = 0; i < str.length(); i++) {
            num = num * BASE + BASE62_CHARS.indexOf(str.charAt(i));
        }
        return num;
    }

    /**
     * Pads the encoded string to minimum length
     */
    private String padToLength(String str) {
        if (str.length() >= SHORT_CODE_LENGTH) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SHORT_CODE_LENGTH - str.length(); i++) {
            sb.append(BASE62_CHARS.charAt(0));
        }
        sb.append(str);
        return sb.toString();
    }

    /**
     * Validates if a string is a valid Base62 encoded string
     */
    public boolean isValidBase62(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (BASE62_CHARS.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }
}
