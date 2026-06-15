package com.realteeth.image_processing.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.realteeth.image_processing.controller.dto.response.CommonApiResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleJobNotFound(JobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                             .body(CommonApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                                 .map(e -> e.getField() + ": " + e.getDefaultMessage())
                                 .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(CommonApiResponse.errors("입력값이 유효하지 않습니다", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                                 .map(ConstraintViolation::getMessage)
                                 .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(CommonApiResponse.errors("입력값이 유효하지 않습니다", details));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(CommonApiResponse.error(ex.getHeaderName() + " 헤더가 필요합니다"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(CommonApiResponse.error("잘못된 " + ex.getName() + " 형식입니다: " + ex.getValue()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(CommonApiResponse.error("서버 오류가 발생했습니다"));
    }
}
