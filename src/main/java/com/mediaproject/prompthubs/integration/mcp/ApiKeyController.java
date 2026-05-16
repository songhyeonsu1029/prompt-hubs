package com.mediaproject.prompthubs.integration.mcp;

import com.mediaproject.prompthubs.integration.mcp.dto.ApiKeyListResponse;
import com.mediaproject.prompthubs.integration.mcp.dto.IssueApiKeyRequest;
import com.mediaproject.prompthubs.integration.mcp.dto.IssueApiKeyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/w/{slug}/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<IssueApiKeyResponse> issue(@PathVariable String slug,
                                                     @Valid @RequestBody IssueApiKeyRequest request) {
        IssueApiKeyResponse response = apiKeyService.issue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyListResponse>> list(@PathVariable String slug) {
        return ResponseEntity.ok(apiKeyService.list());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String slug, @PathVariable UUID id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
