package com.eecs3311.backend.material;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class MaterialParser {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 1_000_000;
    public record Parsed(String fileName, String fileType, String text) { }

    public Parsed parse(String originalName, byte[] bytes) {
        if (bytes.length == 0) throw invalid("Empty files cannot be uploaded.");
        if (bytes.length > MAX_FILE_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each file must be 10 MiB or smaller.");
        String name = safeName(originalName);
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("pdf", "docx", "txt").contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Supported file types: PDF, DOCX, and UTF-8 TXT.");
        }
        try {
            String text = switch (extension) {
                case "pdf" -> pdf(bytes);
                case "docx" -> docx(bytes);
                default -> utf8(bytes);
            };
            if (text.length() > MAX_TEXT_CHARS) throw invalid("Extracted text exceeds the 1,000,000 character limit.");
            if (text.isBlank()) throw invalid("No readable text was found. Scanned documents need OCR before uploading.");
            return new Parsed(name, switch (extension) {
                case "pdf" -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                default -> "text/plain";
            }, text.strip());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ResponseStatusException status) throw status;
            throw invalid("The file is corrupted, encrypted, or unreadable. Upload a readable PDF, DOCX, or UTF-8 TXT file.");
        }
    }

    private String pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                throw invalid("Encrypted PDFs are not supported. Upload an unprotected copy.");
            }
            if (document.getNumberOfPages() > 500) throw invalid("PDFs must contain no more than 500 pages.");
            // Bound extracted output as it is written, rather than after allocating it all.
            StringBuilder content = new StringBuilder();
            Writer text = new Writer() {
                @Override public void write(char[] value, int offset, int length) {
                    if (content.length() + length > MAX_TEXT_CHARS) throw invalid("Extracted text is too large.");
                    content.append(value, offset, length);
                }
                @Override public void flush() { }
                @Override public void close() { }
            };
            new PDFTextStripper().writeText(document, text);
            return content.toString();
        }
    }

    private String docx(byte[] bytes) throws IOException {
        // Bound total expanded ZIP data before the document library allocates it.
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            int entries = 0;
            long expanded = 0;
            byte[] chunk = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 1000) throw invalid("DOCX contains too many archive entries.");
                int read;
                while ((read = zip.read(chunk)) != -1) {
                    expanded += read;
                    if (expanded > 20L * 1024 * 1024) throw invalid("DOCX expanded data exceeds 20 MiB.");
                }
            }
        }
        // POI validates OOXML structure and protects against excessive ZIP expansion.
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
             var extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String utf8(byte[] bytes) throws CharacterCodingException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw invalid("TXT files must contain readable UTF-8 text, not binary data.");
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private String safeName(String original) {
        if (original == null) throw invalid("A file name is required.");
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Use a file name between 1 and 255 characters without control characters.");
        }
        return name;
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
