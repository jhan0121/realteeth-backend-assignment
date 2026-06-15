package com.realteeth.image_processing.repository;

import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByJobId(UUID jobId);

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findByStatus(JobStatus status);
}
