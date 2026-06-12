package com.realteeth.image_processing.repository;

import com.realteeth.image_processing.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {
}
