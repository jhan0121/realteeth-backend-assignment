package com.realteeth.image_processing.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.event.JobCreatedEvent;
import com.realteeth.image_processing.repository.JobRepository;
import com.realteeth.image_processing.service.dto.JobResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public JobResponse submitJob(String idempotencyKey, String imageUrl, String userId) {
        return jobRepository.findByIdempotencyKey(idempotencyKey)
                            .map(existing -> {
                                log.info("중복 요청 처리: idempotencyKey={}", idempotencyKey);
                                return JobResponse.from(existing);
                            })
                            .orElseGet(() -> {
                                Job job = Job.createPending(idempotencyKey, imageUrl, userId);
                                jobRepository.save(job);
                                eventPublisher.publishEvent(new JobCreatedEvent(job.getJobId()));
                                log.info("Job 등록 완료: jobId={}", job.getJobId());
                                return JobResponse.from(job);
                            });
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> listJobs(Pageable pageable) {
        return jobRepository.findAll(pageable).map(JobResponse::from);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId) {
        Job job = jobRepository.findByJobId(jobId)
                               .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        return JobResponse.from(job);
    }
}
