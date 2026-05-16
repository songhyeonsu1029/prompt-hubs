package com.mediaproject.prompthubs.domain.doc.service;

import com.mediaproject.prompthubs.domain.account.entity.Account;
import com.mediaproject.prompthubs.domain.account.repository.AccountRepository;
import com.mediaproject.prompthubs.domain.doc.dto.*;
import com.mediaproject.prompthubs.domain.doc.entity.Doc;
import com.mediaproject.prompthubs.domain.doc.repository.DocRepository;
import com.mediaproject.prompthubs.domain.prompt.repository.PromptDocLinkRepository;
import com.mediaproject.prompthubs.domain.workspace.entity.Workspace;
import com.mediaproject.prompthubs.domain.workspace.repository.WorkspaceRepository;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import com.mediaproject.prompthubs.global.security.WorkspaceContext;
import com.mediaproject.prompthubs.global.security.WorkspaceContextHolder;
import com.mediaproject.prompthubs.integration.common.event.DocChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocService {

    private final DocRepository docRepository;
    private final PromptDocLinkRepository docLinkRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DocResponse create(CreateDocRequest request, UUID accountId) {
        WorkspaceContext context = getWorkspaceContext();

        Workspace workspace = workspaceRepository.findById(context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        Doc doc = Doc.builder()
                .workspace(workspace)
                .author(author)
                .title(request.getTitle())
                .content(request.getContent())
                .category(request.getCategory())
                .build();

        Doc savedDoc = docRepository.save(doc);
        log.info("Doc created: {} in workspace {}", savedDoc.getId(), context.getWorkspaceSlug());

        eventPublisher.publishEvent(new DocChangedEvent(workspace.getId(), savedDoc.getId(), savedDoc, DocChangedEvent.ChangeType.CREATED));
        return DocResponse.from(savedDoc);
    }

    public PageResponse<DocResponse> getDocs(String category, Pageable pageable) {
        WorkspaceContext context = getWorkspaceContext();
        Page<Doc> docs;

        if (category != null && !category.isBlank()) {
            docs = docRepository.findByWorkspaceIdAndCategory(context.getWorkspaceId(), category, pageable);
        } else {
            docs = docRepository.findByWorkspaceId(context.getWorkspaceId(), pageable);
        }

        Page<DocResponse> responsePage = docs.map(DocResponse::from);
        return PageResponse.of(responsePage);
    }

    public DocResponse getDoc(UUID docId) {
        WorkspaceContext context = getWorkspaceContext();

        Doc doc = docRepository.findByIdAndWorkspaceId(docId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Doc not found"));

        DocResponse response = DocResponse.from(doc);
        List<DocResponse.LinkedPromptInfo> linkedPrompts = docLinkRepository.findByDocIdWithPrompt(docId).stream()
                .map(link -> DocResponse.LinkedPromptInfo.builder()
                        .id(link.getPrompt().getId())
                        .title(link.getPrompt().getTitle())
                        .status(link.getPrompt().getStatus().name())
                        .build())
                .toList();
        response.setLinkedPrompts(linkedPrompts);
        return response;
    }

    @Transactional
    public DocResponse update(UUID docId, UpdateDocRequest request) {
        WorkspaceContext context = getWorkspaceContext();

        Doc doc = docRepository.findByIdAndWorkspaceId(docId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Doc not found"));

        doc.update(request.getTitle(), request.getContent(), request.getCategory());
        log.info("Doc updated: {} in workspace {}", docId, context.getWorkspaceSlug());

        eventPublisher.publishEvent(new DocChangedEvent(context.getWorkspaceId(), doc.getId(), doc, DocChangedEvent.ChangeType.UPDATED));
        return DocResponse.from(doc);
    }

    @Transactional
    public void delete(UUID docId) {
        WorkspaceContext context = getWorkspaceContext();

        Doc doc = docRepository.findByIdAndWorkspaceId(docId, context.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Doc not found"));

        docRepository.delete(doc);
        log.info("Doc deleted: {} in workspace {}", docId, context.getWorkspaceSlug());

        eventPublisher.publishEvent(new DocChangedEvent(context.getWorkspaceId(), doc.getId(), doc, DocChangedEvent.ChangeType.DELETED));
    }

    private WorkspaceContext getWorkspaceContext() {
        WorkspaceContext context = WorkspaceContextHolder.getContext();
        if (context == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND, "Workspace context not found");
        }
        return context;
    }
}
