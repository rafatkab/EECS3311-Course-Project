package com.eecs3311.course;
import java.time.LocalDate;

public record CreateCourseRequest(String name, String description, LocalDate startDate, LocalDate endDate) { }
