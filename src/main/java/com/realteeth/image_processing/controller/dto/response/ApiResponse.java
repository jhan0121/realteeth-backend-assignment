package com.realteeth.image_processing.controller.dto.response;

import java.util.List;

public record ApiResponse<T>(boolean success, T data, String error, List<String> details) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> errors(String summary, List<String> details) {
        return new ApiResponse<>(false, null, summary, details);
    }
}
