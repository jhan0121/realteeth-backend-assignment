package com.realteeth.image_processing.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "jobs")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(unique = true, nullable = false)
    private UUID jobId;

    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    private String userId;

    @Column(length = 2048, nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Embedded
    @Builder.Default
    private ProcessingContext processingContext = ProcessingContext.empty();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static Job createPending(String idempotencyKey, String imageUrl, String userId) {
        return Job.builder()
                  .jobId(UUID.randomUUID())
                  .idempotencyKey(idempotencyKey)
                  .imageUrl(imageUrl)
                  .userId(userId)
                  .status(JobStatus.PENDING)
                  .build();
    }

    public void startProcessing(String workerJobId) {
        rejectIfAlreadyTerminated(JobStatus.PROCESSING);
        rejectIfAlreadyProcessing();
        this.status = JobStatus.PROCESSING;
        this.processingContext = ProcessingContext.processing(workerJobId);
    }

    public void complete(String result) {
        rejectIfAlreadyTerminated(JobStatus.COMPLETED);
        rejectIfStillPending();
        this.status = JobStatus.COMPLETED;
        this.processingContext = ProcessingContext.completed(processingContext.getWorkerJobId(), result);
    }

    public void fail(String errorMessage) {
        rejectIfAlreadyTerminated(JobStatus.FAILED);
        this.status = JobStatus.FAILED;
        String currentWorkerJobId = processingContext != null ? processingContext.getWorkerJobId() : null;
        this.processingContext = ProcessingContext.failed(currentWorkerJobId, errorMessage);
    }

    public boolean isPending() {
        return this.status == JobStatus.PENDING;
    }

    private void rejectIfAlreadyTerminated(JobStatus target) {
        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            throw new IllegalStateException(
                    "종료된 상태(%s)에서 %s로 전이할 수 없습니다.".formatted(status, target));
        }
    }

    private void rejectIfAlreadyProcessing() {
        if (status == JobStatus.PROCESSING) {
            throw new IllegalStateException("이미 PROCESSING인 Job입니다.");
        }
    }

    private void rejectIfStillPending() {
        if (status == JobStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서 COMPLETED로 전이할 수 없습니다.");
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
