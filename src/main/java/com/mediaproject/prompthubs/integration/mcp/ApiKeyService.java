package com.mediaproject.prompthubs.integration.mcp;

import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.entity.ApiKey;
import com.mediaproject.prompthubs.integration.common.repository.ApiKeyRepository;
import com.mediaproject.prompthubs.integration.mcp.dto.ApiKeyListResponse;
import com.mediaproject.prompthubs.integration.mcp.dto.IssueApiKeyRequest;
import com.mediaproject.prompthubs.integration.mcp.dto.IssueApiKeyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private static final int RAW_BYTES = 24;        // 24 bytes -> 32 base64url chars
    private static final int PREFIX_LEN = 12;       // includes "ph_" + 8 chars
    private static final int BCRYPT_COST = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final WorkspaceRepository workspaceRepository;

    @Value("${integrations.mcp.api-key-prefix:ph_}")
    private String keyPrefixHead;

    @Transactional
    public IssueApiKeyResponse issue(IssueApiKeyRequest request) {
        WorkspaceContext context = requireAdmin();

        Workspace workspace = workspaceRepository.findById(context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        String plaintext = generatePlaintextKey();
        String prefix = plaintext.substring(0, Math.min(PREFIX_LEN, plaintext.length()));
        String hash = BCrypt.hashpw(plaintext, BCrypt.gensalt(BCRYPT_COST));

        ApiKey apiKey = ApiKey.builder()
                .workspace(workspace)
                .keyHash(hash)
                .keyPrefix(prefix)
                .label(request.getLabel())
                .expiresAt(request.getExpiresAt())
                .active(true)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API key issued: workspace={} label={} prefix={}",
                context.getWorkspaceSlug(), request.getLabel(), prefix);

        return IssueApiKeyResponse.builder()
                .id(saved.getId())
                .label(saved.getLabel())
                .keyPlaintext(plaintext)
                .keyPrefix(prefix)
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    public List<ApiKeyListResponse> list() {
        WorkspaceContext context = requireWorkspace();
        return apiKeyRepository.findByWorkspaceId(context.getWorkspaceId()).stream()
                .map(ApiKeyListResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(UUID id) {
        WorkspaceContext context = requireAdmin();
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "API key not found"));
        if (!key.getWorkspace().getId().equals(context.getWorkspaceId())) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        apiKeyRepository.delete(key);
        log.info("API key deleted: workspace={} id={}", context.getWorkspaceSlug(), id);
    }

    /**
     * Verifies a plaintext API key from a request header. Returns the matching active ApiKey
     * (and bumps lastUsedAt) or empty if none matches.
     */
    @Transactional
    public Optional<ApiKey> verify(String plaintext) {
        if (plaintext == null || plaintext.length() < PREFIX_LEN) {
            return Optional.empty();
        }
        String prefix = plaintext.substring(0, PREFIX_LEN);
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndActiveTrue(prefix);
        for (ApiKey candidate : candidates) {
            if (candidate.isExpired()) {
                continue;
            }
            try {
                if (BCrypt.checkpw(plaintext, candidate.getKeyHash())) {
                    candidate.touchUsage();
                    candidate.getWorkspace().getSlug();
                    return Optional.of(candidate);
                }
            } catch (IllegalArgumentException ignored) {
                // bad hash on row — skip
            }
        }
        return Optional.empty();
    }

    private String generatePlaintextKey() {
        byte[] bytes = new byte[RAW_BYTES];
        RANDOM.nextBytes(bytes);
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return keyPrefixHead + body;
    }

    private WorkspaceContext requireWorkspace() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }

    private WorkspaceContext requireAdmin() {
        WorkspaceContext context = requireWorkspace();
        if (!context.isAdmin()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION, "Admin role required");
        }
        return context;
    }
}
