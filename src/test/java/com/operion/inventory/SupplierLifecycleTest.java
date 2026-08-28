package com.operion.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Covers #50 - Supplier is a plain address-book row (no dedicated service), same
 * ACTIVE-by-default / changeStatus toggle shape as Department. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SupplierLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void newTenant(String slugPrefix) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	@Test
	void creatingASupplierDefaultsToActive() {
		newTenant("create");

		Supplier supplier = supplierRepository
				.save(new Supplier("Acme Stationery", "Jane Doe", "9999999999", "jane@acme.test", "123 Market St"));

		assertThat(supplier.getStatus()).isEqualTo(SupplierStatus.ACTIVE);
		assertThat(supplier.getName()).isEqualTo("Acme Stationery");
	}

	@Test
	void changingStatusPersists() {
		newTenant("toggle");
		Supplier supplier = supplierRepository.save(new Supplier("Acme Stationery", null, null, null, null));

		supplier.changeStatus(SupplierStatus.INACTIVE);
		supplierRepository.save(supplier);

		Supplier reloaded = supplierRepository.findById(supplier.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(SupplierStatus.INACTIVE);
	}

	@Test
	void suppliersAreTenantScoped() {
		newTenant("tenant-a");
		supplierRepository.save(new Supplier("Org A Supplier", null, null, null, null));
		TenantContext.clear();

		newTenant("tenant-b");
		assertThat(supplierRepository.findAll()).isEmpty();
	}
}
