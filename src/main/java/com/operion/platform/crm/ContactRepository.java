package com.operion.platform.crm;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface ContactRepository extends JpaRepository<Contact, Long> {

	List<Contact> findByLeadIdOrderByNameAsc(Long leadId);

	List<Contact> findByOrganisationIdOrderByNameAsc(Long organisationId);
}
