package com.realteeth.image_processing.service;

import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.realteeth.image_processing.client.ImageWorkerClient;
import com.realteeth.image_processing.client.dto.ProcessStartResponse;
import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.event.JobCreatedEvent;
import com.realteeth.image_processing.repository.JobRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JobEventListener {

    private final JobRepository jobRepository;
    private final ImageWorkerClient imageWorkerClient;
    private final Executor executor;

    public JobEventListener(
            JobRepository jobRepository,
            ImageWorkerClient imageWorkerClient,
            @Qualifier("jobProcessingExecutor") Executor executor) {
        this.jobRepository = jobRepository;
        this.imageWorkerClient = imageWorkerClient;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("jobProcessingExecutor")
    public void onJobCreated(JobCreatedEvent event) {
        registerToWorker(event.jobId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingJobs() {
        jobRepository.findByStatus(JobStatus.PENDING)
                     .forEach(job -> executor.execute(() -> registerToWorker(job.getJobId())));
    }

    private void registerToWorker(UUID jobId) {
        Job job = jobRepository.findByJobId(jobId)
                               .orElseThrow(() -> new IllegalStateException("Job 미발견: " + jobId));

        if (!job.isPending()) {
            return;
        }

        try {
            ProcessStartResponse startResponse = imageWorkerClient.startProcessing(job.getImageUrl());
            job.startProcessing(startResponse.jobId());
            jobRepository.save(job);
            log.info("Worker 등록 완료: jobId={}, workerJobId={}", jobId, startResponse.jobId());
        } catch (Exception e) {
            log.error("Worker 등록 실패: jobId={}", jobId, e);
            job.fail("처리 시작 실패: " + e.getMessage());
            jobRepository.save(job);
        }
    }
}
