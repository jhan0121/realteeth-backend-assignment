package com.realteeth.image_processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import com.realteeth.image_processing.client.dto.ProcessStartResponse;
import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.event.JobCreatedEvent;
import com.realteeth.image_processing.repository.JobRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobEventListener")
class JobEventListenerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ImageWorkerClient imageWorkerClient;

    private final Executor syncExecutor = Runnable::run;

    private JobEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new JobEventListener(jobRepository, imageWorkerClient, syncExecutor);
    }

    private Job pendingJob() {
        return Job.createPending("key-001", "https://example.com/image.jpg", "user-1");
    }

    @Nested
    @DisplayName("onJobCreated() - Worker 등록 정상 흐름")
    class OnJobCreatedSuccess {

        @Test
        @DisplayName("PENDING Job이면 Worker 등록 후 상태가 PROCESSING이 된다")
        void onJobCreated_pendingJob_startsProcessing() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(1);
            when(jobRepository.findByJobId(job.getJobId())).thenReturn(Optional.of(job));
            when(imageWorkerClient.startProcessing(anyString()))
                    .thenReturn(new ProcessStartResponse("worker-job-1", "PROCESSING"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.PROCESSING);
        }

        @Test
        @DisplayName("Worker 등록 성공 시 processingContext에 workerJobId가 저장된다")
        void onJobCreated_pendingJob_setsWorkerJobId() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(1);
            when(jobRepository.findByJobId(job.getJobId())).thenReturn(Optional.of(job));
            when(imageWorkerClient.startProcessing(anyString()))
                    .thenReturn(new ProcessStartResponse("worker-job-1", "PROCESSING"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessingContext().getWorkerJobId()).isEqualTo("worker-job-1");
        }

        @Test
        @DisplayName("PENDING Job이면 Worker 등록을 건너뛰지 않는다")
        void onJobCreated_pendingJob_doesNotSkipRegistration() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(1);
            when(jobRepository.findByJobId(job.getJobId())).thenReturn(Optional.of(job));
            when(imageWorkerClient.startProcessing(anyString()))
                    .thenReturn(new ProcessStartResponse("worker-job-1", "PROCESSING"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            verify(imageWorkerClient).startProcessing(job.getImageUrl());
        }

        @Test
        @DisplayName("PENDING이 아닌 Job이면 tryClaimJob이 0을 반환하고 Worker 등록을 건너뛴다")
        void onJobCreated_nonPendingJob_skipsRegistration() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(0);

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            verify(imageWorkerClient, never()).startProcessing(anyString());
            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("onJobCreated() - Worker 등록 실패")
    class OnJobCreatedFailure {

        @Test
        @DisplayName("startProcessing 예외 발생 시 Job 상태가 FAILED가 된다")
        void onJobCreated_startProcessingThrows_jobMarkedFailed() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(1);
            when(jobRepository.findByJobId(job.getJobId())).thenReturn(Optional.of(job));
            when(imageWorkerClient.startProcessing(anyString()))
                    .thenThrow(new RuntimeException("Worker 연결 실패"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("startProcessing 예외 발생 시 errorMessage가 저장된다")
        void onJobCreated_startProcessingThrows_setsErrorMessage() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(1);
            when(jobRepository.findByJobId(job.getJobId())).thenReturn(Optional.of(job));
            when(imageWorkerClient.startProcessing(anyString()))
                    .thenThrow(new RuntimeException("Worker 연결 실패"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
            verify(jobRepository).save(captor.capture());
            assertThat(captor.getValue().getProcessingContext().getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("선점 후 Job을 찾을 수 없으면 IllegalStateException이 발생한다")
        void onJobCreated_jobNotFound_throwsIllegalStateException() {
            UUID unknownId = UUID.randomUUID();
            when(jobRepository.tryClaimJob(unknownId)).thenReturn(1);
            when(jobRepository.findByJobId(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> listener.onJobCreated(new JobCreatedEvent(unknownId)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("onJobCreated() - 동시 이중 등록 방지")
    class ConcurrentRegistrationPrevention {

        @Test
        @DisplayName("tryClaimJob이 0을 반환하면 Worker HTTP 호출 없이 즉시 반환한다")
        void onJobCreated_claimFails_noWorkerCall() {
            Job job = pendingJob();
            when(jobRepository.tryClaimJob(job.getJobId())).thenReturn(0);

            listener.onJobCreated(new JobCreatedEvent(job.getJobId()));

            verify(jobRepository, never()).findByJobId(any());
            verify(imageWorkerClient, never()).startProcessing(anyString());
            verify(jobRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("recoverPendingJobs() - 서버 재시작 시 PENDING Job 복구")
    class RecoverPendingJobs {

        @Test
        @DisplayName("PENDING Job 2개가 있으면 Worker에 2번 등록한다")
        void recoverPendingJobs_pendingJobsExist_registersAllToWorker() {
            Job job1 = Job.createPending("key-001", "https://example.com/1.jpg", "user-1");
            Job job2 = Job.createPending("key-002", "https://example.com/2.jpg", "user-2");
            when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of(job1, job2));
            when(jobRepository.tryClaimJob(job1.getJobId())).thenReturn(1);
            when(jobRepository.tryClaimJob(job2.getJobId())).thenReturn(1);
            when(jobRepository.findByJobId(job1.getJobId())).thenReturn(Optional.of(job1));
            when(jobRepository.findByJobId(job2.getJobId())).thenReturn(Optional.of(job2));
            when(imageWorkerClient.startProcessing(anyString()))
                    .thenReturn(new ProcessStartResponse("worker-job-x", "PROCESSING"));
            when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            listener.recoverPendingJobs();

            verify(imageWorkerClient, times(2)).startProcessing(anyString());
        }

        @Test
        @DisplayName("PENDING Job이 없으면 Worker 호출이 없다")
        void recoverPendingJobs_noPendingJobs_noWorkerCalls() {
            when(jobRepository.findByStatus(JobStatus.PENDING)).thenReturn(List.of());

            listener.recoverPendingJobs();

            verify(imageWorkerClient, never()).startProcessing(anyString());
        }
    }
}
