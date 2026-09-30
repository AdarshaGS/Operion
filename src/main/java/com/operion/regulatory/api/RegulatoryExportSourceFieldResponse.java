package com.operion.regulatory.api;

import com.operion.regulatory.RegulatoryExportSources.Source;

public record RegulatoryExportSourceFieldResponse(String key, String label) {

	public static RegulatoryExportSourceFieldResponse from(Source<?> source) {
		return new RegulatoryExportSourceFieldResponse(source.key(), source.label());
	}
}
