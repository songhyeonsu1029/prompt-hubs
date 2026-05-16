package com.mediaproject.prompthubs.domain.doc.controller;

import com.mediaproject.prompthubs.domain.doc.dto.*;
import com.mediaproject.prompthubs.domain.doc.service.DocService;
import com.mediaproject.prompthubs.global.common.PageResponse;
import com.mediaproject.prompthubs.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Doc", description = "Team documentation API")
@RestController
@RequestMapping("/api/v1/w/{slug}/docs")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @Operation(summary = "Create doc", description = "Create a new team document")
    @PostMapping
    public ResponseEntity<DocResponse> create(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateDocRequest request) {
        DocResponse response = docService.create(request, userDetails.getAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get docs", description = "Get list of documents with optional category filter")
    @GetMapping
    public ResponseEntity<PageResponse<DocResponse>> getDocs(
            @PathVariable String slug,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<DocResponse> response = docService.getDocs(category, pageable);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get doc", description = "Get a specific document by ID")
    @GetMapping("/{docId}")
    public ResponseEntity<DocResponse> getDoc(
            @PathVariable String slug,
            @PathVariable UUID docId) {
        DocResponse response = docService.getDoc(docId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update doc", description = "Update an existing document")
    @PutMapping("/{docId}")
    public ResponseEntity<DocResponse> update(
            @PathVariable String slug,
            @PathVariable UUID docId,
            @Valid @RequestBody UpdateDocRequest request) {
        DocResponse response = docService.update(docId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete doc", description = "Delete a document")
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(
            @PathVariable String slug,
            @PathVariable UUID docId) {
        docService.delete(docId);
        return ResponseEntity.noContent().build();
    }
}