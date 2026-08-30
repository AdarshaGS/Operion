package com.operion.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * MVP storage backend (GitHub #25): local disk under a served static path, not S3 -
 * nothing needs multi-instance/CDN-backed storage yet, and the {@link AssetStorageService}
 * seam means swapping later touches only this class. Served publicly (see WebConfig's
 * "/uploads/**" resource handler, outside "/api/v1/**" and so outside the JWT
 * interceptor) since callers - an <img> tag on a printed receipt, a public letterhead -
 * can't attach an Authorization header. References are random UUIDs specifically so an
 * unauthenticated path isn't also an enumerable one.
 */
@Component
public class LocalDiskAssetStorageService implements AssetStorageService {

	private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
	private static final Map<String, String> ALLOWED_CONTENT_TYPES =
			Map.of("image/png", "png", "image/jpeg", "jpg", "application/pdf", "pdf");

	private final Path baseDir;

	public LocalDiskAssetStorageService(@Value("${app.storage.local.base-dir:./data/uploads}") String baseDir) {
		this.baseDir = Path.of(baseDir);
	}

	@Override
	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new AssetStorageException("No file provided");
		}
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new AssetStorageException("File exceeds the 5MB size limit");
		}
		String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
		if (extension == null) {
			throw new AssetStorageException("Only PNG, JPG, or PDF files are accepted");
		}

		String reference = UUID.randomUUID() + "." + extension;
		try {
			Files.createDirectories(baseDir);
			file.transferTo(baseDir.resolve(reference));
		} catch (IOException e) {
			throw new AssetStorageException("Failed to store the uploaded file", e);
		}
		return reference;
	}

	@Override
	public String resolveUrl(String reference) {
		return "/uploads/" + reference;
	}
}
