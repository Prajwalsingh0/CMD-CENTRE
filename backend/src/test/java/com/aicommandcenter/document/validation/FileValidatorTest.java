package com.aicommandcenter.document.validation;

import com.aicommandcenter.config.StorageProperties;
import com.aicommandcenter.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {

    private static final List<String> EXTENSIONS = List.of("pdf", "txt", "md", "markdown");
    private static final List<String> CONTENT_TYPES =
            List.of("application/pdf", "text/plain", "text/markdown", "text/x-markdown");

    private final FileValidator validator = new FileValidator(new StorageProperties(
            "./target/test-uploads", 1024 * 1024, EXTENSIONS, CONTENT_TYPES));

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    @DisplayName("an allow-listed text upload passes and reports a sanitised name")
    void acceptsTextUploads() {
        var validated = validator.validate(file("notes.md", "text/markdown", "hello".getBytes(StandardCharsets.UTF_8)));
        assertThat(validated.extension()).isEqualTo("md");
        assertThat(validated.displayName()).isEqualTo("notes.md");
        assertThat(validated.contentType()).isEqualTo("text/markdown");
    }

    @Test
    @DisplayName("a real PDF header is accepted")
    void acceptsPdfHeader() {
        byte[] pdf = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
        var validated = validator.validate(file("report.pdf", "application/pdf", pdf));
        assertThat(validated.extension()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("empty uploads and missing files are refused")
    void rejectsEmpty() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("non-empty");
        assertThatThrownBy(() -> validator.validate(file("empty.txt", "text/plain", new byte[0])))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("the size ceiling is enforced before anything is written")
    void rejectsOversizedFiles() {
        byte[] big = new byte[1024 * 1024 + 1];
        assertThatThrownBy(() -> validator.validate(file("big.txt", "text/plain", big)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum allowed size");
    }

    @Test
    @DisplayName("extensions outside the allow-list are refused")
    void rejectsUnsupportedExtensions() {
        assertThatThrownBy(() -> validator.validate(file("script.exe", "application/octet-stream",
                new byte[]{1, 2, 3})))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");

        assertThatThrownBy(() -> validator.validate(file("archive.zip", "application/zip", new byte[]{1})))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("content that contradicts the extension is refused")
    void rejectsContentTypeMismatch() {
        assertThatThrownBy(() -> validator.validate(file("fake.pdf", "application/pdf",
                "not a pdf at all".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a PDF");

        assertThatThrownBy(() -> validator.validate(file("binary.txt", "text/plain",
                new byte[]{'a', 0x00, 0x01})))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("binary");
    }

    @Test
    @DisplayName("a declared content type outside the allow-list is refused")
    void rejectsWrongDeclaredContentType() {
        assertThatThrownBy(() -> validator.validate(file("notes.txt", "application/x-msdownload",
                "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("a browser that sends no content type still works")
    void toleratesMissingContentType() {
        var validated = validator.validate(file("notes.txt", null, "plain".getBytes(StandardCharsets.UTF_8)));
        assertThat(validated.contentType()).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("traversal paths are reduced to a bare file name")
    void sanitisesFileNames() {
        assertThat(FileValidator.sanitiseName("../../etc/passwd.md")).isEqualTo("passwd.md");
        assertThat(FileValidator.sanitiseName("C:\\Windows\\system32\\evil.md")).isEqualTo("evil.md");
        assertThat(FileValidator.sanitiseName("re:port?.md")).isEqualTo("re_port_.md");
        assertThat(FileValidator.sanitiseName("  spaced  .md  ")).isEqualTo("spaced  .md");
        assertThatThrownBy(() -> FileValidator.sanitiseName("..")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> FileValidator.sanitiseName(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a file with no extension is refused")
    void requiresAnExtension() {
        assertThatThrownBy(() -> validator.validate(file("README", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BadRequestException.class);
    }
}
