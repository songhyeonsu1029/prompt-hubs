package com.mediaproject.prompthubs.integration.slack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Validates X-Slack-Signature using HMAC-SHA256(timestamp + body).
 * Reference: https://api.slack.com/authentication/verifying-requests-from-slack
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlackSignatureVerifier {

    private static final long MAX_TIMESTAMP_DRIFT_SECONDS = 60 * 5;
    private static final String VERSION = "v0";

    private final SlackProperties properties;

    public boolean verify(String timestamp, String body, String providedSignature) {
        if (timestamp == null || body == null || providedSignature == null) {
            return false;
        }
        if (properties.getSigningSecret() == null || properties.getSigningSecret().isBlank()) {
            log.warn("Slack signing secret is not configured — rejecting request");
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - ts) > MAX_TIMESTAMP_DRIFT_SECONDS) {
            return false;
        }

        String basestring = VERSION + ":" + timestamp + ":" + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(basestring.getBytes(StandardCharsets.UTF_8));
            String expected = VERSION + "=" + HexFormat.of().formatHex(hash);
            return constantTimeEquals(expected, providedSignature);
        } catch (Exception e) {
            log.error("Slack signature verification failed", e);
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
