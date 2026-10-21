package com.operion.certificate;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateTypePresetRepository extends JpaRepository<CertificateTypePreset, Long> {

	Optional<CertificateTypePreset> findByCode(String code);
}
