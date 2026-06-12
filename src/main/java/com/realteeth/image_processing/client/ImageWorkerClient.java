package com.realteeth.image_processing.client;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.realteeth.image_processing.client.dto.IssueKeyRequest;
import com.realteeth.image_processing.client.dto.IssueKeyResponse;
import com.realteeth.image_processing.client.dto.ProcessRequest;
import com.realteeth.image_processing.client.dto.ProcessStartResponse;
import com.realteeth.image_processing.client.dto.ProcessStatusResponse;
import com.realteeth.image_processing.exception.ImageWorkerException;
import com.realteeth.image_processing.exception.RateLimitException;
import com.realteeth.image_processing.exception.WorkerJobLostException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ImageWorkerClient implements SmartInitializingSingleton {

    private static final int MAX_BACKOFF_RETRIES = 3;

    private final RestClient restClient;
    private final ImageWorkerProperties props;
    private final AtomicReference<String> cachedApiKey = new AtomicReference<>();
    private final ReentrantLock reissueLock = new ReentrantLock();

    public ImageWorkerClient(
            @Qualifier("imageWorkerRestClient") RestClient restClient,
            ImageWorkerProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    @Override
    public void afterSingletonsInstantiated() {
        issueApiKey();
    }

    public ProcessStartResponse startProcessing(String imageUrl) {
        return executeWithKeyRetry(() ->
                                           withExponentialBackoff(() ->
                                                                          callPost("/mock/process",
                                                                                   new ProcessRequest(imageUrl),
                                                                                   ProcessStartResponse.class)));
    }

    public ProcessStatusResponse pollStatus(String workerJobId) {
        try {
            return restClient.get()
                             .uri("/mock/process/{jobId}", workerJobId)
                             .retrieve()
                             .body(ProcessStatusResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new WorkerJobLostException(workerJobId);
        }
    }

    private void issueApiKey() {
        String key = withExponentialBackoff(() -> {
            IssueKeyResponse resp = restClient.post()
                                              .uri("/mock/auth/issue-key")
                                              .body(new IssueKeyRequest(props.candidateName(), props.email()))
                                              .retrieve()
                                              .onStatus(s -> s.value() == 429,
                                                        (req, res) -> {
                                                            throw new RateLimitException();
                                                        })
                                              .body(IssueKeyResponse.class);
            if (resp == null || resp.apiKey() == null) {
                throw new ImageWorkerException("API 키 발급 응답이 비어 있습니다.");
            }
            return resp.apiKey();
        });
        cachedApiKey.set(key);
        log.info("ImageWorkerClient API 키 발급 완료");
    }

    private <T> T executeWithKeyRetry(Supplier<T> action) {
        try {
            return action.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            reissueIfStale();
            return action.get();
        }
    }

    private void reissueIfStale() {
        if (reissueLock.tryLock()) {
            try {
                issueApiKey();
            } finally {
                reissueLock.unlock();
            }
        } else {
            // 다른 스레드가 재발급 중 - 완료 대기 후 진행
            reissueLock.lock();
            reissueLock.unlock();
        }
    }

    private <T> T callPost(String uri, Object body, Class<T> responseType) {
        return restClient.post()
                         .uri(uri)
                         .header("X-API-KEY", cachedApiKey.get())
                         .body(body)
                         .retrieve()
                         .onStatus(s -> s.value() == 429,
                                   (req, res) -> {
                                       throw new RateLimitException();
                                   })
                         .body(responseType);
    }

    private <T> T withExponentialBackoff(Supplier<T> action) {
        int attempt = 0;
        long delayMs = 1_000L;
        while (true) {
            try {
                return action.get();
            } catch (RateLimitException e) {
                if (attempt >= MAX_BACKOFF_RETRIES) {throw e;}
                attempt++;
                sleep(delayMs);
                delayMs *= 2;
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageWorkerException("백오프 중단: 스레드 인터럽트 발생", e);
        }
    }
}
