package com.operion.organisation;

import java.time.LocalDate;

import com.operion.common.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the one-current-year-at-a-time invariant when marking a year current or closing it. */
@Service
public class AcademicYearService {

	private final AcademicYearRepository academicYearRepository;

	public AcademicYearService(AcademicYearRepository academicYearRepository) {
		this.academicYearRepository = academicYearRepository;
	}

	/** Years are tenant-scoped, so the overlap check only sees the caller's own organisation. */
	@Transactional
	public AcademicYear create(String name, LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			throw new InvalidRequestException("End date must not be before the start date");
		}
		if (academicYearRepository.existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(endDate, startDate)) {
			throw new IllegalStateException("The date range overlaps an existing academic year");
		}
		return academicYearRepository.save(new AcademicYear(name, startDate, endDate));
	}

	@Transactional
	public AcademicYear markCurrent(Long id) {
		AcademicYear target = academicYearRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + id));

		academicYearRepository.findByCurrentTrue()
				.filter(current -> !current.getId().equals(id))
				.ifPresent(current -> {
					current.unmarkCurrent();
					academicYearRepository.save(current);
				});

		target.markCurrent();
		return academicYearRepository.save(target);
	}

	@Transactional
	public AcademicYear close(Long id) {
		AcademicYear year = academicYearRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + id));
		year.close();
		return academicYearRepository.save(year);
	}
}
