package com.aicommandcenter.document.entity;

/** Lifecycle of an uploaded document. */
public enum DocumentStatus {
    /** Stored on disk, not yet processed. */
    PENDING,
    /** Extraction/chunking/embedding in progress. */
    PROCESSING,
    /** Text extracted and chunks embedded — usable by the knowledge base. */
    READY,
    /** Processing failed; {@code failureReason} explains why (safe for the owner to read). */
    FAILED
}
