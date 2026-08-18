package com.operion.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Standard pagination envelope for any list endpoint that opts into it - established here
 * once so every future paginated endpoint returns the same shape (content/page/size/
 * totalElements/totalPages) instead of each module inventing its own. Endpoints that
 * genuinely never need pagination (small bounded catalogs - Roles, Permissions, GradeLevels)
 * are not required to use this; it's a convention for anything that can grow unbounded.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
