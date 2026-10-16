package com.operion.student.api;

import com.operion.common.EnumParam;
import jakarta.validation.Valid;
import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.storage.AssetStorageService;
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
	private final AssetStorageService assetStorageService;

	public StudentDocumentController(StudentService studentService, StudentRepository studentRepository,
			StudentDocumentRepository studentDocumentRepository, AssetStorageService assetStorageService) {
		this.studentService = studentService;
		this.studentRepository = studentRepository;
		this.studentDocumentRepository = studentDocumentRepository;
		this.assetStorageService = assetStorageService;
	}

	@PostMapping
	@RequirePermission("STUDENT_DOCUMENT_MANAGE")
	public StudentDocumentResponse upload(@PathVariable Long studentId, @Valid @RequestBody UploadDocumentRequest request) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new IllegalArgumentException("No student with id " + studentId));

		StudentDocument document = studentService.addDocument(
				student, request.documentType(), request.fileReference(), request.fileName(), request.mimeType());
		return StudentDocumentResponse.from(document, assetStorageService);
	}

	@PatchMapping("/{documentId}/verify")
	@RequirePermission("STUDENT_DOCUMENT_MANAGE")
	public StudentDocumentResponse verify(
			@PathVariable Long studentId, @PathVariable Long documentId, @Valid @RequestBody VerifyDocumentRequest request) {
		StudentDocument document = studentDocumentRepository.findById(documentId)
				.filter(found -> found.getStudent().getId().equals(studentId))
				.orElseThrow(() -> new IllegalArgumentException("No document with id " + documentId));

		StudentDocument verified = studentService.verifyDocument(document,
				EnumParam.parse(DocumentVerificationStatus.class, request.verificationStatus()), request.verifiedBy());
		return StudentDocumentResponse.from(verified, assetStorageService);
	}

	@GetMapping
	public List<StudentDocumentResponse> list(@PathVariable Long studentId) {
		return studentDocumentRepository.findByStudentId(studentId).stream()
				.map(document -> StudentDocumentResponse.from(document, assetStorageService))
				.toList();
	}
}
