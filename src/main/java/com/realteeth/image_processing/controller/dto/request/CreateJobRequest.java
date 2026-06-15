package com.realteeth.image_processing.controller.dto.request;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
        @NotBlank(message = "imageUrl은 필수입니다")
        @URL(message = "유효한 url 형식이 아닙니다")
        String imageUrl,
        
        @NotBlank(message = "userId는 필수입니다") String userId
) {}
