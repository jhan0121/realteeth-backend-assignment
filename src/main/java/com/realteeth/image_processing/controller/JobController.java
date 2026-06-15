package com.realteeth.image_processing.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.realteeth.image_processing.controller.dto.request.CreateJobRequest;
import com.realteeth.image_processing.controller.dto.response.CommonApiResponse;
import com.realteeth.image_processing.controller.dto.response.JobResponse;
import com.realteeth.image_processing.controller.dto.response.PagedJobResponse;
import com.realteeth.image_processing.service.JobService;
import com.realteeth.image_processing.service.dto.SubmitJobResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Validated
public class JobController implements JobControllerDocs {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<CommonApiResponse<JobResponse>> submitJob(
            @RequestHeader("Idempotency-Key")
            @Size(max = 255, message = "Idempotency-Key는 255자를 초과할 수 없습니다")
            String idempotencyKey,
            @RequestBody @Valid CreateJobRequest request) {
        SubmitJobResult result = jobService.submitJob(idempotencyKey, request.imageUrl(), request.userId());
        HttpStatus status = result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(CommonApiResponse.ok(JobResponse.from(result.job())));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<CommonApiResponse<JobResponse>> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(CommonApiResponse.ok(JobResponse.from(jobService.getJob(jobId))));
    }

    @GetMapping
    public ResponseEntity<CommonApiResponse<PagedJobResponse>> listJobs(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(CommonApiResponse.ok(PagedJobResponse.from(jobService.listJobs(pageable))));
    }
}
