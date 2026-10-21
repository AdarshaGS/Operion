package com.operion.reporting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * SqlGuard is app-level defence in depth, not the real security boundary (that's the
 * restricted reporting_ro DB role, V55) - but a regression here would still let an
 * obviously bad report query reach the execution engine, so it gets its own check.
 */
class SqlGuardTest {

	@Test
	void allowsAPlainSelect() {
		assertThatCode(() -> SqlGuard.assertSingleSelect("SELECT * FROM reporting.students WHERE academic_year_id = :yearId"))
				.doesNotThrowAnyException();
	}

	@Test
	void allowsATrailingSemicolon() {
		assertThatCode(() -> SqlGuard.assertSingleSelect("SELECT 1;")).doesNotThrowAnyException();
	}

	@Test
	void rejectsEmptyQuery() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("   ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANonSelectStatement() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("UPDATE students SET status = 'ACTIVE'"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsASecondStatementAfterASemicolon() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT 1; DROP TABLE students"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsADataModifyingKeywordEvenInsideASelectShapedQuery() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT * FROM students WHERE 1=1; DELETE FROM students"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsALineCommentHidingASecondStatement() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT 1 -- ; DROP TABLE students"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlockComment() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT 1 /* comment */")).isInstanceOf(IllegalArgumentException.class);
	}

	// SEC-H3: the views scope rows by @reporting_org_id, so a query that reassigns it reads another school's data.
	@Test
	void rejectsReassigningTheOrganisationVariableInline() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect(
				"SELECT s.* FROM (SELECT @reporting_org_id := 7) x, reporting_students s"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsSelectIntoAVariable() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT 7 INTO @reporting_org_id"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAHashComment() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT 1 # hidden")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsWritingToAFile() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT student_name FROM reporting_students INTO OUTFILE '/tmp/x'"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aBackslashCannotHideAnAssignmentInsideWhatLooksLikeAString() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT 'x\\', @reporting_org_id := 7, '' FROM reporting_students"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void harmlessWordsInValuesAliasesAndTheReplaceFunctionAreAllowed() {
		assertThatCode(() -> {
			SqlGuard.assertSingleSelect("SELECT student_name FROM reporting_students WHERE student_name = 'Update me'");
			SqlGuard.assertSingleSelect("SELECT REPLACE(student_name, 'a', 'b') AS name FROM reporting_students LIMIT 1");
			SqlGuard.assertSingleSelect("SELECT class_name AS `set` FROM reporting_students LIMIT 1");
		}).doesNotThrowAnyException();
	}

	@Test
	void forUpdateIsStillRejected() {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect("SELECT student_id FROM reporting_students FOR UPDATE"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void stillAllowsAtSignsAndColonsInsideQuotedValues() {
		assertThatCode(() -> SqlGuard.assertSingleSelect(
				"SELECT * FROM reporting_staff WHERE email = 'head@school.org' AND note <> 'a := b' AND `into` = :yearId"))
				.doesNotThrowAnyException();
	}
}
