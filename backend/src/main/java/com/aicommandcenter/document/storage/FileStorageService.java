package com.aicommandcenter.document.storage;

import com.aicommandcenter.config.StorageProperties;
import com.aicommandcenter.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Owns every filesystem operation for uploads.
 *
 * <p>Security properties that matter here: the stored path is built exclusively from the
 * authenticated user id plus a random UUID — the client-supplied name never reaches the
 * filesystem. Every resolved path is additionally asserted to stay inside the configured root,
 * so even a bug elsewhere cannot turn into a traversal write.</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path root;

    public FileStorageService(StorageProperties properties) {
        this.root = Paths.get(properties.location()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create upload directory " + root, ex);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Configured upload location is not a directory: " + root);
        }
        log.info("Document storage root: {}", root);
    }

    public Path root() {
        return root;
    }

    /**
     * Streams an upload to disk.
     *
     * @return the storage path relative to the root, e.g. {@code 42/9f1c….pdf}
     */
    public String store(Long userId, InputStream content, String extension) {
        String relative = userId + "/" + UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
        Path target = resolveInsideRoot(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store uploaded file", ex);
        }
        return relative;
    }

    public byte[] read(String relativePath) {
        try {
            return Files.readAllBytes(resolveInsideRoot(relativePath));
        } catch (IOException ex) {
            throw new BadRequestException("FILE_UNREADABLE", "The stored file could not be read");
        }
    }

    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolveInsideRoot(relativePath));
        } catch (Exception ex) {
            // Losing the blob is not worth failing a metadata delete the user asked for.
            log.warn("Could not delete stored file {}: {}", relativePath, ex.getClass().getSimpleName());
        }
    }

    /** Resolution + containment check in one place; the only way to obtain a writable path. */
    private Path resolveInsideRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BadRequestException("INVALID_PATH", "Storage path is required");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("INVALID_PATH", "Storage path escapes the upload directory");
        }
        return resolved;
    }
}
