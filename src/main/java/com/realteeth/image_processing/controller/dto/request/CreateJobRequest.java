package com.realteeth.image_processing.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
        @NotBlank(message = "imageUrl은 필수입니다") String imageUrl,
        @NotBlank(message = "userId는 필수입니다") String userId
) {}
