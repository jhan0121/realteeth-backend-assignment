package com.realteeth.image_processing.exception;

public class WorkerJobLostException extends RuntimeException {

    public WorkerJobLostException(String workerJobId) {
        super("Worker 작업을 찾을 수 없습니다: " + workerJobId);
    }
}
