package com.operion.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Materials repository scoping and kind validation (#228). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, LearningMaterialService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LearningMaterialServiceTest extends LearningTestBase {

	@Autowired private LearningMaterialService materialService;

	@Test
	void kindsAreValidatedAndIrrelevantFieldsDropped() {
		assertThatThrownBy(() -> materialService.add(admin, schoolClass.getId(), null, "No file", null, MaterialKind.FILE, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> materialService.add(admin, schoolClass.getId(), null, "Bad", null, MaterialKind.LINK, null, "ftp://x"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> materialService.add(admin, schoolClass.getId(), null, "Empty", null, MaterialKind.NOTE, null, null))
				.isInstanceOf(IllegalArgumentException.class);

		LearningMaterial link = materialService.add(admin, schoolClass.getId(), maths.getId(), "Khan", null, MaterialKind.LINK,
				"stray-ref.pdf", "https://example.org/x");
		assertThat(link.getAttachmentRef()).isNull();
		assertThat(link.getUrl()).isEqualTo("https://example.org/x");
	}

	@Test
	void teacherCanOnlyAddForTheirClassAndSubject() {
		assertThat(materialService.add(ashaTeachesMathsInA, schoolClass.getId(), maths.getId(), "Notes", "body",
				MaterialKind.NOTE, null, null)).isNotNull();
		assertThatThrownBy(() -> materialService.add(ashaTeachesMathsInA, schoolClass.getId(), science.getId(), "Notes",
				"body", MaterialKind.NOTE, null, null)).isInstanceOf(AuthorizationDeniedException.class);
		// A class-wide (no subject) material needs any assignment in that class.
		assertThat(materialService.add(balaTeachesScienceInB, schoolClass.getId(), null, "General", "hello",
				MaterialKind.NOTE, null, null)).isNotNull();
	}

	@Test
	void guardiansSeeActiveMaterialsOfTheChildsClassOnly() {
		LearningMaterial keep = materialService.add(admin, schoolClass.getId(), null, "Keep", "x", MaterialKind.NOTE, null, null);
		LearningMaterial gone = materialService.add(admin, schoolClass.getId(), null, "Gone", "x", MaterialKind.NOTE, null, null);
		materialService.archive(admin, gone.getId());

		assertThat(materialService.forChild(raviEnrollment)).extracting(LearningMaterial::getId).containsExactly(keep.getId());
		assertThat(materialService.list(admin, null)).hasSize(2);
		assertThat(materialService.list(ashaTeachesMathsInA, null)).hasSize(2);
	}
}
