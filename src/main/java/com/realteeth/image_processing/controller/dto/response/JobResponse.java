package com.realteeth.image_processing.controller.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.service.dto.JobData;

public record JobResponse(
        UUID jobId,
        String idempotencyKey,
        String userId,
        String imageUrl,
        JobStatus status,
        String result,
        String errorMessage,
        Instant createdAt
) {

    public static JobResponse from(JobData data) {
        return new JobResponse(
                data.jobId(),
                data.idempotencyKey(),
                data.userId(),
                data.imageUrl(),
                data.status(),
                data.result(),
                data.errorMessage(),
                data.createdAt()
        );
    }
}
