package com.unulearner.backend.content;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class BackendResponseEntity<T> {
    private HttpStatus status;
    private String message;
    private T data;

    // Default constructor
    public BackendResponseEntity() {
    }

    // Constructor for success with data
    public BackendResponseEntity(String message, HttpStatus status, T data) {
        this.message = message;
        this.status = status;
        this.data = data;
    }

    // Constructor for error or success without data
    public BackendResponseEntity(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    // Getters and setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus(HttpStatus status) {
        this.status = status;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ResponseEntity<BackendResponseEntity<T>> createResponseEntity() {
        return ResponseEntity.status(status).body(this);
    }
}
