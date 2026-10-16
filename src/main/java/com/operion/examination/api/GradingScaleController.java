package com.operion.examination.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationService.BandInput;
import com.operion.examination.GradingScale;
import com.operion.examination.GradingScaleBandRepository;
import com.operion.examination.GradingScaleRepository;
import com.operion.common.InvalidRequestException;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No dedicated GRADING_SCALE_VIEW code - reuses EXAM_VIEW for reads (grading scales are
 * exam-adjacent config), same reasoning as Section/ClassSubject reusing CLASS_VIEW. */
@RestController
@RequestMapping("/api/v1/examinations/grading-scales")
@RequirePermission("EXAM_VIEW")
public class GradingScaleController {

	private final ExaminationService examinationService;
	private final GradingScaleRepository gradingScaleRepository;
	private final GradingScaleBandRepository gradingScaleBandRepository;

	public GradingScaleController(ExaminationService examinationService, GradingScaleRepository gradingScaleRepository,
			GradingScaleBandRepository gradingScaleBandRepository) {
		this.examinationService = examinationService;
		this.gradingScaleRepository = gradingScaleRepository;
		this.gradingScaleBandRepository = gradingScaleBandRepository;
	}

	@PostMapping
	@RequirePermission("GRADING_SCALE_MANAGE")
	public GradingScaleResponse create(@Valid @RequestBody CreateGradingScaleRequest request) {
		requireUsableBands(request.bands());
		List<BandInput> bands = request.bands().stream()
				.map(entry -> new BandInput(entry.grade(), entry.minPercentage(), entry.remark()))
				.toList();

		GradingScale scale = examinationService.createGradingScale(request.name(), request.defaultScale(), bands);
		if (scale.isDefaultScale()) {
			// Only one scale is the default - the newest one takes over from any earlier default.
			gradingScaleRepository.findAll().stream()
					.filter(other -> other.isDefaultScale() && !other.getId().equals(scale.getId()))
					.forEach(other -> {
						other.clearDefault();
						gradingScaleRepository.save(other);
					});
		}
		return toResponse(scale);
	}

	@GetMapping
	public List<GradingScaleResponse> list() {
		return gradingScaleRepository.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/{scaleId}")
	public GradingScaleResponse get(@PathVariable Long scaleId) {
		GradingScale scale = gradingScaleRepository.findById(scaleId)
				.orElseThrow(() -> new IllegalArgumentException("No grading scale with id " + scaleId));
		return toResponse(scale);
	}

	/** Every percentage must resolve to a grade, so a band has to start at 0, and no two bands may start at the same percentage. */
	private void requireUsableBands(List<GradingBandEntry> bands) {
		Set<Double> minimums = new HashSet<>();
		for (GradingBandEntry band : bands) {
			if (!minimums.add(band.minPercentage())) {
				throw new InvalidRequestException("Two bands cannot start at the same percentage (" + band.minPercentage() + "%)");
			}
		}
		if (!minimums.contains(0.0)) {
			throw new InvalidRequestException("One band must start at 0 percentage so every result gets a grade");
		}
	}

	private GradingScaleResponse toResponse(GradingScale scale) {
		return GradingScaleResponse.from(scale, gradingScaleBandRepository.findByGradingScaleIdOrderByMinPercentageDesc(scale.getId()));
	}
}
