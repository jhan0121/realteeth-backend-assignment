package com.realteeth.image_processing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Job 엔티티")
class JobTest {

    private Job pendingJob() {
        return Job.createPending("key-001", "https://example.com/image.jpg", "user-1");
    }

    private Job processingJob() {
        Job job = pendingJob();
        job.startProcessing("worker-job-1");
        return job;
    }

    @Nested
    @DisplayName("createPending() - 새 Job 생성")
    class CreatePendingJob {

        @Test
        @DisplayName("초기 status는 PENDING이다")
        void createPending_validInputs_statusIsPending() {
            Job job = Job.createPending("key-001", "https://example.com/image.jpg", "user-1");

            assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        }

        @Test
        @DisplayName("jobId가 UUID로 자동 생성된다")
        void createPending_validInputs_jobIdIsGenerated() {
            Job job = Job.createPending("key-001", "https://example.com/image.jpg", "user-1");

            assertThat(job.getJobId()).isNotNull();
        }

        @Test
        @DisplayName("매 호출마다 고유한 jobId를 생성한다")
        void createPending_calledTwice_jobIdsAreUnique() {
            Job job1 = Job.createPending("key-001", "https://example.com/a.jpg", "user-1");
            Job job2 = Job.createPending("key-002", "https://example.com/b.jpg", "user-1");

            assertThat(job1.getJobId()).isNotEqualTo(job2.getJobId());
        }

        @Test
        @DisplayName("idempotencyKey, imageUrl, userId를 그대로 저장한다")
        void createPending_validInputs_storesAllFields() {
            Job job = Job.createPending("key-001", "https://example.com/image.jpg", "user-1");

            assertSoftly(softly -> {
                softly.assertThat(job.getIdempotencyKey()).isEqualTo("key-001");
                softly.assertThat(job.getImageUrl()).isEqualTo("https://example.com/image.jpg");
                softly.assertThat(job.getUserId()).isEqualTo("user-1");
            });
        }

