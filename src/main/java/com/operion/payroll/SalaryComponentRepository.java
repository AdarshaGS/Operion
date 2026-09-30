package com.operion.payroll;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {

	List<SalaryComponent> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);
}
