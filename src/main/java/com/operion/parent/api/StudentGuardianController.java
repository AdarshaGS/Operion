package com.operion.parent.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.parent.Guardian;
import com.operion.parent.GuardianRelationshipType;
import com.operion.parent.GuardianRepository;
import com.operion.parent.ParentService;
import com.operion.parent.StudentGuardian;
import com.operion.parent.StudentGuardianRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequirePermission("GUARDIAN_VIEW")
public class StudentGuardianController {

	private final ParentService parentService;
	private final StudentGuardianRepository studentGuardianRepository;
	private final StudentRepository studentRepository;
	private final GuardianRepository guardianRepository;

	public StudentGuardianController(ParentService parentService, StudentGuardianRepository studentGuardianRepository,
			StudentRepository studentRepository, GuardianRepository guardianRepository) {
		this.parentService = parentService;
		this.studentGuardianRepository = studentGuardianRepository;
		this.studentRepository = studentRepository;
		this.guardianRepository = guardianRepository;
	}

	@PostMapping("/api/v1/students/{studentId}/guardians")
	@RequirePermission("GUARDIAN_MANAGE")
	public StudentGuardianResponse link(@PathVariable Long studentId, @RequestBody LinkGuardianRequest request) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));
		Guardian guardian = guardianRepository.findById(request.guardianId())
				.orElseThrow(() -> new IllegalArgumentException("No guardian with id " + request.guardianId()));

		StudentGuardian link = parentService.linkGuardian(student, guardian,
				GuardianRelationshipType.valueOf(request.relationshipType()), request.primaryGuardian(),
				request.emergencyContact(), request.canPickup(), request.canReceiveCommunication(), request.contactPriority());
		return StudentGuardianResponse.from(link);
	}

	@PatchMapping("/api/v1/students/{studentId}/guardians/{studentGuardianId}")
	@RequirePermission("GUARDIAN_MANAGE")
	public StudentGuardianResponse update(@PathVariable Long studentId, @PathVariable Long studentGuardianId,
			@RequestBody UpdateGuardianRelationshipRequest request) {
		StudentGuardian studentGuardian = studentGuardianRepository.findById(studentGuardianId)
				.orElseThrow(() -> new IllegalArgumentException("No student-guardian link with id " + studentGuardianId));

		StudentGuardian updated = parentService.updateRelationship(studentGuardian,
				GuardianRelationshipType.valueOf(request.relationshipType()), request.primaryGuardian(),
				request.emergencyContact(), request.canPickup(), request.canReceiveCommunication(), request.contactPriority());
		return StudentGuardianResponse.from(updated);
	}

	@GetMapping("/api/v1/students/{studentId}/guardians")
	public List<StudentGuardianResponse> listForStudent(@PathVariable Long studentId) {
		return studentGuardianRepository.findByStudentId(studentId).stream().map(StudentGuardianResponse::from).toList();
	}

	@GetMapping("/api/v1/guardians/{guardianId}/students")
	public List<StudentGuardianResponse> listForGuardian(@PathVariable Long guardianId) {
		return studentGuardianRepository.findByGuardianId(guardianId).stream().map(StudentGuardianResponse::from).toList();
	}
}
