package com.realteeth.image_processing.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "image-worker")
@Validated
public record ImageWorkerProperties(
        @NotBlank String baseUrl,
        @NotBlank String candidateName,
        @NotBlank @Email String email
) {
}
