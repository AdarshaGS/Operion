package com.operion.onboarding;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolStarterNeedRepository extends JpaRepository<SchoolStarterNeed, Long> {

	List<SchoolStarterNeed> findAllByOrderByDisplayOrderAsc();
}
