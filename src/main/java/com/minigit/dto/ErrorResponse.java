package com.minigit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * 错误响应DTO
 */
public class ErrorResponse {

    private String error;
    private String message;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant timestamp;

    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String error, String message) {
        this();
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}