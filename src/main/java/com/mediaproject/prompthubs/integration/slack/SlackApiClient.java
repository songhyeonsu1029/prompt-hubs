package com.mediaproject.prompthubs.integration.slack;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.request.conversations.ConversationsListRequest;
import com.slack.api.methods.request.oauth.OAuthV2AccessRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.ConversationType;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.mediaproject.prompthubs.global.exception.BusinessException;
import com.mediaproject.prompthubs.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Thin wrapper over slack-api-client. Centralizes auth and exception translation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlackApiClient {

    private final SlackProperties properties;
    private final Slack slack = Slack.getInstance();

    public OAuthV2AccessResponse exchangeCode(String code) {
        try {
            MethodsClient client = slack.methods();
            return client.oauthV2Access(OAuthV2AccessRequest.builder()
                    .clientId(properties.getClientId())
                    .clientSecret(properties.getClientSecret())
                    .code(code)
                    .redirectUri(properties.getRedirectUri())
                    .build());
        } catch (IOException | SlackApiException e) {
            throw new BusinessException(ErrorCode.INTEGRATION_OAUTH_FAILED, e.getMessage());
        }
    }

    public ChatPostMessageResponse postMessage(String botToken, String channel, String text, List<LayoutBlock> blocks) {
        try {
            return slack.methods(botToken).chatPostMessage(ChatPostMessageRequest.builder()
                    .channel(channel)
                    .text(text)
                    .blocks(blocks)
                    .build());
        } catch (IOException | SlackApiException e) {
            log.error("Slack postMessage failed", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }

    public ChatUpdateResponse updateMessage(String botToken, String channel, String ts, String text, List<LayoutBlock> blocks) {
        try {
            return slack.methods(botToken).chatUpdate(ChatUpdateRequest.builder()
                    .channel(channel)
                    .ts(ts)
                    .text(text)
                    .blocks(blocks)
                    .build());
        } catch (IOException | SlackApiException e) {
            log.error("Slack chatUpdate failed", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }

    public ViewsOpenResponse openModal(String botToken, String triggerId, View view) {
        try {
            return slack.methods(botToken).viewsOpen(ViewsOpenRequest.builder()
                    .triggerId(triggerId)
                    .view(view)
                    .build());
        } catch (IOException | SlackApiException e) {
            log.error("Slack viewsOpen failed", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }

    /**
     * Returns channels the bot is a member of (both public and private).
     * Slack only returns channels the bot is invited to when filtering by membership.
     */
    public List<Conversation> listBotChannels(String botToken) {
        try {
            ConversationsListResponse resp = slack.methods(botToken).conversationsList(
                    ConversationsListRequest.builder()
                            .types(List.of(ConversationType.PUBLIC_CHANNEL, ConversationType.PRIVATE_CHANNEL))
                            .excludeArchived(true)
                            .limit(200)
                            .build());
            if (resp == null || !resp.isOk()) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                        resp == null ? "no response" : resp.getError());
            }
            List<Conversation> all = resp.getChannels() == null ? List.of() : resp.getChannels();
            return all.stream().filter(c -> Boolean.TRUE.equals(c.isMember())).toList();
        } catch (IOException | SlackApiException e) {
            log.error("Slack conversations.list failed", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e.getMessage());
        }
    }
}
