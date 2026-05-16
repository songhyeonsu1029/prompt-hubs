package com.mediaproject.prompthubs.integration.common.event;

import com.mediaproject.prompthubs.domain.doc.entity.Doc;

import java.util.UUID;

public record DocChangedEvent(UUID workspaceId, UUID docId, Doc doc, ChangeType type) {

    public enum ChangeType { CREATED, UPDATED, DELETED }
}