        @Test
        @DisplayName("processingContext의 모든 필드가 null로 초기화된다")
        void createPending_validInputs_processingContextIsEmpty() {
            Job job = Job.createPending("key-001", "https://example.com/image.jpg", "user-1");

            assertSoftly(softly -> {
                softly.assertThat(job.getProcessingContext().getWorkerJobId()).isNull();
                softly.assertThat(job.getProcessingContext().getResult()).isNull();
                softly.assertThat(job.getProcessingContext().getErrorMessage()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("startProcessing() - PROCESSING 상태 전이")
    class StartProcessingJob {

        @Test
        @DisplayName("status가 PROCESSING으로 변경된다")
        void startProcessing_whenPending_transitionsToProcessing() {
            Job job = pendingJob();

            job.startProcessing("worker-job-1");

            assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        }

        @Test
        @DisplayName("processingContext에 workerJobId가 저장된다")
        void startProcessing_whenPending_setsWorkerJobId() {
            Job job = pendingJob();

            job.startProcessing("worker-job-1");

            assertThat(job.getProcessingContext().getWorkerJobId()).isEqualTo("worker-job-1");
        }

        @Test
        @DisplayName("processingContext의 result와 errorMessage는 null로 유지된다")
        void startProcessing_whenPending_resultAndErrorMessageAreNull() {
            Job job = pendingJob();

            job.startProcessing("worker-job-1");

            assertSoftly(softly -> {
                softly.assertThat(job.getProcessingContext().getResult()).isNull();
                softly.assertThat(job.getProcessingContext().getErrorMessage()).isNull();
            });
        }

        @Test
        @DisplayName("이미 COMPLETED 상태이면 IllegalStateException이 발생한다")
        void startProcessing_whenCompleted_throwsIllegalStateException() {
            Job job = processingJob();
            job.complete("결과 데이터");

            assertThatThrownBy(() -> job.startProcessing("worker-job-2"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("이미 FAILED 상태이면 IllegalStateException이 발생한다")
        void startProcessing_whenFailed_throwsIllegalStateException() {
            Job job = processingJob();
            job.fail("처리 실패");

            assertThatThrownBy(() -> job.startProcessing("worker-job-2"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FAILED");
        }
    }

    @Nested
    @DisplayName("complete() - COMPLETED 상태 전이")
    class CompleteJob {

        @Test
        @DisplayName("status가 COMPLETED로 변경된다")
        void complete_whenProcessing_transitionsToCompleted() {
            Job job = processingJob();

            job.complete("처리 결과");

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("processingContext에 result가 저장된다")
        void complete_whenProcessing_setsResult() {
            Job job = processingJob();

            job.complete("처리 결과");

            assertThat(job.getProcessingContext().getResult()).isEqualTo("처리 결과");
        }

        @Test
        @DisplayName("processingContext의 workerJobId가 유지된다")
        void complete_whenProcessing_preservesWorkerJobId() {
            Job job = pendingJob();
            job.startProcessing("worker-job-1");

            job.complete("처리 결과");

            assertThat(job.getProcessingContext().getWorkerJobId()).isEqualTo("worker-job-1");
        }

        @Test
        @DisplayName("processingContext의 errorMessage는 null이다")
        void complete_whenProcessing_errorMessageIsNull() {
            Job job = processingJob();

            job.complete("처리 결과");

            assertThat(job.getProcessingContext().getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("PENDING 상태에서 complete()를 호출하면 IllegalStateException이 발생한다")
        void complete_whenPending_throwsIllegalStateException() {
            Job job = pendingJob();

            assertThatThrownBy(() -> job.complete("처리 결과"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("이미 COMPLETED 상태이면 IllegalStateException이 발생한다")
        void complete_whenCompleted_throwsIllegalStateException() {
            Job job = processingJob();
            job.complete("처리 결과");

            assertThatThrownBy(() -> job.complete("또 다른 결과"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("이미 FAILED 상태이면 IllegalStateException이 발생한다")
        void complete_whenFailed_throwsIllegalStateException() {
            Job job = processingJob();
            job.fail("처리 실패");

            assertThatThrownBy(() -> job.complete("처리 결과"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FAILED");
        }
    }

    @Nested
    @DisplayName("fail() - FAILED 상태 전이")
    class FailJob {

        @Test
        @DisplayName("status가 FAILED로 변경된다")
        void fail_whenProcessing_transitionsToFailed() {
            Job job = processingJob();

            job.fail("처리 실패 메시지");

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        }

        @Test
        @DisplayName("processingContext에 errorMessage가 저장된다")
        void fail_whenProcessing_setsErrorMessage() {
            Job job = processingJob();

            job.fail("처리 실패 메시지");

            assertThat(job.getProcessingContext().getErrorMessage()).isEqualTo("처리 실패 메시지");
        }

        @Test
        @DisplayName("processingContext의 workerJobId가 유지된다")
        void fail_whenProcessing_preservesWorkerJobId() {
            Job job = pendingJob();
            job.startProcessing("worker-job-1");

            job.fail("처리 실패 메시지");

            assertThat(job.getProcessingContext().getWorkerJobId()).isEqualTo("worker-job-1");
        }

        @Test
        @DisplayName("processingContext의 result는 null이다")
        void fail_whenProcessing_resultIsNull() {
            Job job = processingJob();

            job.fail("처리 실패 메시지");

            assertThat(job.getProcessingContext().getResult()).isNull();
        }

        @Test
        @DisplayName("PENDING 상태에서 실패하면 workerJobId는 null이고 errorMessage가 저장된다")
        void fail_whenPending_workerJobIdIsNullAndErrorMessageIsSet() {
            Job job = pendingJob();

            job.fail("처리 실패 메시지");

            assertSoftly(softly -> {
                softly.assertThat(job.getProcessingContext().getWorkerJobId()).isNull();
                softly.assertThat(job.getProcessingContext().getErrorMessage())
                      .isEqualTo("처리 실패 메시지");
            });
        }

        @Test
        @DisplayName("이미 COMPLETED 상태이면 IllegalStateException이 발생한다")
        void fail_whenCompleted_throwsIllegalStateException() {
            Job job = processingJob();
            job.complete("처리 결과");

            assertThatThrownBy(() -> job.fail("처리 실패 메시지"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("이미 FAILED 상태이면 IllegalStateException이 발생한다")
        void fail_whenFailed_throwsIllegalStateException() {
            Job job = processingJob();
            job.fail("처리 실패 메시지");

            assertThatThrownBy(() -> job.fail("또 다른 실패"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FAILED");
        }
    }

    @Nested
    @DisplayName("@PrePersist / @PreUpdate - 타임스탬프 자동 설정")
    class JpaTimestampCallbacks {

        @Test
        @DisplayName("createdAt과 updatedAt이 현재 시각으로 설정된다")
        void onCreate_setsCreatedAtAndUpdatedAt() {
            Job job = pendingJob();
            Instant before = Instant.now();

            job.onCreate();

            Instant after = Instant.now();
            assertSoftly(softly -> {
                softly.assertThat(job.getCreatedAt()).isBetween(before, after);
                softly.assertThat(job.getUpdatedAt()).isBetween(before, after);
            });
        }

        @Test
        @DisplayName("createdAt과 updatedAt이 동일한 Instant이다")
        void onCreate_createdAtAndUpdatedAtAreSameInstant() {
            Job job = pendingJob();

            job.onCreate();

            assertThat(job.getCreatedAt()).isEqualTo(job.getUpdatedAt());
        }

        @Test
        @DisplayName("updatedAt이 현재 시각으로 갱신된다")
        void onUpdate_setsUpdatedAt() {
            Job job = pendingJob();
            job.onCreate();
            Instant before = Instant.now();

            job.onUpdate();

            Instant after = Instant.now();
            assertThat(job.getUpdatedAt()).isBetween(before, after);
        }
    }
}
