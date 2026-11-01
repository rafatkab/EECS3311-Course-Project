package com.eecs3311.backend.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCourseRequest(
        @NotBlank(message = "Course name is required.") @Size(max = 120, message = "Use 120 characters or fewer.") String name,
        @NotBlank(message = "Description is required.") @Size(max = 2000, message = "Use 2000 characters or fewer.") String description,
        @NotNull(message = "Start date is required.") LocalDate startDate,
        @NotNull(message = "End date is required.") LocalDate endDate) { }
