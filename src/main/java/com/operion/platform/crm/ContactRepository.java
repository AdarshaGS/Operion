package com.operion.platform.crm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, Long> {

	List<Contact> findByLeadIdOrderByNameAsc(Long leadId);

	List<Contact> findByOrganisationIdOrderByNameAsc(Long organisationId);
}
