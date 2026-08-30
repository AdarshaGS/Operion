package com.operion.student;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out of StudentService so this stays a real, directly @Import-able Spring bean
 * (no AuditLogService/ObjectMapper dependency) - the pessimistic write lock below needs
 * an actually-active transaction, which only a genuine AOP-proxied @Transactional
 * method provides; StudentService itself is hand-constructed in several @DataJpaTest
 * slices that have no ObjectMapper bean, where its own @Transactional is inert.
 */
@Service
public class StudentIdGenerator {

	private final StudentIdCounterRepository studentIdCounterRepository;

	public StudentIdGenerator(StudentIdCounterRepository studentIdCounterRepository) {
		this.studentIdCounterRepository = studentIdCounterRepository;
	}

	/** Atomic per-(organisation, year) sequence - never SELECT MAX()+1. */
	@Transactional
	public String next(LocalDate admissionDate) {
		int year = admissionDate.getYear();
		StudentIdCounter counter = studentIdCounterRepository.findByYear(year)
				.orElseGet(() -> studentIdCounterRepository.save(new StudentIdCounter(year)));
		long number = counter.consumeNext();
		studentIdCounterRepository.save(counter);
		return "STU-" + year + "-" + String.format("%05d", number);
	}
}
