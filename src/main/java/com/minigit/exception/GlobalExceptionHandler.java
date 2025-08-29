package com.minigit.exception;

import com.minigit.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 处理参数缺失异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        logger.warn("Missing required parameter: {}", e.getParameterName());
        
        String message = getMessage("validation.name.required");
        ErrorResponse error = new ErrorResponse("MISSING_PARAMETER", message);
        
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Illegal argument: {}", e.getMessage());
        
        String message = getMessage("repo.invalid");
        ErrorResponse error = new ErrorResponse("INVALID_ARGUMENT", message);
        
        return ResponseEntity.badRequest().body(error);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler({BadCredentialsException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAuthException(Exception e) {
        logger.warn("Authentication/Authorization failed: {}", e.getMessage());
        
        String message = getMessage("auth.required");
        ErrorResponse error = new ErrorResponse("AUTH_REQUIRED", message);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * 处理通用异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unexpected error occurred", e);
        
        String message = getMessage("internal.error");
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", message);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * 获取国际化消息
     */
    private String getMessage(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }
}