package com.realteeth.image_processing.service.dto;

import java.time.Instant;
import java.util.UUID;

import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.domain.ProcessingContext;

public record JobResponse(
        UUID jobId,
        String idempotencyKey,
        String userId,
        String imageUrl,
        JobStatus status,
        String workerJobId,
        String result,
        String errorMessage,
        Instant createdAt
) {

    public static JobResponse from(Job job) {
        ProcessingContext ctx = job.getProcessingContext();
        return new JobResponse(
                job.getJobId(),
                job.getIdempotencyKey(),
                job.getUserId(),
                job.getImageUrl(),
                job.getStatus(),
                ctx != null ? ctx.getWorkerJobId() : null,
                ctx != null ? ctx.getResult() : null,
                ctx != null ? ctx.getErrorMessage() : null,
                job.getCreatedAt()
        );
    }
}
