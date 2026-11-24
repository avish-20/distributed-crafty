package com.avish.distributed_craftly.workspace_service.repository;

import com.avish.distributed_craftly.workspace_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
}
