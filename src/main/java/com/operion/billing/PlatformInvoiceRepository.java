package com.operion.billing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformInvoiceRepository extends JpaRepository<PlatformInvoice, Long> {

	List<PlatformInvoice> findByOrganisationIdOrderByPeriodStartDesc(Long organisationId);
}
