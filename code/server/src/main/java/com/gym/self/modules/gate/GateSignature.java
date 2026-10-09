package com.gym.self.modules.gate;

import com.gym.self.common.time.TimeProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

public final class GateSignature {

    private GateSignature() {
    }

    public static String sha1(String sn, String token, String timestamp, String nonce) {
        String[] parts = {sn, token, timestamp, "nonce" + nonce};
        Arrays.sort(parts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            String joined = String.join("", parts);
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static boolean valid(String sn, String token, String timestamp, String nonce, String signature,
                                TimeProvider timeProvider) {
        if (sn == null || token == null || timestamp == null || nonce == null || signature == null || signature.isBlank()) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        long now = timeProvider.now().atZone(TimeProvider.ZONE).toEpochSecond();
        if (Math.abs(now - ts) > 60) {
            return false;
        }
        return sha1(sn, token, timestamp, nonce).equalsIgnoreCase(signature.trim());
    }
}
