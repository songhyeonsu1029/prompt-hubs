package com.mediaproject.prompthubs.integration.common;

import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.dto.IntegrationStatusResponse;
import com.mediaproject.prompthubs.integration.common.entity.IntegrationType;
import com.mediaproject.prompthubs.integration.common.entity.WorkspaceIntegration;
import com.mediaproject.prompthubs.integration.common.repository.WorkspaceIntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/w/{slug}/integrations")
@RequiredArgsConstructor
public class IntegrationStatusController {

    private final WorkspaceIntegrationRepository integrationRepository;

    @GetMapping
    public ResponseEntity<List<IntegrationStatusResponse>> list(@PathVariable String slug) {
        WorkspaceContext context = requireContext();
        List<IntegrationStatusResponse> responses = integrationRepository
                .findByWorkspaceId(context.getWorkspaceId()).stream()
                .map(IntegrationStatusResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/notion/status")
    public ResponseEntity<IntegrationStatusResponse> notionStatus(@PathVariable String slug) {
        WorkspaceContext context = requireContext();
        WorkspaceIntegration integration = integrationRepository
                .findByWorkspaceIdAndType(context.getWorkspaceId(), IntegrationType.NOTION)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTEGRATION_NOT_FOUND));
        return ResponseEntity.ok(IntegrationStatusResponse.from(integration));
    }

    private WorkspaceContext requireContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
        }
        return context;
    }
}
