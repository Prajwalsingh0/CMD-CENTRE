package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.document.dto.DocumentInsightOperation;
import com.aicommandcenter.document.dto.DocumentInsightResponse;
import com.aicommandcenter.document.dto.DocumentResponse;
import com.aicommandcenter.document.service.DocumentService;
import com.aicommandcenter.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SummarizeDocumentTool implements CommandTool {

    private final DocumentService documentService;

    public SummarizeDocumentTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.SUMMARIZE_DOCUMENT;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.SUMMARIZE_DOCUMENT.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        Long documentId = args.optionalLong("documentId");
        DocumentResponse document = documentId != null
                ? documentService.get(userId, documentId)
                : findByPartialName(userId, args.optional("documentName"));

        DocumentInsightResponse insight = documentService.insight(userId, document.id(),
                DocumentInsightOperation.SUMMARY);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("document", document);
        data.put("content", insight.content());
        data.put("provider", insight.provider());
        return ToolResult.of("Summarised \"" + document.name() + "\"",
                List.of("Read \"" + document.name() + "\"",
                        "Produced a summary with the " + insight.provider() + " provider"), data);
    }

    /** Falls back to the most recent document when the command does not name one. */
    private DocumentResponse findByPartialName(Long userId, String name) {
        List<DocumentResponse> documents = documentService.list(userId, null, null);
        if (documents.isEmpty()) {
            throw new BadRequestException("NO_DOCUMENTS", "Upload a document first");
        }
        if (name == null || name.isBlank()) {
            return documents.get(0);
        }
        String needle = name.toLowerCase(Locale.ROOT);
        return documents.stream()
                .filter(document -> document.name().toLowerCase(Locale.ROOT).contains(needle))
                .findFirst()
                .orElse(documents.get(0));
    }
}
