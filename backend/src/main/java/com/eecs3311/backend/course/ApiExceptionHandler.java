package com.eecs3311.backend.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import com.eecs3311.backend.ai.AiFailure;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record ApiError(String message, Map<String, String> fields) { }

    @ExceptionHandler(AiFailure.class)
    ResponseEntity<ApiError> ai(AiFailure exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("Each file must be 10 MiB or smaller; select at most 10 files.", Map.of()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> missingFiles(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("Select at least one file in the files field.", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> fields(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("Check the highlighted course details.", fields));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception exception) {
        if (exception.getCause() instanceof com.fasterxml.jackson.databind.exc.ValueInstantiationException value
                && java.util.Set.of(com.eecs3311.backend.planning.PlanRequest.class, com.eecs3311.backend.planning.StudyProfile.Availability.class, com.eecs3311.backend.assessment.QuizRequest.class).contains(value.getType().getRawClass())
                && value.getCause() instanceof IllegalArgumentException invalid)
            return ResponseEntity.badRequest().body(new ApiError(invalid.getMessage(), Map.of()));
        return ResponseEntity.badRequest().body(new ApiError("Invalid request. Use ISO dates (YYYY-MM-DD) and valid course IDs.", Map.of()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> missing(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(exception.getReason(), Map.of()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> storage(DataAccessException exception) {
        LOG.error("Course storage operation failed", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("Data could not be saved or loaded. Please try again.", Map.of()));
    }
}
