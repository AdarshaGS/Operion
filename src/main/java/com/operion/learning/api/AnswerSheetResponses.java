package com.operion.learning.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.operion.examination.ExamSchedule;
import com.operion.learning.AnswerSheet;
import com.operion.learning.AnswerSheetAnnotation;
import com.operion.learning.AnswerSheetPage;
import com.operion.learning.AnswerSheetService;
import com.operion.learning.AnnotationKind;
import com.operion.learning.Labels;
import com.operion.storage.AssetStorageService;

/** Response/request shapes for on-screen marking (#230). */
public final class AnswerSheetResponses {

	private AnswerSheetResponses() {
	}

	public record ScheduleResponse(Long id, String examName, String className, String sectionName, Long subjectId,
			String subjectName, LocalDate examDate, Double maxMarks) {

		public static ScheduleResponse from(ExamSchedule s) {
			return new ScheduleResponse(s.getId(), s.getExam().getName(), Labels.className(s.getSchoolClass()),
					s.getSection() == null ? null : s.getSection().getName(), s.getSubject().getId(), s.getSubject().getName(),
					s.getExamDate(), s.getMaxMarks());
		}
	}

	public record RosterRowResponse(Long enrollmentId, Long studentId, String studentName, Integer rollNumber, String sectionName,
			Long sheetId, String status, int pages, Double totalMarks) {

		public static RosterRowResponse from(AnswerSheetService.RosterRow row) {
			var e = row.enrollment();
			AnswerSheet sheet = row.sheet();
			return new RosterRowResponse(e.getId(), e.getStudent().getId(), Labels.personName(e.getStudent().getPerson()),
					e.getRollNumber(), e.getSection().getName(), sheet == null ? null : sheet.getId(),
					sheet == null ? "NOT_UPLOADED" : sheet.getStatus().name(), row.pages(), sheet == null ? null : sheet.getTotalMarks());
		}
	}

	public record PageResponse(Long id, int pageNumber, String url) {

		static PageResponse from(AnswerSheetPage p, AssetStorageService storage) {
			return new PageResponse(p.getId(), p.getPageNumber(), Labels.url(storage, p.getAssetRef()));
		}
	}

	public record AnnotationResponse(int pageNumber, AnnotationKind kind, double x, double y, String label, Double marks) {

		static AnnotationResponse from(AnswerSheetAnnotation a) {
			return new AnnotationResponse(a.getPage().getPageNumber(), a.getKind(), a.getX(), a.getY(), a.getLabel(), a.getMarks());
		}
	}

	public record SheetResponse(Long id, String examName, String subjectName, String studentName, String status, Double maxMarks,
			Double totalMarks, String overallRemarks, Instant releasedAt, List<PageResponse> pages, List<AnnotationResponse> annotations) {

		public static SheetResponse from(AnswerSheetService.SheetDetail d, AssetStorageService storage) {
			AnswerSheet s = d.sheet();
			ExamSchedule schedule = s.getExamSchedule();
			return new SheetResponse(s.getId(), schedule.getExam().getName(), schedule.getSubject().getName(),
					Labels.personName(s.getStudentEnrollment().getStudent().getPerson()), s.getStatus().name(), schedule.getMaxMarks(),
					s.getTotalMarks(), s.getOverallRemarks(), s.getReleasedAt(),
					d.pages().stream().map(p -> PageResponse.from(p, storage)).toList(),
					d.annotations().stream().map(AnnotationResponse::from).toList());
		}
	}

	public record UploadRequest(Long enrollmentId, List<String> pageRefs) {
	}

	public record MarkRequest(List<AnnotationResponse> annotations, Double totalMarks, String overallRemarks) {
	}
}
