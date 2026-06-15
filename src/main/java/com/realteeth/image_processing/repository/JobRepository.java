package com.realteeth.image_processing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.realteeth.image_processing.domain.Job;
import com.realteeth.image_processing.domain.JobStatus;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByJobId(UUID jobId);

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findByStatus(JobStatus status);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
                UPDATE Job j SET j.status = com.realteeth.image_processing.domain.JobStatus.PROCESSING
                WHERE j.jobId = :jobId AND j.status = com.realteeth.image_processing.domain.JobStatus.PENDING
            """)
    int tryClaimJob(@Param("jobId") UUID jobId);
}
