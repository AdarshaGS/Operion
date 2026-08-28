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
}
