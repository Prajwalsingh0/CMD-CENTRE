package com.aicommandcenter.ai.chat;

import com.aicommandcenter.ai.AiException;
import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.rag.RagService;
import com.aicommandcenter.rag.dto.AnswerResponse;
import com.aicommandcenter.rag.dto.KnowledgeAskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Free-form chat that is knowledge-aware.
 *
 * <p>Every message first gets a retrieval attempt against the caller's own documents. If anything
 * is retrieved the answer must be grounded in it and carries its sources; only when nothing is
 * retrieved does the request become a general chat completion — and even then the prompt states
 * that no document context was available, so the model cannot imply it read the user's files.</p>
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final RagService ragService;
    private final AiService aiService;
    private final AiActivityService activityService;

    public AiChatService(RagService ragService, AiService aiService, AiActivityService activityService) {
        this.ragService = ragService;
        this.aiService = aiService;
        this.activityService = activityService;
    }

    public ChatResponse chat(Long userId, ChatRequest request) {
        String message = request.message().trim();

        AnswerResponse grounded = ragService.ask(userId, new KnowledgeAskRequest(message, null, null));
        if (grounded.grounded()) {
            return new ChatResponse(grounded.answer(), true, grounded.sources(),
                    grounded.provider(), grounded.remoteProvider());
        }

        String reply;
        try {
            reply = aiService.complete(new AiRequest(
                    "You are a concise personal assistant inside a productivity workspace. "
                            + "No documents were retrieved, so do not claim to have read the user's files. "
                            + "If the question needs their data, tell them what to upload or which page to open.",
                    java.util.List.of(com.aicommandcenter.ai.AiMessage.user(message)),
                    AiTask.GENERIC, false, 800, 0.3)).text();
        } catch (AiException ex) {
            log.info("Chat completion fell back to a controlled message: {}", ex.getCode());
            reply = "The AI provider is currently unavailable (" + ex.getCode()
                    + "). Your workspace data is unaffected — try again shortly.";
        }
        activityService.record(userId, message, "CHAT", AiActivityService.ActivityStatus.SUCCESS,
                "Replied without document context.");
        return new ChatResponse(reply, false, java.util.List.of(),
                aiService.providerName(), aiService.isRemote());
    }
}
