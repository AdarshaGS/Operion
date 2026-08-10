package com.operion.examination.api;

import java.util.List;

import com.operion.examination.ExaminationService;
import com.operion.examination.ExaminationService.BandInput;
import com.operion.examination.GradingScale;
import com.operion.examination.GradingScaleBandRepository;
import com.operion.examination.GradingScaleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examinations/grading-scales")
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
	public GradingScaleResponse create(@RequestBody CreateGradingScaleRequest request) {
		List<BandInput> bands = request.bands().stream()
				.map(entry -> new BandInput(entry.grade(), entry.minPercentage(), entry.remark()))
				.toList();

		GradingScale scale = examinationService.createGradingScale(request.name(), request.defaultScale(), bands);
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

	private GradingScaleResponse toResponse(GradingScale scale) {
		return GradingScaleResponse.from(scale, gradingScaleBandRepository.findByGradingScaleIdOrderByMinPercentageDesc(scale.getId()));
	}
}
