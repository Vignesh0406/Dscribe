package in.dscribe.admissions;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(org.springframework.dao.DataAccessException.class)
  ResponseEntity<?> storageUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("message", "The database is temporarily unavailable. Please try again."));
  }

  public static class Invalid extends RuntimeException {
    final Map<String, String> errors;

    public Invalid(Map<String, String> errors) {
      super("Please correct the highlighted fields.");
      this.errors = errors;
    }
  }

  public static class Missing extends RuntimeException {}

  @ExceptionHandler(Invalid.class)
  ResponseEntity<?> invalid(Invalid e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage(), "errors", e.errors));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    Map<String, String> errors = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return invalid(new Invalid(errors));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                "Invalid request. Check field types, whole-number age, and ISO date format"
                    + " (YYYY-MM-DD)."));
  }

  @ExceptionHandler(Missing.class)
  ResponseEntity<?> missing() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("message", "Patient not found. Refresh the register."));
  }
}
