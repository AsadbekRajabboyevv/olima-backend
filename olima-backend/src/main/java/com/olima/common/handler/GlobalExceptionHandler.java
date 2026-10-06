package com.olima.common.handler;

import com.olima.common.error.BusinessException;
import com.olima.common.error.ErrorCode;
import com.olima.common.error.ProblemResponses;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final ProblemResponses problems;

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
    ErrorCode code = ex.code();
    log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
    if (ex.retryAfter() != null) {
      builder.header(
          HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())));
    }
    return builder.body(problems.build(code, ex.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    log.warn("Uploaded file exceeds the allowed size: {}", ex.getMessage());
    return respond(
        ErrorCode.PAYLOAD_TOO_LARGE, "Yuklangan fayl hajmi ruxsat etilgan limitdan oshib ketdi");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    return respond(ErrorCode.FORBIDDEN, "You do not have permission to perform this action");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Illegal argument: {}", ex.getMessage());
    return respond(ErrorCode.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
    log.warn("Conflict: {}", ex.getMessage());
    return respond(ErrorCode.INVALID_STATE, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    String fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .distinct()
            .collect(Collectors.joining("; "));
    log.warn("Validation error: {}", fields);
    return respond(
        ErrorCode.VALIDATION_FAILED,
        fields.isBlank() ? "Validatsiya xatoligi yuz berdi" : "Validatsiya xatoligi — " + fields);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex) {
    String details =
        ex.getAllErrors().stream()
            .map(e -> e.getDefaultMessage())
            .distinct()
            .collect(Collectors.joining("; "));
    log.warn("Parameter validation error: {}", details);
    return respond(ErrorCode.VALIDATION_FAILED, "Validatsiya xatoligi — " + details);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return respond(
        ErrorCode.DATA_INTEGRITY_VIOLATION,
        "Ma'lumot bazadagi cheklovga zid: bunday nom allaqachon mavjud yoki bog'liq yozuvlar bor");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict: {}", ex.getMessage());
    return respond(
        ErrorCode.INVALID_STATE,
        "Ma'lumot boshqa jarayon tomonidan o'zgartirilgan yoki o'chirilgan");
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MissingServletRequestPartException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
    log.warn("Bad request: {}", ex.getMessage());
    return respond(
        ErrorCode.BAD_REQUEST,
        "So'rov noto'g'ri tuzilgan: parametrlar yoki JSON formatini tekshiring");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            problems.build(ErrorCode.BAD_REQUEST, HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
    return respond(ErrorCode.NOT_FOUND, "Resource not found");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
    String ref = UUID.randomUUID().toString().substring(0, 8);
    log.error("Error {} {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
    ProblemDetail problem =
        problems.build(
            ErrorCode.INTERNAL_ERROR,
            "Ichki xatolik yuz berdi. Qayta urinib ko'ring (kod: " + ref + ")");
    problem.setProperty("ref", ref);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }

  private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail) {
    return ResponseEntity.status(code.status()).body(problems.build(code, detail));
  }
}
