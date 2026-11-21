package com.operion.student.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Optional;

import com.operion.common.TenantContext;
import com.operion.student.Student;
import com.operion.student.StudentExit;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentExitType;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The student exit's initiatedBy is the authenticated actor (TenantContext), never a client-supplied
 * value - a body carrying its own initiatedBy must be ignored, not rejected. Standalone MockMvc with
 * mocked collaborators: no Spring context, no database.
 */
class StudentExitControllerActorTest {

	private static final long STUDENT_ID = 7L;
	private static final long ACTOR_ID = 42L;
	private static final long SPOOFED_ID = 999L;

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void aBodySuppliedInitiatedByIsIgnoredAndTheAuthenticatedActorIsRecorded() throws Exception {
		StudentService studentService = mock(StudentService.class);
		StudentRepository studentRepository = mock(StudentRepository.class);
		Student student = mock(Student.class);
		when(student.getId()).thenReturn(STUDENT_ID);
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
		StudentExit exit = mock(StudentExit.class);
		when(exit.getStudent()).thenReturn(student);
		when(exit.getExitType()).thenReturn(StudentExitType.WITHDRAWAL);
		when(exit.getExitDate()).thenReturn(LocalDate.of(2026, 3, 1));
		when(exit.getInitiatedBy()).thenReturn(ACTOR_ID);
		when(studentService.recordExit(any(), any(), any(), any(), any(), any())).thenReturn(exit);
		MockMvc mockMvc = MockMvcBuilders
				.standaloneSetup(new StudentExitController(studentService, studentRepository, mock(StudentExitRepository.class)))
				.build();

		TenantContext.set(1L, ACTOR_ID);
		mockMvc.perform(post("/api/v1/students/" + STUDENT_ID + "/exits").contentType(MediaType.APPLICATION_JSON)
						.content("{\"exitType\":\"WITHDRAWAL\",\"exitDate\":\"2026-03-01\",\"reason\":\"moved\","
								+ "\"initiatedBy\":" + SPOOFED_ID + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.initiatedBy").value(ACTOR_ID));

		verify(studentService).recordExit(eq(student), eq(StudentExitType.WITHDRAWAL), eq(LocalDate.of(2026, 3, 1)),
				eq("moved"), eq(null), eq(ACTOR_ID));
	}
}
