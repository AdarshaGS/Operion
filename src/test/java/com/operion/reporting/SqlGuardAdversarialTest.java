package com.operion.reporting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SEC-H3 follow-up: the reporting views scope rows by the MySQL session variable @reporting_org_id, so each of these
 * is a way a REPORT_CREATE user might get that variable re-pointed (or otherwise escape a single read), run against
 * the guard as an adversary. None of this can be tested against a real MySQL here - these prove the guard's verdicts,
 * not MySQL's parse; the allow-list keeps them conservative.
 */
class SqlGuardAdversarialTest {

	private static void assertRejected(String sql) {
		assertThatThrownBy(() -> SqlGuard.assertSingleSelect(sql))
				.as("should be rejected: %s", sql)
				.isInstanceOf(IllegalArgumentException.class);
	}

	// The reported bypass: MySQL reads \' as an escaped quote, so a parser that doesn't see the escape thinks the string
	// ended and the assignment that follows is code (or the reverse). Backslashes are refused outright.
	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT '\\'', (SELECT 1) FROM reporting_students WHERE '' = ' @reporting_org_id := 7 '",
			"SELECT 'a\\'' , @reporting_org_id := 7, '' FROM reporting_students",
			"SELECT * FROM reporting_students WHERE student_name = '\\' OR (SELECT @reporting_org_id := 7) -- '",
			"SELECT * FROM reporting_students WHERE student_name = \"\\\" OR (SELECT @reporting_org_id := 7) OR \"\"=\"",
			"SELECT 'x' FROM reporting_students WHERE student_name = 'Ann\\' AND '1'='1'",
			"SELECT 1 FROM reporting_students WHERE a = 'plain' AND b = '\\\\'",
	})
	void backslashEscapeTricksAreRejected(String sql) {
		assertRejected(sql);
	}

	@Test
	void aBackslashIsRejectedEvenInsideAnOtherwiseHarmlessLiteral() {
		assertRejected("SELECT 'O\\'Brien' FROM reporting_students");
		assertRejected("SELECT 'a\\nb' FROM reporting_students");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT @reporting_org_id FROM reporting_students",
			"SELECT s.* FROM (SELECT @reporting_org_id := 7) x, reporting_students s",
			"SELECT s.* FROM (SELECT @REPORTING_ORG_ID:=7) x, reporting_students s",
			"SELECT s.* FROM (SELECT @`reporting_org_id` := 7) x, reporting_students s",
			"SELECT s.* FROM (SELECT @'reporting_org_id' := 7) x, reporting_students s",
			"SELECT s.* FROM (SELECT @\"reporting_org_id\" := 7) x, reporting_students s",
			"SELECT @a := 1",
			"SELECT 1 INTO @a",
			"SELECT 7 INTO @reporting_org_id",
			"SELECT @@global.sql_mode",
			"SELECT @@session.sql_mode",
			"SELECT s.* FROM reporting_students s WHERE (@x := 1)",
			"SELECT 1 WHERE @x:=1",
	})
	void userVariablesAndAssignmentsAreRejected(String sql) {
		assertRejected(sql);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT 'reporting_org_id' FROM reporting_students",
			"SELECT `reporting_org_id` FROM reporting_students",
			"SELECT REPORTING_ORG_ID() FROM reporting_students",
			"SELECT * FROM reporting_students WHERE student_name = 'Reporting_Org_Id'",
	})
	void theOrganisationScopeNameIsRejectedAnywhereEvenInsideLiterals(String sql) {
		assertRejected(sql);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT 1 /*! , @reporting_org_id := 7 */",
			"SELECT /*!50000 @a := 1 */ 1",
			"SELECT /*+ MAX_EXECUTION_TIME(1000000) */ 1 FROM reporting_students",
			"SELECT 1 /**/ FROM reporting_students",
			"SEL/**/ECT 1",
			"SELECT 1 FROM reporting_students /*",
			"SELECT 1 */ FROM reporting_students",
			"SELECT 1 -- , @a := 1",
			"SELECT 1 --",
			"SELECT 1 --1",
			"SELECT 1 # , @a := 1",
			"SELECT 1 #",
			"SELECT 1\n-- ; DROP TABLE x",
	})
	void commentsOfEveryKindAreRejected(String sql) {
		assertRejected(sql);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT 1; SELECT 2",
			"SELECT 1;;",
			"SELECT 1; ; ",
			"SELECT 1;\nSET @reporting_org_id = 7",
			"SELECT 1; DROP TABLE students",
			"SELECT 1 ;SELECT 2;",
			";SELECT 1",
	})
	void multipleStatementsAreRejected(String sql) {
		assertRejected(sql);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SET @reporting_org_id = 7",
			"set @reporting_org_id = 7",
			"CALL some_proc()",
			"{call some_proc()}",
			"LOAD DATA INFILE '/etc/passwd' INTO TABLE t",
			"INSERT INTO t VALUES (1)",
			"UPDATE t SET a = 1",
			"DELETE FROM t",
			"DROP TABLE t",
			"TRUNCATE t",
			"SHOW TABLES",
			"DESCRIBE reporting_students",
			"EXPLAIN SELECT 1",
			"USE other_db",
			"PREPARE s FROM 'SELECT 1'",
			"HANDLER t OPEN",
			"(SELECT 1)",
			"   SELECT 1",
			"\u0000SELECT 1",
			"   ",
			"",
	})
	void statementsThatAreNotASelectOrWithAreRejected(String sql) {
		assertRejected(sql);
	}

	@Test
	void nullIsRejected() {
		assertRejected(null);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"WITH x AS (SELECT 1) UPDATE t SET a = 1",
			"WITH x AS (SELECT 1) DELETE FROM t",
			"WITH x AS (SELECT 1) INSERT INTO t SELECT * FROM x",
			"SELECT * INTO OUTFILE '/tmp/x' FROM reporting_students",
			"SELECT * FROM reporting_students INTO OUTFILE '/tmp/x'",
			"SELECT * FROM reporting_students INTO DUMPFILE '/tmp/x'",
			"SELECT student_name FROM reporting_students INTO @v",
			"SELECT student_name INTO @v FROM reporting_students",
			"SELECT * FROM reporting_students FOR UPDATE",
			"SELECT * FROM reporting_students FOR SHARE",
			"SELECT * FROM reporting_students LOCK IN SHARE MODE",
			"SELECT * FROM reporting_students PROCEDURE ANALYSE()",
			"SELECT LOAD_FILE('/etc/passwd')",
			"SELECT SLEEP(100)",
			"SELECT sleep (100) FROM reporting_students",
			"SELECT BENCHMARK(100000000, MD5('x'))",
			"SELECT GET_LOCK('a', 100)",
			"SELECT RELEASE_LOCK('a')",
			"SELECT IS_FREE_LOCK('a')",
			"SELECT IS_USED_LOCK('a')",
			"SELECT RELEASE_ALL_LOCKS()",
			"SELECT MASTER_POS_WAIT('a', 1, 100)",
			"SELECT WAIT_FOR_EXECUTED_GTID_SET('a', 100)",
	})
	void dataModifyingLockingFileAndSleepConstructsAreRejected(String sql) {
		assertRejected(sql);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT {fn NOW()}",
			"SELECT {d '2024-01-01'}",
			"SELECT 1 {call x()}",
	})
	void jdbcEscapeSyntaxIsRejectedBecauseTheDriverRewritesItBeforeMySql(String sql) {
		assertRejected(sql);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"SELECT 'unterminated",
			"SELECT \"unterminated",
			"SELECT `unterminated",
			"SELECT 'a''",
			"SELECT 1 FROM reporting_students WHERE a = 'x' AND b = 'y",
			"SELECT 1; '",
	})
	void unterminatedQuotesAreRejectedRatherThanGuessed(String sql) {
		assertRejected(sql);
	}

	@Test
	void controlCharactersAreRejected() {
		assertRejected("SELECT 1\u0000, @a := 1");
		assertRejected("SELECT 1\u001a");
		assertRejected("SELECT 1\u000b");
		assertRejected("SELECT 'a\u0000b'");
	}

	@Test
	void anExcessivelyLongQueryIsRejected() {
		assertRejected("SELECT 1 " + " ".repeat(25_000) + "FROM reporting_students WHERE a = 1 " + "AND a = 1 ".repeat(100));
	}

	@Test
	void aSecondStatementCannotHideBehindADoubledQuoteThatLooksLikeALiteralEnd() {
		// '' inside a literal is an escaped quote in MySQL too, so this is one literal followed by nothing.
		assertThatCode(() -> SqlGuard.assertSingleSelect("SELECT 'it''s; SET @a := 1' FROM reporting_students"))
				.doesNotThrowAnyException();
		// ...but the same text with the doubled quote closing the literal early exposes the statement separator.
		assertRejected("SELECT 'it'; SET @a := 1 ''' FROM reporting_students");
		assertRejected("SELECT 'it''' ; SET @a := 1 --'");
	}

	@Test
	void backtickAndDoubleQuoteIdentifiersAreMaskedLikeLiterals() {
		assertThatCode(() -> SqlGuard.assertSingleSelect("SELECT `select`, `a``b`, \"x\"\"y\" FROM reporting_students"))
				.doesNotThrowAnyException();
		assertRejected("SELECT `a` `b` @x := 1");
		assertRejected("SELECT `a`; DROP TABLE x");
	}

	@Test
	void legitimateReportsStillPass() {
		assertThatCode(() -> {
			SqlGuard.assertSingleSelect("SELECT * FROM reporting_students WHERE academic_year_id = :yearId");
			SqlGuard.assertSingleSelect("  select class_name, COUNT(*) AS n from reporting_students group by class_name;  ");
			SqlGuard.assertSingleSelect("SELECT * FROM reporting_staff WHERE email = 'head@school.org' AND note <> 'a := b'");
			SqlGuard.assertSingleSelect("SELECT 'a;b', 'x -- y', 'p # q', '/* c */' FROM reporting_students");
			SqlGuard.assertSingleSelect("SELECT * FROM reporting_students WHERE student_name = 'O''Brien'");
			SqlGuard.assertSingleSelect("SELECT * FROM reporting_students WHERE student_name = 'Zoë 中文'");
			SqlGuard.assertSingleSelect("WITH a AS (SELECT class_name FROM reporting_students) SELECT class_name FROM a");
			SqlGuard.assertSingleSelect("SELECT REPLACE(student_name, 'a', 'b') AS name FROM reporting_students");
			SqlGuard.assertSingleSelect("SELECT 5 - -3, 10 / 2, a * b FROM reporting_students");
			SqlGuard.assertSingleSelect("SELECT student_name AS `update`, 'delete' AS d FROM reporting_students");
		}).doesNotThrowAnyException();
	}

	/** Every built-in report must keep passing - a guard that rejects the seeded reports would break them in production. */
	@Test
	@SuppressWarnings("unchecked")
	void everySeededStandardReportPassesTheGuard() throws Exception {
		Field field = SavedReportService.class.getDeclaredField("STANDARD_REPORTS");
		field.setAccessible(true);
		List<Record> definitions = (List<Record>) field.get(null);
		Method sqlQuery = definitions.get(0).getClass().getDeclaredMethod("sqlQuery");
		sqlQuery.setAccessible(true);
		for (Record definition : definitions) {
			String sql = (String) sqlQuery.invoke(definition);
			assertThatCode(() -> SqlGuard.assertSingleSelect(sql)).as(sql).doesNotThrowAnyException();
		}
	}
}
