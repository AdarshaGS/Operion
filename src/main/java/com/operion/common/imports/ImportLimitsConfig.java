package com.operion.common.imports;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Binds the app.imports.* properties onto the static {@link ImportFileParser}, the single reader every bulk import goes through. */
@Component
class ImportLimitsConfig {

	private final long maxFileBytes;
	private final int maxDataRows;
	private final int maxColumns;
	private final long maxXlsxEntryBytes;
	private final double minInflateRatio;

	ImportLimitsConfig(@Value("${app.imports.max-file-bytes:5242880}") long maxFileBytes,
			@Value("${app.imports.max-rows:10000}") int maxDataRows,
			@Value("${app.imports.max-columns:200}") int maxColumns,
			@Value("${app.imports.max-xlsx-entry-bytes:31457280}") long maxXlsxEntryBytes,
			@Value("${app.imports.min-inflate-ratio:0.01}") double minInflateRatio) {
		this.maxFileBytes = maxFileBytes;
		this.maxDataRows = maxDataRows;
		this.maxColumns = maxColumns;
		this.maxXlsxEntryBytes = maxXlsxEntryBytes;
		this.minInflateRatio = minInflateRatio;
	}

	@PostConstruct
	void apply() {
		ImportFileParser.configure(maxFileBytes, maxDataRows, maxColumns, maxXlsxEntryBytes, minInflateRatio);
	}
}
