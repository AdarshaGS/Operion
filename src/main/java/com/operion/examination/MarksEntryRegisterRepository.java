package com.operion.examination;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarksEntryRegisterRepository extends JpaRepository<MarksEntryRegister, Long> {

	Optional<MarksEntryRegister> findByExamScheduleId(Long examScheduleId);
}
