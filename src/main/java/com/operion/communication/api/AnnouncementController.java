package com.operion.communication.api;

import java.util.List;

import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import com.operion.communication.Announcement;
import com.operion.communication.AnnouncementRepository;
import com.operion.communication.AnnouncementStatus;
import com.operion.communication.AudienceType;
import com.operion.communication.CommunicationService;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.student.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

	private final CommunicationService communicationService;
	private final AnnouncementRepository announcementRepository;
	private final CampusRepository campusRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final StudentRepository studentRepository;

	public AnnouncementController(CommunicationService communicationService, AnnouncementRepository announcementRepository,
			CampusRepository campusRepository, SchoolClassRepository schoolClassRepository,
			SectionRepository sectionRepository, StudentRepository studentRepository) {
		this.communicationService = communicationService;
		this.announcementRepository = announcementRepository;
		this.campusRepository = campusRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.studentRepository = studentRepository;
	}

	@PostMapping
	public AnnouncementResponse create(@RequestBody CreateAnnouncementRequest request) {
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		AudienceType audienceType = AudienceType.valueOf(request.audienceType());
		validateAudienceRef(audienceType, request.audienceRefId());

		Announcement announcement = communicationService.createAnnouncement(
				campus, request.title(), request.body(), audienceType, request.audienceRefId());
		return AnnouncementResponse.from(announcement);
	}

	@PostMapping("/{id}/publish")
	public AnnouncementResponse publish(@PathVariable Long id) {
		return AnnouncementResponse.from(communicationService.publishAnnouncement(findAnnouncement(id)));
	}

	@PostMapping("/{id}/cancel")
	public AnnouncementResponse cancel(@PathVariable Long id) {
		return AnnouncementResponse.from(communicationService.cancelAnnouncement(findAnnouncement(id)));
	}

	/** Feed of published announcements, optionally narrowed to one campus. */
	@GetMapping
	public List<AnnouncementResponse> feed(@RequestParam(required = false) Long campusId) {
		List<Announcement> announcements = campusId != null
				? announcementRepository.findByCampusIdAndStatus(campusId, AnnouncementStatus.PUBLISHED)
				: announcementRepository.findByStatus(AnnouncementStatus.PUBLISHED);
		return announcements.stream().map(AnnouncementResponse::from).toList();
	}

	private Announcement findAnnouncement(Long id) {
		return announcementRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No announcement with id " + id));
	}

	private void validateAudienceRef(AudienceType audienceType, Long audienceRefId) {
		switch (audienceType) {
			case ORG, CAMPUS -> {
			}
			case CLASS -> schoolClassRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No school class with id " + audienceRefId));
			case SECTION -> sectionRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No section with id " + audienceRefId));
			case INDIVIDUAL -> studentRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No student with id " + audienceRefId));
		}
	}
}
