package com.aicommandcenter.document.extract;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Turns stored bytes into plain text.
 *
 * <p>PDF parsing is inherently untrusted-input territory, so the extractor caps page count and
 * output size and converts every parser failure into a controlled {@link ExtractionException}
 * rather than letting a malformed file take down the request.</p>
 */
public final class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private static final int MAX_CHARS = 400_000;
    private static final int MAX_PDF_PAGES = 300;

    private TextExtractionService() {
    }

    public static String extract(Path file, String extension) {
        try {
            return switch (extension == null ? "" : extension.toLowerCase()) {
                case "pdf" -> extractPdf(file);
                case "txt", "md", "markdown" -> extractPlain(file);
                default -> throw new ExtractionException("Unsupported file type for text extraction");
            };
        } catch (ExtractionException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("Extraction failed for {}: {}", file.getFileName(), ex.getClass().getSimpleName());
            throw new ExtractionException("The document could not be parsed");
        } catch (RuntimeException ex) {
            log.warn("Unexpected extraction error for {}: {}", file.getFileName(), ex.getClass().getSimpleName());
            throw new ExtractionException("The document could not be parsed");
        }
    }

    private static String extractPdf(Path file) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            int pages = pdf.getNumberOfPages();
            if (pages > MAX_PDF_PAGES) {
                log.info("PDF has {} pages; extracting the first {}", pages, MAX_PDF_PAGES);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(pages, MAX_PDF_PAGES));
            String text = stripper.getText(pdf);
            if (text == null || text.isBlank()) {
                throw new ExtractionException(
                        "No selectable text found in this PDF (it may be a scanned image without OCR)");
            }
            return truncate(text);
        }
    }

    private static String extractPlain(Path file) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new ExtractionException("The document is empty");
        }
        return truncate(text);
    }

    private static String truncate(String text) {
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }

    /** Extraction problem the user is allowed to see (no parser internals). */
    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message) {
            super(message);
        }
    }
}
