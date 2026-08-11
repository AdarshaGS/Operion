package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.student.DocumentVerificationStatus;
import com.operion.student.Student;
import com.operion.student.StudentDocument;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/students/{studentId}/documents")
@RequirePermission("STUDENT_DOCUMENT_VIEW")
public class StudentDocumentController {

	private final StudentService studentService;
	private final StudentRepository studentRepository;
	private final StudentDocumentRepository studentDocumentRepository;

	public StudentDocumentController(StudentService studentService, StudentRepository studentRepository,
			StudentDocumentRepository studentDocumentRepository) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.studentDocumentRepository = studentDocumentRepository;
	}

	@PostMapping
	@RequirePermission("STUDENT_DOCUMENT_MANAGE")
	public StudentDocumentResponse upload(@PathVariable Long studentId, @RequestBody UploadDocumentRequest request) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));

		StudentDocument document = studentService.addDocument(
				student, request.documentType(), request.fileReference(), request.fileName(), request.mimeType());
		return StudentDocumentResponse.from(document);
	}

	@PatchMapping("/{documentId}/verify")
	@RequirePermission("STUDENT_DOCUMENT_MANAGE")
	public StudentDocumentResponse verify(
			@PathVariable Long studentId, @PathVariable Long documentId, @RequestBody VerifyDocumentRequest request) {
		StudentDocument document = studentDocumentRepository.findById(documentId)
				.orElseThrow(() -> new IllegalArgumentException("No document with id " + documentId));

		StudentDocument verified = studentService.verifyDocument(
				document, DocumentVerificationStatus.valueOf(request.verificationStatus()), request.verifiedBy());
		return StudentDocumentResponse.from(verified);
	}

	@GetMapping
	public List<StudentDocumentResponse> list(@PathVariable Long studentId) {
		return studentDocumentRepository.findByStudentId(studentId).stream().map(StudentDocumentResponse::from).toList();
	}
}
