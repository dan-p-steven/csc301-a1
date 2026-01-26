package Shared;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class SecurityUtils {

    public static String SHA256Hash(String plaintext) {
        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Hash the password bytes
            byte[] hashBytes = digest.digest(plaintext.getBytes("UTF-8"));

            // Convert byte array to hex string for storage
            StringBuilder hexString = new StringBuilder();

            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b).toUpperCase();
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

    } catch (Exception e) {

        throw new RuntimeException("Error hashing password", e);

        }
    }
}


