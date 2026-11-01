package com.eecs3311.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CourseApiFactory {
    private final String baseUrl;
    private final ObjectMapper mapper;
    public CourseApiFactory(@Value("${study.backend-url}") String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.mapper = mapper;
    }
    /** Creates a separate HTTP session for each signed-in view. */
    public CourseApi connect(String username, String password) {
        return new CourseApi(baseUrl, username, password, mapper);
    }
}
