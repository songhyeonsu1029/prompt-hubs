package com.mediaproject.prompthubs.global.security;

public class WorkspaceContextHolder {

    private static final ThreadLocal<WorkspaceContext> contextHolder = new ThreadLocal<>();

    public static void setContext(WorkspaceContext context) {
        contextHolder.set(context);
    }

    public static WorkspaceContext getContext() {
        return contextHolder.get();
    }

    public static void clear() {
        contextHolder.remove();
    }
}