package com.aicommandcenter.document.validation;

import com.aicommandcenter.config.StorageProperties;
import com.aicommandcenter.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Upload gate. A file must pass <em>all</em> of these before a single byte is written:
 *
 * <ol>
 *   <li>present and non-empty,</li>
 *   <li>within the configured size ceiling,</li>
 *   <li>extension in the allow-list,</li>
 *   <li>declared content type in the allow-list (when the browser sends one),</li>
 *   <li>content actually matches the extension (magic bytes) — this is the check that stops a
 *       renamed executable from being treated as a document,</li>
 *   <li>a display name that survives sanitisation.</li>
 * </ol>
 */
@Component
public class FileValidator {

    private static final int SNIFF_BYTES = 512;

    private final StorageProperties properties;

    public FileValidator(StorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_REQUIRED", "A non-empty file is required");
        }
        long maxSize = properties.maxFileSizeBytes() > 0 ? properties.maxFileSizeBytes() : 8L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BadRequestException("FILE_TOO_LARGE",
                    "File exceeds the maximum allowed size of " + (maxSize / (1024 * 1024)) + " MB");
        }

        String displayName = sanitiseName(file.getOriginalFilename());
        String extension = extensionOf(displayName);
        if (!allowed(extension, properties.allowedExtensions())) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE",
                    "Unsupported file type '." + extension + "'. Allowed: "
                            + String.join(", ", properties.allowedExtensions() == null ? java.util.List.of() : properties.allowedExtensions()));
        }

        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declared.isBlank() && !"application/octet-stream".equals(declared)
                && !allowed(declared, properties.allowedContentTypes())) {
            throw new BadRequestException("UNSUPPORTED_CONTENT_TYPE",
                    "Declared content type '" + declared + "' is not allowed");
        }

        byte[] head = readHead(file);
        assertContentMatchesType(extension, head);

        return new ValidatedUpload(displayName, extension, declared.isBlank() ? defaultContentType(extension) : declared,
                file.getSize());
    }

    private void assertContentMatchesType(String extension, byte[] head) {
        if ("pdf".equals(extension)) {
            String magic = new String(head, 0, Math.min(5, head.length), java.nio.charset.StandardCharsets.US_ASCII);
            if (!magic.startsWith("%PDF-")) {
                throw new BadRequestException("CONTENT_TYPE_MISMATCH",
                        "The file extension says PDF but the content is not a PDF");
            }
            return;
        }
        for (byte value : head) {
            if (value == 0) {
                throw new BadRequestException("CONTENT_TYPE_MISMATCH",
                        "Text documents must not contain binary data");
            }
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException ex) {
            throw new BadRequestException("FILE_UNREADABLE", "The upload could not be read");
        }
    }

    /** Keeps only the final path segment and strips anything that could confuse a shell or a log. */
    public static String sanitiseName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new BadRequestException("FILE_REQUIRED", "The uploaded file has no name");
        }
        String name = originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").replaceAll("[<>:\"|?*]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new BadRequestException("INVALID_FILE_NAME", "The uploaded file name is not usable");
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean allowed(String value, java.util.List<String> allowList) {
        if (value == null || value.isBlank() || allowList == null) {
            return false;
        }
        String needle = value.toLowerCase(Locale.ROOT);
        return allowList.stream().map(item -> item.toLowerCase(Locale.ROOT)).anyMatch(needle::equals);
    }

    private static String defaultContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "md", "markdown" -> "text/markdown";
            default -> "text/plain";
        };
    }

    public record ValidatedUpload(String displayName, String extension, String contentType, long sizeBytes) {
    }
}
