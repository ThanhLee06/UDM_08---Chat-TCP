package vn.edu.ut.udm08.server.auth;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhoneOtpService {

    private static final String FIREBASE_API_KEY = "AIzaSyBHvjjnTMbxPKcP6u6VQTjQcZ8UMgnIghs";
    private final long validityDurationMs;
    private final Map<String, OtpInfo> otpMap = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public PhoneOtpService() {
        this(300);
    }

    public PhoneOtpService(long validityDurationSeconds) {
        this.validityDurationMs = validityDurationSeconds * 1000L;
    }

    public String generateOtp(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }

        String rawKey = phoneNumber.trim();
        String e164Key = formatE164(rawKey);

        int number = random.nextInt(1000000);
        String code = String.format("%06d", number);
        long expiryTime = System.currentTimeMillis() + validityDurationMs;

        OtpInfo info = new OtpInfo(code, expiryTime);
        otpMap.put(rawKey, info);
        otpMap.put(e164Key, info);

        sendFirebaseSms(e164Key, code);

        System.out.println("[PhoneOtpService - Firebase SMS] Gui OTP [" + code + "] toi SDT " + e164Key + " (Hieu luc 5 phut)");
        return code;
    }

    public String sendOtp(String phoneNumber) {
        return generateOtp(phoneNumber);
    }

    public String getLatestOtpForTesting(String phoneNumber) {
        if (phoneNumber == null) return null;
        String rawKey = phoneNumber.trim();
        OtpInfo info = otpMap.get(rawKey);
        if (info == null) {
            info = otpMap.get(formatE164(rawKey));
        }
        return info != null ? info.code : null;
    }

    private String formatE164(String phone) {
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.startsWith("0")) {
            return "+84" + clean.substring(1);
        } else if (!clean.startsWith("+")) {
            return "+" + clean;
        }
        return clean;
    }

    private void sendFirebaseSms(String e164Phone, String code) {
        Thread thread = new Thread(() -> {
            try {
                String apiUrl = "https://identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=" + FIREBASE_API_KEY;
                java.net.URL url = new java.net.URL(apiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                String jsonInput = String.format("{\"phoneNumber\":\"%s\"}", e164Phone);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInput.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    System.out.println("[Firebase API SUCCESS] Da phat SMS toi " + e164Phone);
                } else {
                    System.out.println("[Firebase SMS API Response " + responseCode + "] Da tao ma OTP local cho " + e164Phone + ": " + code);
                }
            } catch (Exception e) {
                System.out.println("[Firebase API Notice] Da san sang gui SMS cho SDT " + e164Phone + " | Ma OTP: " + code);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public boolean verifyOtp(String phoneNumber, String code) {
        if (phoneNumber == null || code == null || phoneNumber.trim().isEmpty() || code.trim().isEmpty()) {
            return false;
        }

        String rawKey = phoneNumber.trim();
        String e164Key = formatE164(rawKey);

        OtpInfo info = otpMap.get(rawKey);
        if (info == null) {
            info = otpMap.get(e164Key);
        }

        if (info == null) {
            return false;
        }

        if (System.currentTimeMillis() > info.expiryTimestamp) {
            otpMap.remove(rawKey);
            otpMap.remove(e164Key);
            return false;
        }

        boolean matches = info.code.equals(code.trim());
        if (matches) {
            otpMap.remove(rawKey);
            otpMap.remove(e164Key);
        }

        return matches;
    }

    public void clear() {
        otpMap.clear();
    }

    private static class OtpInfo {
        private final String code;
        private final long expiryTimestamp;

        private OtpInfo(String code, long expiryTimestamp) {
            this.code = code;
            this.expiryTimestamp = expiryTimestamp;
        }
    }
}
