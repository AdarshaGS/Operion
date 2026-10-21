package com.operion.certificate;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CertificateTypeRepository extends JpaRepository<CertificateType, Long> {

	List<CertificateType> findAllByOrderByNameAsc();

	Optional<CertificateType> findByCode(String code);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from CertificateType t where t.id = :id")
	Optional<CertificateType> findByIdForUpdate(@Param("id") Long id);
}
