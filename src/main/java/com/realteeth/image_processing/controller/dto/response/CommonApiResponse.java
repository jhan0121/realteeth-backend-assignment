package com.realteeth.image_processing.controller.dto.response;

import java.util.List;

public record CommonApiResponse<T>(boolean success, T data, String error, List<String> details) {

    public static <T> CommonApiResponse<T> ok(T data) {
        return new CommonApiResponse<>(true, data, null, null);
    }

    public static <T> CommonApiResponse<T> error(String message) {
        return new CommonApiResponse<>(false, null, message, null);
    }

    public static <T> CommonApiResponse<T> errors(String summary, List<String> details) {
        return new CommonApiResponse<>(false, null, summary, details);
    }
}
