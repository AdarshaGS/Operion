package com.operion.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.operion.academic.SchoolClass;
import com.operion.academic.YearStructureLookup;
import com.operion.modules.AppModule;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.rollover.RolloverContributor;
import com.operion.organisation.rollover.RolloverOptions;
import com.operion.organisation.rollover.RolloverStep;
import com.operion.organisation.rollover.RolloverStep.Tally;
import org.springframework.stereotype.Component;

/**
 * Fee structures per class: the group, each fee line and its installments, with due dates moved
 * by the number of years between the two years and amounts optionally raised by a percentage.
 * A group the new year already has (same class and name) is never merged into, so a fee set up
 * by hand for the new year is left exactly as it is.
 */
@Component
public class FeeStructureRollover implements RolloverContributor {

	private final FeeStructureGroupRepository groupRepository;
	private final FeeStructureRepository structureRepository;
	private final FeeStructureInstallmentRepository installmentRepository;
	private final YearStructureLookup lookup;

	FeeStructureRollover(FeeStructureGroupRepository groupRepository, FeeStructureRepository structureRepository,
			FeeStructureInstallmentRepository installmentRepository, YearStructureLookup lookup) {
		this.groupRepository = groupRepository;
		this.structureRepository = structureRepository;
		this.installmentRepository = installmentRepository;
		this.lookup = lookup;
	}

	@Override
	public String key() {
		return "FEE_STRUCTURES";
	}

	@Override
	public String label() {
		return "Fee structures";
	}

	@Override
	public AppModule module() {
		return AppModule.FEES;
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public RolloverStep run(AcademicYear source, AcademicYear target, RolloverOptions options) {
		Tally groups = new Tally("Fee groups");
		Tally lines = new Tally("Fee lines");
		Tally installments = new Tally("Installments");
		int noClass = 0;
		int yearShift = target.getStartDate().getYear() - source.getStartDate().getYear();
		BigDecimal factor = BigDecimal.ONE.add(options.feeIncreasePercent().movePointLeft(2));

		for (FeeStructureGroup group : groupRepository.findByAcademicYearId(source.getId())) {
			if (group.getStatus() != FeeStructureGroupStatus.ACTIVE) {
				continue;
			}
			SchoolClass targetClass = lookup.classIn(target, group.getSchoolClass()).orElse(null);
			if (targetClass == null) {
				noClass++;
				continue;
			}
			boolean exists = groupRepository.findByAcademicYearIdAndSchoolClassId(target.getId(), targetClass.getId()).stream()
					.anyMatch(existing -> existing.getName().equalsIgnoreCase(group.getName()));
			if (exists) {
				groups.alreadyThere();
				continue;
			}
			FeeStructureGroup newGroup = groupRepository.save(new FeeStructureGroup(group.getName(), target, targetClass));
			groups.copied();

			for (FeeStructure line : structureRepository.findByFeeStructureGroupId(group.getId())) {
				if (line.getStatus() != FeeStructureStatus.ACTIVE) {
					continue;
				}
				BigDecimal amount = scale(line.getAmount(), factor);
				BigDecimal discount = line.getOneShotDiscountAmount() == null ? null : scale(line.getOneShotDiscountAmount(), factor);
				FeeStructure newLine = structureRepository.save(
						new FeeStructure(newGroup, line.getFeeCategory(), amount, line.getPaymentFrequency(), discount));
				lines.copied();

				List<FeeStructureInstallment> sourceInstallments = installmentRepository.findByFeeStructureIdOrderByInstallmentNumber(line.getId());
				boolean sourceAddedUp = sourceInstallments.stream().map(FeeStructureInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
						.compareTo(line.getAmount()) == 0;
				BigDecimal scaledSum = BigDecimal.ZERO;
				for (int i = 0; i < sourceInstallments.size(); i++) {
					FeeStructureInstallment installment = sourceInstallments.get(i);
					BigDecimal installmentAmount = scale(installment.getAmount(), factor);
					scaledSum = scaledSum.add(installmentAmount);
					boolean last = i == sourceInstallments.size() - 1;
					if (last && sourceAddedUp) {
						// Rounding each installment can leave the total a few paise off the scaled line amount.
						installmentAmount = installmentAmount.add(amount.subtract(scaledSum));
					}
					installmentRepository.save(new FeeStructureInstallment(
							newLine, installment.getInstallmentNumber(), installment.getDueDate().plusYears(yearShift), installmentAmount));
					installments.copied();
				}
			}
		}
		List<String> notes = new ArrayList<>();
		if (noClass > 0) {
			notes.add(noClass + " fee group(s) left out: their class does not exist in the new year yet (run the classes step first)");
		}
		if (yearShift == 0) {
			notes.add("Both years start in the same calendar year, so installment due dates were not moved - check them");
		}
		return RolloverStep.of(this, List.of(groups, lines, installments), notes);
	}

	private static BigDecimal scale(BigDecimal amount, BigDecimal factor) {
		return amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
	}
}
