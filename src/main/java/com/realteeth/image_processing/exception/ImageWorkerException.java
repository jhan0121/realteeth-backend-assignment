package com.realteeth.image_processing.exception;

public class ImageWorkerException extends RuntimeException {

    public ImageWorkerException(String message) {
        super(message);
    }

    public ImageWorkerException(String message, Throwable cause) {
        super(message, cause);
    }
}
