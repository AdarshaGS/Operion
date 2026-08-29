package com.operion.communication.api;

import com.operion.communication.CommunicationService.AudiencePreview;

public record AudiencePreviewResponse(int audienceSize, int notifiableCount) {

	static AudiencePreviewResponse from(AudiencePreview preview) {
		return new AudiencePreviewResponse(preview.audienceSize(), preview.notifiableCount());
	}
}
