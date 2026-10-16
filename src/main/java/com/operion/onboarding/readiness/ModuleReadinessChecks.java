package com.operion.onboarding.readiness;

import java.util.List;

import com.operion.academic.GradeLevelRepository;
import com.operion.academic.SchoolClassRepository;
import com.operion.academic.SubjectRepository;
import com.operion.examination.GradingScaleRepository;
import com.operion.finance.FeeCategoryRepository;
import com.operion.finance.FeeStructureRepository;
import com.operion.hr.LeaveTypeRepository;
import com.operion.hr.StaffProfileRepository;
import com.operion.inventory.ItemCategoryRepository;
import com.operion.library.BookRepository;
import com.operion.modules.AppModule;
import com.operion.student.StudentRepository;
import com.operion.transport.RouteRepository;
import com.operion.transport.VehicleRepository;
import org.springframework.stereotype.Component;

/**
 * Per-module go-live checks, one small bean each, kept together because each is a few lines.
 * Configuration problems FAIL (the module cannot work); missing data only WARNs (a school may
 * legitimately go live and import later). Adding a module's checks never touches another's.
 */
final class ModuleReadinessChecks {

	private ModuleReadinessChecks() {
	}

	private static boolean none(long count) {
		return count == 0;
	}

	@Component
	static class Academics implements ReadinessCheck {
		private final GradeLevelRepository grades;
		private final SchoolClassRepository classes;
		private final SubjectRepository subjects;

		Academics(GradeLevelRepository grades, SchoolClassRepository classes, SubjectRepository subjects) {
			this.grades = grades;
			this.classes = classes;
			this.subjects = subjects;
		}

		public AppModule module() {
			return AppModule.ACADEMICS;
		}

		public List<ReadinessFinding> check() {
			return List.of(
					ReadinessFinding.of(!none(grades.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.FAIL, "Grades are set up", "No grades yet", "/academics"),
					ReadinessFinding.of(!none(classes.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.FAIL, "Classes are set up", "No classes yet", "/academics"),
					ReadinessFinding.of(!none(subjects.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.WARN, "Subjects are set up", "No subjects yet", "/academics"));
		}
	}

	@Component
	static class Students implements ReadinessCheck {
		private final StudentRepository students;

		Students(StudentRepository students) {
			this.students = students;
		}

		public AppModule module() {
			return AppModule.STUDENTS;
		}

		public List<ReadinessFinding> check() {
			return List.of(ReadinessFinding.of(!none(students.count()), ReadinessStage.DATA, ReadinessLevel.WARN, "Students are loaded",
					"No students yet - add or import them", "/students"));
		}
	}

	@Component
	static class Fees implements ReadinessCheck {
		private final FeeCategoryRepository categories;
		private final FeeStructureRepository structures;

		Fees(FeeCategoryRepository categories, FeeStructureRepository structures) {
			this.categories = categories;
			this.structures = structures;
		}

		public AppModule module() {
			return AppModule.FEES;
		}

		public List<ReadinessFinding> check() {
			return List.of(
					ReadinessFinding.of(!none(categories.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.FAIL, "Fee categories are set up",
							"No fee categories yet", "/fees/setup"),
					ReadinessFinding.of(!none(structures.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.FAIL, "Fee structures are set up",
							"No fee structure yet - fees cannot be charged", "/fees/setup"));
		}
	}

	@Component
	static class Examinations implements ReadinessCheck {
		private final GradingScaleRepository scales;

		Examinations(GradingScaleRepository scales) {
			this.scales = scales;
		}

		public AppModule module() {
			return AppModule.EXAMINATIONS;
		}

		public List<ReadinessFinding> check() {
			return List.of(ReadinessFinding.of(!none(scales.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.FAIL, "A grading scale exists",
					"No grading scale - marks cannot be turned into grades", "/examinations"));
		}
	}

	@Component
	static class Transport implements ReadinessCheck {
		private final VehicleRepository vehicles;
		private final RouteRepository routes;

		Transport(VehicleRepository vehicles, RouteRepository routes) {
			this.vehicles = vehicles;
			this.routes = routes;
		}

		public AppModule module() {
			return AppModule.TRANSPORT;
		}

		public List<ReadinessFinding> check() {
			return List.of(
					ReadinessFinding.of(!none(vehicles.count()), ReadinessStage.DATA, ReadinessLevel.WARN, "Vehicles are added", "No vehicles yet", "/transport"),
					ReadinessFinding.of(!none(routes.count()), ReadinessStage.DATA, ReadinessLevel.WARN, "Routes are added", "No routes yet", "/transport"));
		}
	}

	@Component
	static class Library implements ReadinessCheck {
		private final BookRepository books;

		Library(BookRepository books) {
			this.books = books;
		}

		public AppModule module() {
			return AppModule.LIBRARY;
		}

		public List<ReadinessFinding> check() {
			return List.of(ReadinessFinding.of(!none(books.count()), ReadinessStage.DATA, ReadinessLevel.WARN, "Books are catalogued",
					"No books yet", "/library"));
		}
	}

	@Component
	static class Hr implements ReadinessCheck {
		private final StaffProfileRepository staff;
		private final LeaveTypeRepository leaveTypes;

		Hr(StaffProfileRepository staff, LeaveTypeRepository leaveTypes) {
			this.staff = staff;
			this.leaveTypes = leaveTypes;
		}

		public AppModule module() {
			return AppModule.HR;
		}

		public List<ReadinessFinding> check() {
			return List.of(
					ReadinessFinding.of(!none(staff.count()), ReadinessStage.DATA, ReadinessLevel.WARN, "Staff records are loaded", "No staff records yet", "/hr"),
					ReadinessFinding.of(!none(leaveTypes.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.WARN, "Leave types are set up",
							"No leave types yet - leave cannot be requested", "/hr"));
		}
	}

	@Component
	static class Inventory implements ReadinessCheck {
		private final ItemCategoryRepository categories;

		Inventory(ItemCategoryRepository categories) {
			this.categories = categories;
		}

		public AppModule module() {
			return AppModule.INVENTORY;
		}

		public List<ReadinessFinding> check() {
			return List.of(ReadinessFinding.of(!none(categories.count()), ReadinessStage.CONFIGURATION, ReadinessLevel.WARN,
					"Inventory categories are set up", "No inventory categories yet", "/inventory"));
		}
	}
}
