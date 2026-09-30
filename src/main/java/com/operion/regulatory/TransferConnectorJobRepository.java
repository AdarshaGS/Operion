package com.operion.regulatory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferConnectorJobRepository extends JpaRepository<TransferConnectorJob, Long> {

	List<TransferConnectorJob> findByStudentExitIdOrderByIdDesc(Long studentExitId);

	List<TransferConnectorJob> findByStatusOrderByIdDesc(TransferConnectorJobStatus status);

	List<TransferConnectorJob> findAllByOrderByIdDesc();
}
