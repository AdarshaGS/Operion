package com.operion.organisation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationBranding;
import com.operion.organisation.OrganisationBrandingRepository;
import com.operion.organisation.OrganisationRepository;
import com.operion.storage.AssetStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Proves the GET/PUT round trip for OrganisationBranding (#26) - refs saved on PUT,
 * resolved to URLs on GET, null slots stay null rather than resolving to a broken URL. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganisationBrandingControllerTest {

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private OrganisationBrandingRepository brandingRepository;

	private final AssetStorageService fakeAssetStorageService = new AssetStorageService() {
		@Override
		public String store(org.springframework.web.multipart.MultipartFile file) {
			throw new UnsupportedOperationException("not needed by this controller");
		}

		@Override
		public String resolveUrl(String reference) {
			return "/uploads/" + reference;
		}
	};

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation newOrganisation() {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "test-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		brandingRepository.save(new OrganisationBranding(organisation));
		return organisation;
	}

	@Test
	void getResolvesNullRefsToNullUrlsRatherThanBrokenLinks() {
		newOrganisation();
		OrganisationBrandingController controller = new OrganisationBrandingController(brandingRepository, fakeAssetStorageService);

		OrganisationBrandingResponse response = controller.get();

		assertThat(response.logoUrl()).isNull();
		assertThat(response.stampUrl()).isNull();
		assertThat(response.signatureUrl()).isNull();
	}

	@Test
	void updateSavesRefsAndTextFieldsAndReturnsResolvedUrls() {
		newOrganisation();
		OrganisationBrandingController controller = new OrganisationBrandingController(brandingRepository, fakeAssetStorageService);
		UpdateOrganisationBrandingRequest request = new UpdateOrganisationBrandingRequest("logo.png", "stamp.png", "signature.png",
				"ABC Public School", "12 Main Street", "Affiliated to XYZ Board", "Thank you for choosing us", "STU-{YYYY}-{SEQ:4}",
				"INV-{AY}-{SEQ:6}", "RCT-{AY}-{SEQ:6}");

		OrganisationBrandingResponse response = controller.update(request);

		assertThat(response.logoRef()).isEqualTo("logo.png");
		assertThat(response.logoUrl()).isEqualTo("/uploads/logo.png");
		assertThat(response.stampUrl()).isEqualTo("/uploads/stamp.png");
		assertThat(response.signatureUrl()).isEqualTo("/uploads/signature.png");
		assertThat(response.schoolNameOverride()).isEqualTo("ABC Public School");
		assertThat(response.addressLine()).isEqualTo("12 Main Street");
		assertThat(response.affiliationText()).isEqualTo("Affiliated to XYZ Board");
		assertThat(response.footerText()).isEqualTo("Thank you for choosing us");
		assertThat(response.admissionNumberFormat()).isEqualTo("STU-{YYYY}-{SEQ:4}");

		OrganisationBrandingResponse reread = controller.get();
		assertThat(reread.logoUrl()).isEqualTo("/uploads/logo.png");
	}
}
