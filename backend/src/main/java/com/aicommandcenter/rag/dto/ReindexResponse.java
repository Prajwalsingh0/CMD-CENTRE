package com.aicommandcenter.rag.dto;

public record ReindexResponse(int documentsProcessed, long chunksEmbedded, String provider) {
}
