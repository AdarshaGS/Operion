package com.operion.communication.api;

import java.util.List;

import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SectionRepository;
import com.operion.authorization.RequirePermission;
import com.operion.communication.Announcement;
import com.operion.communication.AnnouncementRepository;
import com.operion.communication.AnnouncementStatus;
import com.operion.communication.AudienceType;
import com.operion.communication.CommunicationService;
import com.operion.hr.StaffProfileRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
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
@RequirePermission("COMMUNICATION_VIEW")
public class AnnouncementController {

	private final CommunicationService communicationService;
	private final AnnouncementRepository announcementRepository;
	private final CampusRepository campusRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final StudentRepository studentRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final PersonRepository personRepository;

	public AnnouncementController(CommunicationService communicationService, AnnouncementRepository announcementRepository,
			CampusRepository campusRepository, SchoolClassRepository schoolClassRepository,
			SectionRepository sectionRepository, StudentRepository studentRepository,
			StaffProfileRepository staffProfileRepository, PersonRepository personRepository) {
		this.communicationService = communicationService;
		this.announcementRepository = announcementRepository;
		this.campusRepository = campusRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.studentRepository = studentRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.personRepository = personRepository;
	}

	@PostMapping
	@RequirePermission("ANNOUNCEMENT_CREATE")
	public AnnouncementResponse create(@RequestBody CreateAnnouncementRequest request) {
		Campus campus = request.campusId() == null ? null : campusRepository.findById(request.campusId())
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + request.campusId()));
		AudienceType audienceType = AudienceType.valueOf(request.audienceType());
		validateAudienceRef(audienceType, request.audienceRefId());

		List<Person> selectedGroupPersons = List.of();
		if (audienceType == AudienceType.SELECTED_GROUP) {
			List<Long> personIds = request.audienceMemberPersonIds();
			if (personIds == null || personIds.isEmpty()) {
				throw new IllegalArgumentException("SELECTED_GROUP audience requires at least one audienceMemberPersonIds entry");
			}
			selectedGroupPersons = resolvePersons(personIds);
		}

		Announcement announcement = communicationService.createAnnouncement(campus, request.title(), request.body(), audienceType,
				request.audienceRefId(), selectedGroupPersons, request.scheduledAt());
		return AnnouncementResponse.from(announcement);
	}

	/** Recipient preview/count before committing to publish - lets the compose screen show
	 * "this will reach N people" for the audience selection currently in the form, ahead of
	 * any Announcement being saved. */
	@GetMapping("/preview-audience")
	@RequirePermission("ANNOUNCEMENT_CREATE")
	public AudiencePreviewResponse previewAudience(
			@RequestParam(required = false) Long campusId,
			@RequestParam String audienceType,
			@RequestParam(required = false) Long audienceRefId,
			@RequestParam(required = false) List<Long> audienceMemberPersonIds) {
		Campus campus = campusId == null ? null : campusRepository.findById(campusId)
				.orElseThrow(() -> new IllegalArgumentException("No campus with id " + campusId));
		AudienceType type = AudienceType.valueOf(audienceType);
		validateAudienceRef(type, audienceRefId);

		List<Person> selectedGroupPersons = type == AudienceType.SELECTED_GROUP && audienceMemberPersonIds != null
				? resolvePersons(audienceMemberPersonIds)
				: List.of();
		return AudiencePreviewResponse.from(communicationService.previewAudience(campus, type, audienceRefId, selectedGroupPersons));
	}

	@PostMapping("/{id}/publish")
	@RequirePermission("ANNOUNCEMENT_PUBLISH")
	public AnnouncementResponse publish(@PathVariable Long id) {
		return AnnouncementResponse.from(communicationService.publishAnnouncement(findAnnouncement(id)));
	}

	@PostMapping("/{id}/cancel")
	@RequirePermission("ANNOUNCEMENT_CANCEL")
	public AnnouncementResponse cancel(@PathVariable Long id) {
		return AnnouncementResponse.from(communicationService.cancelAnnouncement(findAnnouncement(id)));
	}

	/** Defaults to PUBLISHED (the consumer-facing feed); an admin management view passes
	 * status=DRAFT to find its own unpublished announcements to publish or cancel. */
	@GetMapping
	public List<AnnouncementResponse> feed(
			@RequestParam(required = false) Long campusId, @RequestParam(required = false) String status) {
		AnnouncementStatus filterStatus = status != null ? AnnouncementStatus.valueOf(status) : AnnouncementStatus.PUBLISHED;
		List<Announcement> announcements = campusId != null
				? announcementRepository.findByCampusIdAndStatus(campusId, filterStatus)
				: announcementRepository.findByStatus(filterStatus);
		return announcements.stream().map(AnnouncementResponse::from).toList();
	}

	private List<Person> resolvePersons(List<Long> personIds) {
		return personIds.stream()
				.map(personId -> personRepository.findById(personId)
						.orElseThrow(() -> new IllegalArgumentException("No person with id " + personId)))
				.toList();
	}

	private Announcement findAnnouncement(Long id) {
		return announcementRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No announcement with id " + id));
	}

	private void validateAudienceRef(AudienceType audienceType, Long audienceRefId) {
		switch (audienceType) {
			case ORG, CAMPUS, STAFF, SELECTED_GROUP -> {
			}
			case CLASS -> schoolClassRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No school class with id " + audienceRefId));
			case SECTION -> sectionRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No section with id " + audienceRefId));
			case INDIVIDUAL -> studentRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No student with id " + audienceRefId));
			case STAFF_MEMBER -> staffProfileRepository.findById(audienceRefId)
					.orElseThrow(() -> new IllegalArgumentException("No staff profile with id " + audienceRefId));
		}
	}
}
