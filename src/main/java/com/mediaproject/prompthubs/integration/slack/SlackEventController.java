package com.mediaproject.prompthubs.integration.slack;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Receives Slack-originated webhooks: slash commands and interactions.
 * All requests are authenticated via X-Slack-Signature; payload bodies are
 * application/x-www-form-urlencoded so we read the raw body and parse manually
 * (Slack signatures are computed over the raw body, so we cannot use Spring's
 * @RequestParam binding which reads the body separately).
 */
@Slf4j
@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackEventController {

    private static final String SIG_HEADER = "X-Slack-Signature";
    private static final String TS_HEADER = "X-Slack-Request-Timestamp";

    private final SlackSignatureVerifier signatureVerifier;
    private final SlackCommandService commandService;
    private final SlackInteractionService interactionService;

    @PostMapping(value = "/commands",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> commands(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        if (!signatureVerifier.verify(request.getHeader(TS_HEADER), body, request.getHeader(SIG_HEADER))) {
            return ResponseEntity.status(401).build();
        }
        Map<String, String> form = parseForm(body);
        String teamId = form.getOrDefault("team_id", "");
        String command = form.getOrDefault("command", "");
        String text = form.getOrDefault("text", "");

        String response = commandService.handleCommand(teamId, command, text);
        Map<String, Object> body2 = new HashMap<>();
        body2.put("response_type", "ephemeral");
        body2.put("text", response);
        return ResponseEntity.ok(body2);
    }

    @PostMapping(value = "/interactions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> interactions(HttpServletRequest request) throws IOException {
        String body = readBody(request);
        if (!signatureVerifier.verify(request.getHeader(TS_HEADER), body, request.getHeader(SIG_HEADER))) {
            return ResponseEntity.status(401).build();
        }
        Map<String, String> form = parseForm(body);
        String payload = form.getOrDefault("payload", "");
        String response = interactionService.handle(payload);
        Map<String, Object> body2 = new HashMap<>();
        body2.put("response_type", "ephemeral");
        body2.put("text", response.isBlank() ? "ok" : response);
        return ResponseEntity.ok(body2);
    }

    @PostMapping(value = "/events",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> events(HttpServletRequest request,
                                                      @RequestBody String body) {
        if (!signatureVerifier.verify(request.getHeader(TS_HEADER), body, request.getHeader(SIG_HEADER))) {
            return ResponseEntity.status(401).build();
        }
        // URL verification handshake.
        Map<String, Object> resp = new HashMap<>();
        if (body.contains("\"type\":\"url_verification\"")) {
            int idx = body.indexOf("\"challenge\":\"");
            if (idx > 0) {
                int start = idx + "\"challenge\":\"".length();
                int end = body.indexOf('"', start);
                resp.put("challenge", body.substring(start, end));
            }
        }
        return ResponseEntity.ok(resp);
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            // Trim the trailing newline we appended on the last line
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        if (body == null || body.isEmpty()) return form;
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            form.put(k, v);
        }
        return form;
    }
}
