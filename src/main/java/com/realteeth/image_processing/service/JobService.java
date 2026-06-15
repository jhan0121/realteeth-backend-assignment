package com.realteeth.image_processing.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.event.JobCreatedEvent;
import com.realteeth.image_processing.exception.JobNotFoundException;
import com.realteeth.image_processing.repository.JobRepository;
import com.realteeth.image_processing.service.dto.JobData;
import com.realteeth.image_processing.service.dto.SubmitJobResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SubmitJobResult submitJob(String idempotencyKey, String imageUrl, String userId) {
        return jobRepository.findByIdempotencyKey(idempotencyKey)
                            .map(existing -> {
                                log.info("중복 요청 처리: idempotencyKey={}", idempotencyKey);
                                return new SubmitJobResult(JobData.from(existing), false);
                            })
                            .orElseGet(() -> {
                                Job job = Job.createPending(idempotencyKey, imageUrl, userId);
                                jobRepository.save(job);
                                eventPublisher.publishEvent(new JobCreatedEvent(job.getJobId()));
                                log.info("Job 등록 완료: jobId={}", job.getJobId());
                                return new SubmitJobResult(JobData.from(job), true);
                            });
    }

    @Transactional(readOnly = true)
    public Page<JobData> listJobs(Pageable pageable) {
        return jobRepository.findAll(pageable).map(JobData::from);
    }

    @Transactional(readOnly = true)
    public JobData getJob(UUID jobId) {
        Job job = jobRepository.findByJobId(jobId)
                               .orElseThrow(() -> new JobNotFoundException(jobId));
        return JobData.from(job);
    }
}
