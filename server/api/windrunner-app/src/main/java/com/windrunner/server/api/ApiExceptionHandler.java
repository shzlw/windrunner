package com.windrunner.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException exception,
                                                                           HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus responseStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? responseStatus.getReasonPhrase()
                : exception.getReason();

        return ResponseEntity
                .status(responseStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(
                        ApiError.of(errorCode(responseStatus), message),
                        ApiMeta.request(requestId(request))
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception,
                                                                           HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(
                        ApiError.of("PAYLOAD_TOO_LARGE", "The uploaded file is too large."),
                        ApiMeta.request(requestId(request))
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException exception,
                                                                            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(
                        ApiError.of("NOT_FOUND", "Resource not found."),
                        ApiMeta.request(requestId(request))
                ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception,
                                                               HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(
                        ApiError.of("BAD_REQUEST", "Request is invalid."),
                        ApiMeta.request(requestId(request))
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception, HttpServletRequest request) {
        if (DisconnectedClientHelper.isClientDisconnectedException(exception)
                || (exception instanceof AsyncRequestTimeoutException
                && request.getRequestURI().endsWith("/internal-api/v1/notifications/stream"))) {
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled API error: method={} path={} requestId={}",
                request.getMethod(), request.getRequestURI(), requestId(request), exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(
                        ApiError.of("INTERNAL_SERVER_ERROR", "An unexpected server error occurred."),
                        ApiMeta.request(requestId(request))
                ));
    }

    private String errorCode(HttpStatus status) {
        return status.name();
    }

    private String requestId(HttpServletRequest request) {
        String headerValue = request.getHeader("x-request-id");
        return headerValue == null || headerValue.isBlank() ? UUID.randomUUID().toString() : headerValue;
    }
}
