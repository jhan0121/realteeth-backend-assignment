package com.realteeth.image_processing.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.realteeth.image_processing.client.ImageWorkerClient;
import com.realteeth.image_processing.client.dto.ProcessStatusResponse;
import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.exception.WorkerJobLostException;
import com.realteeth.image_processing.repository.JobRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JobPollingScheduler {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final JobRepository jobRepository;
    private final ImageWorkerClient imageWorkerClient;
    private final Executor executor;

    public JobPollingScheduler(
            JobRepository jobRepository,
            ImageWorkerClient imageWorkerClient,
            @Qualifier("jobProcessingExecutor") Executor executor) {
        this.jobRepository = jobRepository;
        this.imageWorkerClient = imageWorkerClient;
        this.executor = executor;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollProcessingJobs() {
        jobRepository.findByStatus(JobStatus.PROCESSING)
                     .forEach(job -> executor.execute(() -> {
                         try {
                             checkJobStatus(job);
                         } catch (ObjectOptimisticLockingFailureException e) {
                             log.warn("낙관적 락 충돌 - 다른 스레드가 먼저 처리 완료: jobId={}", job.getJobId());
                         } catch (Exception e) {
                             log.error("폴링 중 예외 발생: jobId={}", job.getJobId(), e);
                         }
                     }));
    }

    private void checkJobStatus(Job job) {
        Instant checkBoundaryTime = job.getUpdatedAt().plus(TIMEOUT);
        if (checkBoundaryTime.isBefore(Instant.now())) {
            job.fail("이미지 처리 서버 응답 시간 초과");
            jobRepository.save(job);
            log.warn("Job 타임아웃: jobId={}", job.getJobId());
            return;
        }

        String workerJobId = job.getProcessingContext().getWorkerJobId();
        ProcessStatusResponse statusResponse;
        try {
            statusResponse = imageWorkerClient.pollStatus(workerJobId);
        } catch (WorkerJobLostException e) {
            log.error("Worker Job 유실: workerJobId={}", workerJobId);
            job.fail("Worker Job 유실: " + workerJobId);
            jobRepository.save(job);
            return;
        } catch (Exception e) {
            log.warn("상태 조회 일시 실패, 다음 폴링 때 재시도: jobId={}", job.getJobId());
            return;
        }

        if (STATUS_COMPLETED.equals(statusResponse.status())) {
            job.complete(statusResponse.result());
            jobRepository.save(job);
            log.info("Job 완료: jobId={}", job.getJobId());
        } else if (STATUS_FAILED.equals(statusResponse.status())) {
            String errorMessage = statusResponse.result() != null
                                  ? statusResponse.result()
                                  : "Worker 처리 실패";
            job.fail(errorMessage);
            jobRepository.save(job);
            log.warn("Job 실패: jobId={}", job.getJobId());
        }
    }
}
