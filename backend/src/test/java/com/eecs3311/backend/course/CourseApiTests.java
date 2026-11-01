package com.eecs3311.backend.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() { jdbc.update("DELETE FROM courses"); }

    private String body(String name, String description, String start, String end) throws Exception {
        return mapper.writeValueAsString(Map.of("name", name, "description", description, "startDate", start, "endDate", end));
    }

    @Test void createsTrimsPersistsAndListsOwnedCourse() throws Exception {
        var result = mvc.perform(post("/api/courses").with(httpBasic("test-student", "test-only-password")).with(csrf())
                        .contentType("application/json").content(body("  EECS 3311  ", "  Software design  ", "2026-09-01", "2026-12-20")))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("EECS 3311"))
                .andExpect(jsonPath("$.description").value("Software design")).andReturn();
        String location = result.getResponse().getHeader("Location");
        mvc.perform(get(location).with(user("test-student"))).andExpect(status().isOk());
        mvc.perform(get("/api/courses").with(user("test-student")))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("EECS 3311"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM courses", Integer.class)).isEqualTo(1);
    }

    @Test void rejectsMissingBlankAndOversizedFieldsWithoutSaving() throws Exception {
        for (String content : new String[]{"{}", body(" ", "desc", "2026-09-01", "2026-12-20"),
                body("Course", "\t", "2026-09-01", "2026-12-20"),
                body("a".repeat(121), "desc", "2026-09-01", "2026-12-20"),
                body("Course", "a".repeat(2001), "2026-09-01", "2026-12-20")}) {
            mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json").content(content))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM courses", Integer.class)).isZero();
    }

    @Test void rejectsReversedDatesAndMalformedDatesButAllowsOneDayCourse() throws Exception {
        for (String content : new String[]{body("Course", "desc", "2026-12-20", "2026-09-01"),
                body("Course", "desc", "2026-02-30", "2026-12-20"), "not json"}) {
            mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json").content(content))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content(body("Course", "desc", "2026-12-20", "2026-12-20"))).andExpect(status().isCreated());
    }

    @Test void rejectsUnauthenticatedWrongPasswordAndMissingCsrf() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses").with(httpBasic("test-student", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/courses").with(csrf()).contentType("application/json")
                .content(body("Course", "desc", "2026-09-01", "2026-12-20"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/courses").with(user("alice")).contentType("application/json")
                .content(body("Course", "desc", "2026-09-01", "2026-12-20"))).andExpect(status().isForbidden());
    }

    @Test void studentsCannotListOrReadAnotherStudentsCourse() throws Exception {
        var result = mvc.perform(post("/api/courses").with(user("alice")).with(csrf()).contentType("application/json")
                .content(body("Private course", "desc", "2026-09-01", "2026-12-20")))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(get("/api/courses").with(user("bob"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(result.getResponse().getHeader("Location")).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/not-a-uuid").with(user("alice"))).andExpect(status().isBadRequest());
    }
}
