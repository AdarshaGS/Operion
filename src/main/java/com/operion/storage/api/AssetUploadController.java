package com.operion.storage.api;

import com.operion.storage.AssetStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Generic asset upload (GitHub #25) - deliberately not permission-gated beyond the JWT
 * interceptor every "/api/v1/**" endpoint already requires. Uploading bytes attaches
 * them to nothing by itself; the permission check belongs on whichever endpoint saves a
 * returned reference onto a real record (e.g. OrganisationBranding's PUT, gated behind
 * ORGANISATION_MANAGE). Reused as-is by every future caller - branding, ID cards, HR/
 * student document uploads - rather than each module growing its own upload endpoint.
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetUploadController {

	private final AssetStorageService assetStorageService;

	public AssetUploadController(AssetStorageService assetStorageService) {
		this.assetStorageService = assetStorageService;
	}

	@PostMapping
	public AssetUploadResponse upload(@RequestParam("file") MultipartFile file) {
		String reference = assetStorageService.store(file);
		return new AssetUploadResponse(reference, assetStorageService.resolveUrl(reference));
	}
}
