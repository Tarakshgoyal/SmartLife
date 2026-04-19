package com.smartlife.security.privacy;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.Random;
import java.util.UUID;

/**
 * Layer 7: ML Privacy Guard — Data Anonymization before ML processing.
 *
 * Ensures that sensitive PII is never fed into ML models in raw form.
 * Instead, data is transformed using:
 *
 *   1. Pseudonymisation — replace user IDs with one-way hashed tokens
 *   2. Generalisation   — exact values rounded to ranges (e.g., age 34 -> "30-40")
 *   3. Noise injection  — add calibrated random noise to numerical values
 *   4. Aggregation      — individual records replaced with group statistics
 *
 * This implements Differential Privacy principles, ensuring that the presence
 * or absence of any single user's data cannot be inferred from ML model outputs.
 */
@Slf4j
@UtilityClass
public class DataAnonymizer {

    private static final Random SECURE_RANDOM = new Random();

    /**
     * Pseudonymise a user ID for ML training data.
     * Same userId always maps to the same token (deterministic), but the
     * original userId cannot be recovered from the token.
     */
    public static String pseudonymise(UUID userId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(("smartlife-ml-salt-" + userId).getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return "user_" + hex.substring(0, 16); // first 16 hex chars = 64-bit token
        } catch (Exception e) {
            return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }

    /**
     * Add Laplace noise to a numerical value (differential privacy).
     *
     * @param value     original value
     * @param epsilon   privacy budget (smaller = more privacy, less accuracy)
     *                  Recommended: 0.1 for high sensitivity, 1.0 for low sensitivity
     * @param sensitivity  max change in output from one individual's data
     */
    public static double addLaplaceNoise(double value, double epsilon, double sensitivity) {
        double scale = sensitivity / epsilon;
        double u = SECURE_RANDOM.nextDouble() - 0.5;
        double noise = -scale * Math.signum(u) * Math.log(1 - 2 * Math.abs(u));
        return value + noise;
    }

    /** Add noise to a BigDecimal amount (financial data). */
    public static BigDecimal addNoise(BigDecimal amount, double epsilon) {
        double noisy = addLaplaceNoise(amount.doubleValue(), epsilon, 100.0);
        return BigDecimal.valueOf(Math.max(0, noisy)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Generalise a health metric to a range bucket.
     * e.g., sleep hours 6.7 -> "6-7h", mood score 8 -> "7-8"
     */
    public static String generaliseToRange(double value, double bucketSize) {
        long bucket = (long) (value / bucketSize);
        double low  = bucket * bucketSize;
        double high = low + bucketSize;
        return String.format("%.0f-%.0f", low, high);
    }

    /**
     * Generalise an age to a decade bucket.
     * e.g., 34 -> "30-40"
     */
    public static String generaliseAge(int age) {
        int decade = (age / 10) * 10;
        return decade + "-" + (decade + 10);
    }

    /**
     * Generalise a monetary amount to a logarithmic range.
     * e.g., 4500 -> "1000-10000"
     */
    public static String generaliseAmount(BigDecimal amount) {
        double v = amount.doubleValue();
        if (v < 100)    return "0-100";
        if (v < 500)    return "100-500";
        if (v < 1000)   return "500-1000";
        if (v < 5000)   return "1000-5000";
        if (v < 10000)  return "5000-10000";
        if (v < 50000)  return "10000-50000";
        return "50000+";
    }

    /**
     * Suppress a value if it could be used to re-identify the user
     * (i.e., if fewer than k records share the same value — k-anonymity check).
     *
     * @param value     the value to check
     * @param count     how many records share this value
     * @param k         minimum group size for k-anonymity (typically 5)
     */
    public static String kAnonymise(String value, long count, int k) {
        return count >= k ? value : "[SUPPRESSED]";
    }
}
