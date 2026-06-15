package com.realteeth.image_processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.realteeth.image_processing.client.ImageWorkerClient;
import com.realteeth.image_processing.client.dto.ProcessStatusResponse;
import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.exception.WorkerJobLostException;
import com.realteeth.image_processing.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobPollingScheduler")
class JobPollingSchedulerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ImageWorkerClient imageWorkerClient;

    private final Executor syncExecutor = Runnable::run;

    private JobPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new JobPollingScheduler(jobRepository, imageWorkerClient, syncExecutor);
    }

    private Job processingJob() {
        Job job = Job.createPending("key-001", "https://example.com/image.jpg", "user-1");
        callOnCreate(job);
        job.startProcessing("worker-job-1");
        return job;
    }

    private void callOnCreate(Job job) {
        try {
            Method method = Job.class.getDeclaredMethod("onCreate");
            method.setAccessible(true);
            method.invoke(job);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setUpdatedAt(Job job, Instant instant) {
        try {
            Field field = Job.class.getDeclaredField("updatedAt");
            field.setAccessible(true);
            field.set(job, instant);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("pollProcessingJobs() - PROCESSING Job 없음")
    class NoProcessingJobs {

        @Test
        @DisplayName("PROCESSING 상태 Job이 없으면 Worker 폴링을 하지 않는다")
        void pollProcessingJobs_noProcessingJobs_noWorkerCalls() {
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of());

            scheduler.pollProcessingJobs();

            verify(imageWorkerClient, never()).pollStatus(anyString());
        }
    }

    @Nested
    @DisplayName("pollProcessingJobs() - 타임아웃 처리")
    class TimeoutHandling {

        @Test
        @DisplayName("updatedAt이 5분을 초과하고 Worker가 PROCESSING이면 FAILED 상태가 된다")
        void pollProcessingJobs_timedOutJob_statusBecomeFailed() {
            Job job = processingJob();
            setUpdatedAt(job, Instant.now().minus(6, ChronoUnit.MINUTES));
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "PROCESSING", null));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("타임아웃 시 errorMessage에 '시간 초과' 문구가 포함된다")
        void pollProcessingJobs_timedOutJob_errorMessageContainsTimeoutText() {
            Job job = processingJob();
            setUpdatedAt(job, Instant.now().minus(6, ChronoUnit.MINUTES));
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "PROCESSING", null));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessingContext().getErrorMessage()).contains("시간 초과");
        }

        @Test
        @DisplayName("Worker가 COMPLETED를 반환하면 타임아웃이 지나도 COMPLETED 상태가 된다")
        void pollProcessingJobs_completedResponseAfterTimeout_jobCompleted() {
            Job job = processingJob();
            setUpdatedAt(job, Instant.now().minus(6, ChronoUnit.MINUTES));
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "COMPLETED", "result-data"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("pollProcessingJobs() - Worker 상태 응답 처리")
    class WorkerStatusResponse {

        @Test
        @DisplayName("Worker가 COMPLETED를 반환하면 Job 상태가 COMPLETED가 된다")
        void pollProcessingJobs_completedResponse_jobCompleted() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "COMPLETED", "result-data"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("Worker가 COMPLETED를 반환하면 result가 저장된다")
        void pollProcessingJobs_completedResponse_resultSaved() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "COMPLETED", "result-data"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessingContext().getResult()).isEqualTo("result-data");
        }

        @Test
        @DisplayName("Worker가 FAILED를 반환하면 Job 상태가 FAILED가 된다")
        void pollProcessingJobs_failedResponse_jobFailed() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "FAILED", null));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("Worker가 FAILED를 반환하고 result가 있으면 result가 errorMessage로 저장된다")
        void pollProcessingJobs_failedResponseWithResult_resultUsedAsErrorMessage() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "FAILED", "이미지 용량 초과"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessingContext().getErrorMessage()).isEqualTo("이미지 용량 초과");
        }

        @Test
        @DisplayName("Worker가 FAILED를 반환하고 result가 null이면 기본 메시지가 errorMessage로 저장된다")
        void pollProcessingJobs_failedResponseWithNullResult_defaultErrorMessage() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "FAILED", null));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessingContext().getErrorMessage()).isEqualTo("Worker 처리 실패");
        }

        @Test
        @DisplayName("Worker가 PROCESSING을 반환하면 Job을 저장하지 않는다")
        void pollProcessingJobs_processingResponse_noSave() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "PROCESSING", null));

            scheduler.pollProcessingJobs();

            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("pollProcessingJobs() - WorkerJobLostException 처리")
    class WorkerJobLostHandling {

        @Test
        @DisplayName("WorkerJobLostException 발생 시 Job 상태가 FAILED가 된다")
        void pollProcessingJobs_workerJobLost_jobFailed() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenThrow(new WorkerJobLostException("worker-job-1"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            scheduler.pollProcessingJobs();

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("WorkerJobLostException이 발생해도 예외가 외부로 전파되지 않는다")
        void pollProcessingJobs_workerJobLost_exceptionNotPropagated() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenThrow(new WorkerJobLostException("worker-job-1"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> scheduler.pollProcessingJobs()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("pollProcessingJobs() - 일시적 오류 처리")
    class TransientErrorHandling {

        @Test
        @DisplayName("일시적 예외 발생 시 Job을 저장하지 않는다")
        void pollProcessingJobs_transientException_noSave() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenThrow(new RuntimeException("네트워크 일시 오류"));

            scheduler.pollProcessingJobs();

            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("pollProcessingJobs() - 낙관적 잠금 충돌 처리")
    class OptimisticLockingHandling {

        @Test
        @DisplayName("낙관적 잠금 충돌 시 예외가 외부로 전파되지 않는다")
        void pollProcessingJobs_optimisticLockingConflict_exceptionNotPropagated() {
            Job job = processingJob();
            when(jobRepository.findByStatus(JobStatus.PROCESSING)).thenReturn(List.of(job));
            when(imageWorkerClient.pollStatus("worker-job-1"))
                    .thenReturn(new ProcessStatusResponse("worker-job-1", "COMPLETED", "result"));
            when(jobRepository.save(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Job.class, job.getJobId()));

            assertThatCode(() -> scheduler.pollProcessingJobs()).doesNotThrowAnyException();
        }
    }
}
