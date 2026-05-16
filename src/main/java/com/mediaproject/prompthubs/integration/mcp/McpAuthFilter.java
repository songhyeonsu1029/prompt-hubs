package com.mediaproject.prompthubs.integration.mcp;

import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.exception.ErrorResponse;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.entity.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates MCP external endpoints via the X-API-Key header.
 * Pass-through for any non-MCP path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpAuthFilter extends OncePerRequestFilter {

    private static final String LEGACY_MCP_PATH_PREFIX = "/api/v1/external/mcp/";
    private static final String STANDARD_MCP_PATH = "/mcp";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isLegacy = path.startsWith(LEGACY_MCP_PATH_PREFIX);
        boolean isStandard = path.equals(STANDARD_MCP_PATH) || path.startsWith(STANDARD_MCP_PATH + "/");
        if (!isLegacy && !isStandard) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request);
        if (apiKey == null || apiKey.isBlank()) {
            sendError(response, ErrorCode.INVALID_API_KEY);
            return;
        }

        Optional<ApiKey> match = apiKeyService.verify(apiKey);
        if (match.isEmpty()) {
            sendError(response, ErrorCode.INVALID_API_KEY);
            return;
        }

        ApiKey key = match.get();
        Workspace workspace = key.getWorkspace();

        try {
            // Inject minimal authentication so downstream code can rely on a non-anonymous principal.
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "api-key:" + key.getId(),
                            null,
                            List.of()
                    )
            );

            WorkspaceContextHolder.setContext(new WorkspaceContext(
                    workspace.getId(),
                    workspace.getSlug(),
                    Member.Role.MEMBER
            ));

            filterChain.doFilter(request, response);
        } finally {
            WorkspaceContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) return header.trim();
        String auth = request.getHeader(AUTH_HEADER);
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private void sendError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(code)));
    }
}
