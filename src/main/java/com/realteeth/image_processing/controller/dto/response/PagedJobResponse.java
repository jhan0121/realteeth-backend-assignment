package com.realteeth.image_processing.controller.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import com.realteeth.image_processing.service.dto.JobData;

public record PagedJobResponse(
        List<JobResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static PagedJobResponse from(Page<JobData> pageResult) {
        return new PagedJobResponse(
                pageResult.getContent().stream().map(JobResponse::from).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
        );
    }
}
