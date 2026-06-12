package com.realteeth.image_processing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessingContext {

    private String workerJobId;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public static ProcessingContext empty() {
        return new ProcessingContext(null, null, null);
    }

    public static ProcessingContext processing(String workerJobId) {
        return new ProcessingContext(workerJobId, null, null);
    }

    public static ProcessingContext completed(String workerJobId, String result) {
        return new ProcessingContext(workerJobId, result, null);
    }

    public static ProcessingContext failed(String workerJobId, String errorMessage) {
        return new ProcessingContext(workerJobId, null, errorMessage);
    }
}
