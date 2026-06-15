package com.realteeth.image_processing.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID jobId) {
        super("Job을 찾을 수 없습니다: " + jobId);
    }
}
