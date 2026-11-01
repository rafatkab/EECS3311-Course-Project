package com.eecs3311.backend.material;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MaterialApiTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private String courseId;
    private String path;

    @BeforeEach void createCourse() throws Exception {
        jdbc.update("DELETE FROM courses");
        var created = mvc.perform(post("/api/courses").with(user("alice")).with(csrf())
                .contentType("application/json").content("{\"name\":\"EECS 3311\",\"description\":\"Software design\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-12-20\"}"))
                .andExpect(status().isCreated()).andReturn();
        courseId = mapper.readTree(created.getResponse().getContentAsString()).path("courseId").asText();
        path = "/api/courses/" + courseId + "/materials";
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        // Deliberately untrusted content type: server must inspect and parse the bytes.
        return new MockMultipartFile("files", name, "application/octet-stream", bytes);
    }
    private byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    @Test void uploadListReadAndDownloadRetainRealContent() throws Exception {
        byte[] bytes = text("Lecture 1: Strategy and Observer patterns.\n");
        var upload = mvc.perform(multipart(path).file(file("../../notes.txt", bytes)).with(user("alice")).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].fileName").value("notes.txt"))
                .andExpect(jsonPath("$[0].fileType").value("text/plain"))
                .andExpect(jsonPath("$[0].sizeBytes").value(bytes.length))
                .andExpect(jsonPath("$[0].content").doesNotExist()).andReturn();
        String id = mapper.readTree(upload.getResponse().getContentAsString()).get(0).path("materialId").asText();
        mvc.perform(get(path).with(user("alice"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path + "/" + id + "/content").accept("text/plain", "application/json").with(user("alice")))
                .andExpect(content().string("Lecture 1: Strategy and Observer patterns."));
        mvc.perform(get(path + "/" + id + "/download").with(user("alice")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().exists("Content-Disposition")).andExpect(content().bytes(bytes));
        assertThat(jdbc.queryForObject("SELECT original_file FROM learning_materials", byte[].class)).isEqualTo(bytes);
    }

    @Test void acceptsRealPdfDocxAndMultipleFiles() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("PDF lecture about repositories");
                stream.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }
        byte[] docx;
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("DOCX lecture about factories");
            document.write(output);
            docx = output.toByteArray();
        }
        var result = mvc.perform(multipart(path).file(file("lecture.PDF", pdf)).file(file("tutorial.docx", docx))
                .with(user("alice")).with(csrf())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2)).andReturn();
        var materials = mapper.readTree(result.getResponse().getContentAsString());
        mvc.perform(get(path + "/" + materials.get(0).path("materialId").asText() + "/content").with(user("alice")))
                .andExpect(content().string("PDF lecture about repositories"));
        mvc.perform(get(path + "/" + materials.get(1).path("materialId").asText() + "/content").with(user("alice")))
                .andExpect(content().string("DOCX lecture about factories"));
    }

    @Test void rejectsUnsupportedEmptyCorruptAndBinaryFiles() throws Exception {
        mvc.perform(multipart(path).file(file("notes.exe", text("hello"))).with(user("alice")).with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
        for (var bad : new MockMultipartFile[]{file("empty.txt", new byte[0]), file("blank.txt", text("  \n")),
                file("fake.pdf", text("not a PDF")), file("fake.docx", text("not a DOCX")),
                file("binary.txt", new byte[]{0, 1, 2}), file("bad-utf8.txt", new byte[]{(byte)0xFF}),
                file("oversized-text.txt", text("a".repeat(MaterialParser.MAX_TEXT_CHARS + 1)))}) {
            mvc.perform(multipart(path).file(bad).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_materials", Integer.class)).isZero();
    }

    @Test void rejectsOversizedAndExcessiveFileCounts() throws Exception {
        mvc.perform(multipart(path).file(file("huge.txt", new byte[MaterialParser.MAX_FILE_BYTES + 1]))
                .with(user("alice")).with(csrf())).andExpect(status().isPayloadTooLarge());
        var request = multipart(path);
        for (int index = 0; index < 11; index++) request.file(file("note" + index + ".txt", text("lecture")));
        mvc.perform(request.with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart(path).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test void rollsBackWholeRequestWhenAnyFileIsInvalid() throws Exception {
        mvc.perform(multipart(path).file(file("valid.txt", text("good notes"))).file(file("bad.pdf", text("broken")))
                .with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_materials", Integer.class)).isZero();
    }

    @Test void rejectsUnauthenticatedAndUnownedUploadListReadAndDownload() throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path).file(file("note.txt", text("notes"))).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path).file(file("note.txt", text("notes"))).with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(file("note.txt", text("notes"))).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/courses/" + UUID.randomUUID() + "/materials").file(file("note.txt", text("notes")))
                .with(user("alice")).with(csrf())).andExpect(status().isNotFound());
        var created = mvc.perform(multipart(path).file(file("private.txt", text("Private lecture"))).with(user("alice")).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).get(0).path("materialId").asText();
        for (String operation : new String[]{"content", "download"}) {
            mvc.perform(get(path + "/" + id + "/" + operation).accept("text/plain", "application/json").with(user("bob"))).andExpect(status().isNotFound());
            mvc.perform(get(path + "/" + UUID.randomUUID() + "/" + operation).with(user("alice"))).andExpect(status().isNotFound());
        }
    }
}
