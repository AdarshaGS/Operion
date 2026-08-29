package com.operion.document.api;

import com.operion.document.IdCardTemplate;

public record IdCardTemplateResponse(Long id, String name, double widthMm, double heightMm, String layoutJson) {

	public static IdCardTemplateResponse from(IdCardTemplate template) {
		return new IdCardTemplateResponse(template.getId(), template.getName(), template.getWidthMm(), template.getHeightMm(),
				template.getLayoutJson());
	}
}
