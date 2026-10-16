package com.operion.modules;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface OrganisationModuleSettingRepository extends JpaRepository<OrganisationModuleSetting, Long> {

	List<OrganisationModuleSetting> findByOrganisationIdOrderByIdAsc(Long organisationId);

	List<OrganisationModuleSetting> findByOrganisationIdOrderByIdDesc(Long organisationId);
}
