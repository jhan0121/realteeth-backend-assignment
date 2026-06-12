package com.realteeth.image_processing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.realteeth.image_processing.client.dto.ProcessStartResponse;
import com.realteeth.image_processing.client.dto.ProcessStatusResponse;
import com.realteeth.image_processing.exception.WorkerJobLostException;

class ImageWorkerClientTest {

    private static final String BASE_URL = "http://worker.test";
    private static final ImageWorkerProperties PROPS =
            new ImageWorkerProperties(BASE_URL, "Tester", "tester@example.com");

    private MockRestServiceServer server;
    private ImageWorkerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                                               .baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder)
                                      .build();
        client = new ImageWorkerClient(builder.build(), PROPS);

        server.expect(requestTo(BASE_URL + "/mock/auth/issue-key"))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("{\"apiKey\":\"init-key\"}", MediaType.APPLICATION_JSON));

        client.afterSingletonsInstantiated();
        server.verify();
        server.reset();
    }

    @Test
    @DisplayName("startProcessing은 정상 응답 시 jobId와 status를 반환한다")
    void startProcessing_happyPath_returnsResponse() {
        server.expect(requestTo(BASE_URL + "/mock/process"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("X-API-KEY", "init-key"))
              .andRespond(withSuccess(
                      "{\"jobId\":\"worker-job-1\",\"status\":\"PROCESSING\"}",
                      MediaType.APPLICATION_JSON));

        ProcessStartResponse response = client.startProcessing("http://img.test/photo.jpg");

        assertThat(response.jobId()).isEqualTo("worker-job-1");
        assertThat(response.status()).isEqualTo("PROCESSING");
        server.verify();
    }

    @Test
    @DisplayName("startProcessing은 401 응답 시 키를 재발급하고 1회 재시도한다")
    void startProcessing_on401_reissuesKeyAndRetries() {
        server.expect(requestTo(BASE_URL + "/mock/process"))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/mock/auth/issue-key"))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess("{\"apiKey\":\"refreshed-key\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/mock/process"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("X-API-KEY", "refreshed-key"))
              .andRespond(withSuccess(
                      "{\"jobId\":\"worker-job-2\",\"status\":\"PROCESSING\"}",
                      MediaType.APPLICATION_JSON));

        ProcessStartResponse response = client.startProcessing("http://img.test/photo.jpg");

        assertThat(response.jobId()).isEqualTo("worker-job-2");
        server.verify();
    }

    @Test
    @DisplayName("startProcessing은 429 응답 시 1초 대기 후 재시도하여 성공한다")
    void startProcessing_on429_retriesAfterBackoff() {
        server.expect(requestTo(BASE_URL + "/mock/process"))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE_URL + "/mock/process"))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(
                      "{\"jobId\":\"worker-job-3\",\"status\":\"PROCESSING\"}",
                      MediaType.APPLICATION_JSON));

        ProcessStartResponse response = client.startProcessing("http://img.test/photo.jpg");

        assertThat(response.jobId()).isEqualTo("worker-job-3");
        server.verify();
    }

    @Test
    @DisplayName("pollStatus는 정상 응답 시 상태와 결과를 반환한다")
    void pollStatus_happyPath_returnsStatus() {
        server.expect(requestTo(BASE_URL + "/mock/process/worker-job-1"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(
                      "{\"jobId\":\"worker-job-1\",\"status\":\"COMPLETED\",\"result\":\"Image processed successfully\"}",
                      MediaType.APPLICATION_JSON));

        ProcessStatusResponse response = client.pollStatus("worker-job-1");

        assertThat(response.jobId()).isEqualTo("worker-job-1");
        assertThat(response.status()).isEqualTo("COMPLETED");
        server.verify();
    }

    @Test
    @DisplayName("pollStatus는 404 응답 시 WorkerJobLostException을 던진다")
    void pollStatus_on404_throwsWorkerJobLostException() {
        server.expect(requestTo(BASE_URL + "/mock/process/missing-job"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.pollStatus("missing-job"))
                .isInstanceOf(WorkerJobLostException.class);
    }
}
