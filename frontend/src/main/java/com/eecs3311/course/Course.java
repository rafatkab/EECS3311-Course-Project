package com.eecs3311.course;
import java.time.LocalDate;
import java.util.UUID;

public record Course(UUID courseId, String name, String description, LocalDate startDate, LocalDate endDate) { }
