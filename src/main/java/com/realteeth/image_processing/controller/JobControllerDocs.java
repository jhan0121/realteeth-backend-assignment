package com.realteeth.image_processing.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.realteeth.image_processing.controller.dto.request.CreateJobRequest;
import com.realteeth.image_processing.controller.dto.response.CommonApiResponse;
import com.realteeth.image_processing.controller.dto.response.JobResponse;
import com.realteeth.image_processing.controller.dto.response.PagedJobResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Jobs", description = "이미지 처리 작업 관리 API")
public interface JobControllerDocs {

    @Operation(
            summary = "작업 등록",
            description = "이미지 처리 작업을 비동기로 등록합니다. 처리는 백그라운드에서 진행되며 jobId를 즉시 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202",
                    description = "작업 생성 성공 (신규 등록)"),
            @ApiResponse(responseCode = "200",
                    description = "중복 요청 - 동일 Idempotency-Key의 기존 작업 반환"),
            @ApiResponse(responseCode = "400",
                    description = "잘못된 요청 (필수 파라미터 누락 또는 형식 오류)")
    })
    ResponseEntity<CommonApiResponse<JobResponse>> submitJob(
            @Parameter(description = "멱등성 키 - 동일 키 재요청 시 기존 결과 반환", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreateJobRequest request
    );

    @Operation(
            summary = "작업 단건 조회",
            description = "jobId로 단일 이미지 처리 작업의 상태 및 결과를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400",
                    description = "jobId 형식 오류 (UUID가 아닌 경우)"),
            @ApiResponse(responseCode = "404",
                    description = "작업을 찾을 수 없음")
    })
    ResponseEntity<CommonApiResponse<JobResponse>> getJob(
            @Parameter(description = "작업 ID (UUID)", required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID jobId
    );

    @Operation(
            summary = "작업 목록 조회",
            description = "등록된 모든 이미지 처리 작업을 페이지네이션으로 조회합니다. 기본 페이지 크기는 20입니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<CommonApiResponse<PagedJobResponse>> listJobs(
            @PageableDefault(size = 20) Pageable pageable
    );
}
