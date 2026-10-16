package com.operion.hr;

import java.util.ArrayList;
import java.util.List;

import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.rollover.RolloverContributor;
import com.operion.organisation.rollover.RolloverOptions;
import com.operion.organisation.rollover.RolloverStep;
import com.operion.organisation.rollover.RolloverStep.Tally;
import org.springframework.stereotype.Component;

/** Each staff member's leave entitlement for the new year, set to the same days as before. Used days are not carried and unused days are not rolled over. Staff who have left are skipped. */
@Component
public class LeaveAllocationRollover implements RolloverContributor {

	private final LeaveBalanceRepository balanceRepository;

	LeaveAllocationRollover(LeaveBalanceRepository balanceRepository) {
		this.balanceRepository = balanceRepository;
	}

	@Override
	public String key() {
		return "LEAVE_ALLOCATIONS";
	}

	@Override
	public String label() {
		return "Leave allocations";
	}

	@Override
	public AppModule module() {
		return AppModule.HR;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public RolloverStep run(AcademicYear source, AcademicYear target, RolloverOptions options) {
		Tally allocations = new Tally("Leave allocations");
		int left = 0;
		for (LeaveBalance balance : balanceRepository.findByAcademicYearId(source.getId())) {
			StaffProfileStatus status = balance.getStaffProfile().getStatus();
			if (status == StaffProfileStatus.RESIGNED || status == StaffProfileStatus.TERMINATED) {
				left++;
				continue;
			}
			if (balanceRepository.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(
					balance.getStaffProfile().getId(), balance.getLeaveType().getId(), target.getId()).isPresent()) {
				allocations.alreadyThere();
			} else {
				balanceRepository.save(new LeaveBalance(balance.getStaffProfile(), balance.getLeaveType(), target, balance.getAllocatedDays()));
				allocations.copied();
			}
		}
		List<String> notes = new ArrayList<>();
		if (left > 0) {
			notes.add(left + " allocation(s) left out: the staff member has resigned or been terminated");
		}
		return RolloverStep.of(this, List.of(allocations), notes);
	}
}
