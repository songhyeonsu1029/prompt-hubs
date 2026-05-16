package com.mediaproject.prompthubs.global.security;

import tools.jackson.databind.ObjectMapper;
import com.mediaproject.prompthubs.domain.workspace.entity.Member;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.MemberRepository;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceAccessFilter extends OncePerRequestFilter {

    private static final Pattern WORKSPACE_URL_PATTERN = Pattern.compile("^/api/v1/w/([^/]+)(/.*)?$");

    private final WorkspaceRepository workspaceRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            Matcher matcher = WORKSPACE_URL_PATTERN.matcher(path);

            if (matcher.matches()) {
                String slug = matcher.group(1);

                // Check authentication
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated() ||
                    !(authentication.getPrincipal() instanceof CustomUserDetails)) {
                    sendErrorResponse(response, ErrorCode.UNAUTHORIZED);
                    return;
                }

                CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

                // Find workspace by slug
                Workspace workspace = workspaceRepository.findBySlugAndDeletedAtIsNull(slug)
                        .orElse(null);

                if (workspace == null) {
                    sendErrorResponse(response, ErrorCode.WORKSPACE_NOT_FOUND);
                    return;
                }

                // Check if user is a member of this workspace
                Member member = memberRepository.findByAccountIdAndWorkspaceId(
                        userDetails.getAccountId(),
                        workspace.getId()
                ).orElse(null);

                if (member == null) {
                    sendErrorResponse(response, ErrorCode.NOT_WORKSPACE_MEMBER);
                    return;
                }

                // Set workspace context
                WorkspaceContext context = new WorkspaceContext(
                        workspace.getId(),
                        workspace.getSlug(),
                        member.getRole()
                );
                WorkspaceContextHolder.setContext(context);

                log.debug("Workspace access granted: user={}, workspace={}, role={}",
                        userDetails.getEmail(), slug, member.getRole());
            }

            filterChain.doFilter(request, response);

        } finally {
            WorkspaceContextHolder.clear();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}