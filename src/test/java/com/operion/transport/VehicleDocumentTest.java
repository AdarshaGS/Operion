package com.operion.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.communication.CommunicationService;
import com.operion.finance.FeeService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves VehicleDocument re-upload supersedes the prior ACTIVE document of the same
 * type (same pattern as StaffDocument/StudentDocument), and verify records the decision.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, TransportService.class, FeeService.class, TransportNotificationService.class,
		CommunicationService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VehicleDocumentTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private VehicleDocumentRepository vehicleDocumentRepository;

	@Autowired
	private TransportService transportService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Vehicle setUpVehicle(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		return vehicleRepository.save(new Vehicle(campus, "KA-01-1234", VehicleType.BUS, 40, null, null));
	}

	@Test
	void reuploadSupersedesThePriorActiveDocumentOfTheSameType() {
		Vehicle vehicle = setUpVehicle("vehicle-document-reupload");

		VehicleDocument first = transportService.addVehicleDocument(vehicle, "INSURANCE", "s3://ref-1", "insurance-v1.pdf", "application/pdf", null);
		VehicleDocument second = transportService.addVehicleDocument(vehicle, "INSURANCE", "s3://ref-2", "insurance-v2.pdf", "application/pdf", null);

		assertThat(vehicleDocumentRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(VehicleDocumentStatus.SUPERSEDED);
		assertThat(second.getStatus()).isEqualTo(VehicleDocumentStatus.ACTIVE);

		List<VehicleDocument> active = vehicleDocumentRepository.findByVehicleIdAndStatus(vehicle.getId(), VehicleDocumentStatus.ACTIVE);
		assertThat(active).hasSize(1);
		assertThat(active.get(0).getId()).isEqualTo(second.getId());
	}

	@Test
	void differentDocumentTypesDoNotSupersedeEachOther() {
		Vehicle vehicle = setUpVehicle("vehicle-document-different-types");

		transportService.addVehicleDocument(vehicle, "INSURANCE", "s3://ref-1", "insurance.pdf", "application/pdf", null);
		transportService.addVehicleDocument(vehicle, "PERMIT", "s3://ref-2", "permit.pdf", "application/pdf", null);

		List<VehicleDocument> active = vehicleDocumentRepository.findByVehicleIdAndStatus(vehicle.getId(), VehicleDocumentStatus.ACTIVE);
		assertThat(active).hasSize(2);
	}

	@Test
	void verifyingRecordsTheDecision() {
		Vehicle vehicle = setUpVehicle("vehicle-document-verify");
		VehicleDocument document = transportService.addVehicleDocument(vehicle, "INSURANCE", "s3://ref-1", "insurance.pdf", "application/pdf", null);

		transportService.verifyVehicleDocument(document, DocumentVerificationStatus.VERIFIED, 7L);

		assertThat(document.getVerificationStatus()).isEqualTo(DocumentVerificationStatus.VERIFIED);
		assertThat(document.getVerifiedBy()).isEqualTo(7L);
		assertThat(document.getVerifiedAt()).isNotNull();
	}
}
