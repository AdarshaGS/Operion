package com.operion.payroll;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

	List<PayrollRun> findAllByOrderByPayYearDescPayMonthDescIdDesc();

	/**
	 * Row-locks every run of one month (all statuses), in id order, so generate / correct / finalize /
	 * discard / adjust for that month serialise. Must be the first read of the transaction: later plain
	 * reads then see everything committed before the lock was granted. When the month has no run yet
	 * there is no row to lock; InnoDB's gap lock on the (organisation, year, month) index still stops
	 * two concurrent first runs both inserting (the loser fails with a deadlock instead).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from PayrollRun r where r.payYear = :year and r.payMonth = :month order by r.id")
	List<PayrollRun> findPeriodForUpdate(@Param("year") int year, @Param("month") int month);
}
