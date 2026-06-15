package com.realteeth.image_processing.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.realteeth.image_processing.TestcontainersConfiguration;
import com.realteeth.image_processing.client.ImageWorkerClient;
import com.realteeth.image_processing.client.dto.ProcessStartResponse;
import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.repository.JobRepository;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@DisplayName("JobController")
class JobControllerTest {

    @LocalServerPort
    int port;

    @MockitoBean
    ImageWorkerClient imageWorkerClient;

    @Autowired
    JobRepository jobRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        jobRepository.deleteAll();
        when(imageWorkerClient.startProcessing(any()))
                .thenReturn(new ProcessStartResponse("worker-job-id", "PROCESSING"));
    }

    @Nested
    @DisplayName("POST /api/v1/jobs")
    class SubmitJob {

        @Test
        @DisplayName("신규 멱등키이면 202와 jobId를 반환한다")
        void submitJob_newIdempotencyKey_returns202WithJobId() {
            given()
                    .contentType(ContentType.JSON)
                    .header("Idempotency-Key", "key-001")
                    .body("""
                                  {"imageUrl": "https://example.com/image.jpg", "userId": "user-1"}
                                  """)
                    .when()
                    .post("/api/v1/jobs")
                    .then()
                    .statusCode(202)
                    .body("success", equalTo(true))
                    .body("data.jobId", notNullValue())
                    .body("data.status", equalTo("PENDING"));
        }

        @Test
        @DisplayName("동일 멱등키 재요청이면 200과 동일 jobId를 반환한다")
        void submitJob_duplicateIdempotencyKey_returns200WithSameJobId() {
            String firstJobId = given()
                    .contentType(ContentType.JSON)
                    .header("Idempotency-Key", "key-dup")
                    .body("""
                                  {"imageUrl": "https://example.com/image.jpg", "userId": "user-1"}
                                  """)
                    .when()
                    .post("/api/v1/jobs")
                    .then()
                    .statusCode(202)
                    .extract().jsonPath().getString("data.jobId");

            String secondJobId = given()
                    .contentType(ContentType.JSON)
                    .header("Idempotency-Key", "key-dup")
                    .body("""
                                  {"imageUrl": "https://example.com/image.jpg", "userId": "user-1"}
                                  """)
                    .when()
                    .post("/api/v1/jobs")
                    .then()
                    .statusCode(200)
                    .body("success", equalTo(true))
                    .extract().jsonPath().getString("data.jobId");

            assertThat(secondJobId).isEqualTo(firstJobId);
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400을 반환한다")
        void submitJob_missingIdempotencyKeyHeader_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                                  {"imageUrl": "https://example.com/image.jpg", "userId": "user-1"}
                                  """)
                    .when()
                    .post("/api/v1/jobs")
                    .then()
                    .statusCode(400)
                    .body("success", equalTo(false));
        }

        @Test
        @DisplayName("imageUrl이 공백이면 400을 반환한다")
        void submitJob_blankImageUrl_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .header("Idempotency-Key", "key-blank-url")
                    .body("""
                                  {"imageUrl": "", "userId": "user-1"}
                                  """)
                    .when()
                    .post("/api/v1/jobs")
                    .then()
                    .statusCode(400)
                    .body("success", equalTo(false));
        }

        @Test
        @DisplayName("userId가 공백이면 400을 반환한다")
        void submitJob_blankUserId_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .header("Idempotency-Key", "key-blank-user")
                    .body("""
                                  {"imageUrl": "https://example.com/image.jpg", "userId": ""}
                                  """)
                    .when()
                    .post("/api/v1/jobs")
                    .then()
                    .statusCode(400)
                    .body("success", equalTo(false));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs/{jobId}")
    class GetJob {

        @Test
        @DisplayName("존재하는 jobId이면 200과 작업 정보를 반환한다")
        void getJob_existingJobId_returns200WithJobData() {
            Job job = jobRepository.save(
                    Job.createPending("key-get", "https://example.com/image.jpg", "user-1"));

            given()
                    .when()
                    .get("/api/v1/jobs/{jobId}", job.getJobId())
                    .then()
                    .statusCode(200)
                    .body("success", equalTo(true))
                    .body("data.jobId", equalTo(job.getJobId().toString()))
                    .body("data.status", equalTo("PENDING"));
        }

        @Test
        @DisplayName("존재하지 않는 jobId이면 404를 반환한다")
        void getJob_unknownJobId_returns404() {
            given()
                    .when()
                    .get("/api/v1/jobs/{jobId}", UUID.randomUUID())
                    .then()
                    .statusCode(404)
                    .body("success", equalTo(false));
        }

        @Test
        @DisplayName("UUID 형식이 아닌 jobId이면 400을 반환한다")
        void getJob_invalidUuidFormat_returns400() {
            given()
                    .when()
                    .get("/api/v1/jobs/{jobId}", "not-a-uuid")
                    .then()
                    .statusCode(400)
                    .body("success", equalTo(false));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/jobs")
    class ListJobs {

        @Test
        @DisplayName("작업이 존재하면 200과 페이지 목록을 반환한다")
        void listJobs_withJobs_returns200WithPagedContent() {
            jobRepository.save(Job.createPending("key-a", "https://example.com/a.jpg", "user-1"));
            jobRepository.save(Job.createPending("key-b", "https://example.com/b.jpg", "user-2"));

            given()
                    .when()
                    .get("/api/v1/jobs")
                    .then()
                    .statusCode(200)
                    .body("success", equalTo(true))
                    .body("data.content", hasSize(2))
                    .body("data.totalElements", equalTo(2));
        }

        @Test
        @DisplayName("작업이 없으면 200과 빈 페이지를 반환한다")
        void listJobs_noJobs_returns200WithEmptyPage() {
            given()
                    .when()
                    .get("/api/v1/jobs")
                    .then()
                    .statusCode(200)
                    .body("success", equalTo(true))
                    .body("data.content", hasSize(0))
                    .body("data.totalElements", equalTo(0));
        }

        @Test
        @DisplayName("size 파라미터를 지정하면 해당 크기의 페이지를 반환한다")
        void listJobs_withSizeParam_returnsCorrectPageSize() {
            for (int i = 1; i <= 10; i++) {
                jobRepository.save(
                        Job.createPending("key-size-" + i, "https://example.com/" + i + ".jpg", "user-1"));
            }

            given()
                    .queryParam("size", 5)
                    .when()
                    .get("/api/v1/jobs")
                    .then()
                    .statusCode(200)
                    .body("success", equalTo(true))
                    .body("data.content", hasSize(5))
                    .body("data.size", equalTo(5))
                    .body("data.totalElements", equalTo(10));
        }
    }
}
