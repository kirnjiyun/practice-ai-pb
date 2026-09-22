package com.aipb.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> staleEdit() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Asset changed; reload before editing"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Account already exists or data conflicts"));
    }
}
