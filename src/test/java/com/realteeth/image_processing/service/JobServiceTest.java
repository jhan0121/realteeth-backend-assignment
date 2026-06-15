package com.realteeth.image_processing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import com.realteeth.image_processing.event.JobCreatedEvent;
import com.realteeth.image_processing.exception.JobNotFoundException;
import com.realteeth.image_processing.repository.JobRepository;
import com.realteeth.image_processing.service.dto.JobData;
import com.realteeth.image_processing.service.dto.SubmitJobResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService")
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, eventPublisher);
    }

    private Job pendingJob(String idempotencyKey) {
        return Job.createPending(idempotencyKey, "https://example.com/image.jpg", "user-1");
    }

    @Nested
    @DisplayName("submitJob() - 새 Job 등록")
    class SubmitNewJob {

        @BeforeEach
        void setUp() {
            when(jobRepository.findByIdempotencyKeyAndUserId("key-001", "user-1")).thenReturn(Optional.empty());
            when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("중복 없는 idempotencyKey이면 Job을 저장한다")
        void submitJob_newIdempotencyKey_savesJob() {
            jobService.submitJob("key-001", "https://example.com/image.jpg", "user-1");

            verify(jobRepository).save(any(Job.class));
        }

        @Test
        @DisplayName("중복 없는 idempotencyKey이면 JobCreatedEvent를 발행한다")
        void submitJob_newIdempotencyKey_publishesJobCreatedEvent() {
            jobService.submitJob("key-001", "https://example.com/image.jpg", "user-1");

            ArgumentCaptor<JobCreatedEvent> captor = ArgumentCaptor.forClass(JobCreatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().jobId()).isNotNull();
        }

        @Test
        @DisplayName("반환된 JobResponse의 status는 PENDING이고 created는 true이다")
        void submitJob_newIdempotencyKey_returnsPendingStatusAndCreatedTrue() {
            SubmitJobResult result = jobService.submitJob("key-001", "https://example.com/image.jpg", "user-1");

            assertSoftly(softly -> {
                softly.assertThat(result.job().status()).isEqualTo(JobStatus.PENDING);
                softly.assertThat(result.created()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("submitJob() - 중복 요청 처리")
    class SubmitDuplicateJob {

        @Test
        @DisplayName("중복 idempotencyKey이면 기존 Job의 jobId를 반환하고 created는 false이다")
        void submitJob_duplicateIdempotencyKey_returnsExistingJobIdAndCreatedFalse() {
            Job existing = pendingJob("key-001");
            when(jobRepository.findByIdempotencyKeyAndUserId("key-001", "user-1")).thenReturn(Optional.of(existing));

            SubmitJobResult result = jobService.submitJob("key-001", "https://example.com/image.jpg", "user-1");

            assertSoftly(softly -> {
                softly.assertThat(result.job().jobId()).isEqualTo(existing.getJobId());
                softly.assertThat(result.created()).isFalse();
            });
        }

        @Test
        @DisplayName("중복 idempotencyKey이면 새 Job을 저장하지 않는다")
        void submitJob_duplicateIdempotencyKey_doesNotSaveNewJob() {
            Job existing = pendingJob("key-001");
            when(jobRepository.findByIdempotencyKeyAndUserId("key-001", "user-1")).thenReturn(Optional.of(existing));

            jobService.submitJob("key-001", "https://example.com/image.jpg", "user-1");

            verify(jobRepository, never()).save(any());
        }

        @Test
        @DisplayName("중복 idempotencyKey이면 이벤트를 발행하지 않는다")
        void submitJob_duplicateIdempotencyKey_doesNotPublishEvent() {
            Job existing = pendingJob("key-001");
            when(jobRepository.findByIdempotencyKeyAndUserId("key-001", "user-1")).thenReturn(Optional.of(existing));

            jobService.submitJob("key-001", "https://example.com/image.jpg", "user-1");

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("listJobs() - Job 목록 조회")
    class ListJobs {

        @Test
        @DisplayName("Job이 존재하면 Page<JobData>를 반환한다")
        void listJobs_withJobs_returnsPage() {
            Job job1 = pendingJob("key-001");
            Job job2 = pendingJob("key-002");
            PageRequest pageable = PageRequest.of(0, 10);
            when(jobRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(job1, job2), pageable, 2));

            Page<JobData> page = jobService.listJobs(pageable);

            assertSoftly(softly -> {
                softly.assertThat(page.getTotalElements()).isEqualTo(2);
                softly.assertThat(page.getContent()).hasSize(2);
            });
        }

        @Test
        @DisplayName("Job이 없으면 빈 Page를 반환한다")
        void listJobs_noJobs_returnsEmptyPage() {
            PageRequest pageable = PageRequest.of(0, 10);
            when(jobRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<JobData> page = jobService.listJobs(pageable);

            assertSoftly(softly -> {
                softly.assertThat(page.getTotalElements()).isZero();
                softly.assertThat(page.getContent()).isEmpty();
            });
        }
    }

    @Nested
    @DisplayName("getJob() - Job 조회")
    class GetJob {

        @Test
        @DisplayName("존재하는 jobId이면 JobData를 반환한다")
        void getJob_existingJobId_returnsJobData() {
            Job job = pendingJob("key-001");
            when(jobRepository.findByJobId(job.getJobId())).thenReturn(Optional.of(job));

            JobData response = jobService.getJob(job.getJobId());

            assertThat(response.jobId()).isEqualTo(job.getJobId());
        }

        @Test
        @DisplayName("존재하지 않는 jobId이면 JobNotFoundException이 발생한다")
        void getJob_unknownJobId_throwsJobNotFoundException() {
            UUID unknownId = UUID.randomUUID();
            when(jobRepository.findByJobId(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> jobService.getJob(unknownId))
                    .isInstanceOf(JobNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }
    }
}
