package com.operion.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** Proves the store()/resolveUrl() round trip and the validation rules from #25 (type, size). */
class LocalDiskAssetStorageServiceTest {

	@TempDir
	Path tempDir;

	private AssetStorageService storageService;

	@BeforeEach
	void setUp() {
		storageService = new LocalDiskAssetStorageService(tempDir.toString());
	}

	@Test
	void storesAValidPngAndResolvesItBackToTheSameBytes() throws Exception {
		byte[] content = { 1, 2, 3, 4 };
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", content);

		String reference = storageService.store(file);

		assertThat(reference).endsWith(".png");
		assertThat(storageService.resolveUrl(reference)).isEqualTo("/uploads/" + reference);
		assertThat(Files.readAllBytes(tempDir.resolve(reference))).isEqualTo(content);
	}

	@Test
	void rejectsAnUnsupportedContentType() {
		MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] { 1 });

		assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(AssetStorageException.class)
				.hasMessageContaining("PNG or JPG");
	}

	@Test
	void rejectsAFileOverTheSizeLimit() {
		byte[] tooLarge = new byte[6 * 1024 * 1024];
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", tooLarge);

		assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(AssetStorageException.class)
				.hasMessageContaining("5MB");
	}

	@Test
	void rejectsAnEmptyFile() {
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

		assertThatThrownBy(() -> storageService.store(file)).isInstanceOf(AssetStorageException.class);
	}
}
