package com.realteeth.image_processing.exception;

public class RateLimitException extends RuntimeException {

    public RateLimitException() {
        super("Worker API 요청 한도 초과 (429)");
    }
}
