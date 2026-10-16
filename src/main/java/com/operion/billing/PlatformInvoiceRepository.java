package com.operion.billing;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface PlatformInvoiceRepository extends JpaRepository<PlatformInvoice, Long> {

	List<PlatformInvoice> findByOrganisationIdOrderByPeriodStartDesc(Long organisationId);
}
