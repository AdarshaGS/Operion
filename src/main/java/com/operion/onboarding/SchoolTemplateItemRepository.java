package com.operion.onboarding;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolTemplateItemRepository extends JpaRepository<SchoolTemplateItem, Long> {

	List<SchoolTemplateItem> findByCategoryOrderBySequenceOrderAscNameAsc(SchoolTemplateItemCategory category);
}
