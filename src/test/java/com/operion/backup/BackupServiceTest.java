package com.operion.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.operion.backup.BackupService.BackupStatus;
import com.operion.backup.BackupService.BackupSummary;
import com.operion.backup.BackupService.DatabaseTarget;

import tools.jackson.databind.json.JsonMapper;

/** Runs BackupService against a fake mysqldump script, so the real process handling is exercised without MySQL. */
class BackupServiceTest {

	private static final String PASSWORD = "s3cret\"pass";

	@TempDir
	Path temp;

	private Path backups;
	private Path uploads;
	private MutableClock clock;

	@BeforeEach
	void setUp() throws IOException {
		backups = temp.resolve("backups");
		uploads = Files.createDirectories(temp.resolve("uploads"));
		Files.createDirectories(uploads.resolve("logos"));
		Files.writeString(uploads.resolve("logos/school.png"), "png-bytes");
		clock = new MutableClock(Instant.parse("2026-10-20T02:30:00Z"));
	}

	@Test
	void writesBothDumpsTheUploadsAndAManifestAndKeepsThePasswordOffTheCommandLine() throws IOException {
		Path dump = script("fake-dump", """
				echo "$@" > "$(dirname "$0")/args-$(echo "$@" | sed 's/.* //')"
				for a in "$@"; do case "$a" in --defaults-extra-file=*) cp "${a#*=}" "$(dirname "$0")/cnf-copy";; esac; done
				echo "-- MySQL dump 10.13"
				echo "CREATE TABLE students (id bigint);"
				echo "-- Dump completed on 2026-10-20  2:30:01"
				""");
		BackupService service = service(dump.toString(), 3);

		BackupSummary summary = service.runBackup();

		Path folder = backups.resolve(summary.name());
		assertThat(summary.name()).isEqualTo("operion-backup-20261020-023000");
		assertThat(summary.files()).extracting(BackupService.BackupFile::name)
				.containsExactly("school.sql.gz", "platform.sql.gz", "uploads.zip");
		assertThat(gunzip(folder.resolve("school.sql.gz"))).contains("CREATE TABLE students");
		assertThat(zipEntries(folder.resolve("uploads.zip"))).containsExactly("logos/school.png");
		assertThat(Files.readString(folder.resolve("manifest.json"))).contains("operion_school", "operion_platform", "sha256");
		try (Stream<Path> left = Files.list(folder)) {
			assertThat(left.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("school.sql.gz", "platform.sql.gz",
					"uploads.zip", "manifest.json");
		}

		String schoolArgs = Files.readString(temp.resolve("args-operion_school"));
		assertThat(schoolArgs).contains("--single-transaction", "--routines", "--host=db.example", "--port=3307", "--user=operion")
				.doesNotContain("s3cret");
		assertThat(Files.readString(temp.resolve("cnf-copy"))).contains("password=\"s3cret\\\"pass\"");

		BackupStatus status = service.status();
		assertThat(status.healthy()).isTrue();
		assertThat(status.lastError()).isNull();
		assertThat(status.backups()).extracting(BackupSummary::name).containsExactly(summary.name());
	}

	@Test
	void aFailingDumpLeavesNoBackupBehindAndIsReportedWithMysqldumpsOwnError() throws IOException {
		Path dump = script("failing-dump", """
				echo "mysqldump: [Warning] Using a password on the command line interface can be insecure." >&2
				echo "mysqldump: Got error: 1045: Access denied for user 'operion'@'localhost'" >&2
				exit 2
				""");
		BackupService service = service(dump.toString(), 3);

		assertThatThrownBy(service::runBackup).isInstanceOf(BackupFailedException.class)
				.hasMessageContaining("school database failed (exit 2)")
				.hasMessageContaining("Access denied");

		try (Stream<Path> left = Files.list(backups)) {
			assertThat(left).isEmpty();
		}
		BackupStatus status = service.status();
		assertThat(status.healthy()).isFalse();
		assertThat(status.lastError()).contains("Access denied");
	}

	@Test
	void aDumpThatStopsBeforeDumpCompletedIsRejected() throws IOException {
		Path dump = script("truncated-dump", """
				echo "-- MySQL dump 10.13"
				echo "CREATE TABLE students (id bigint);"
				""");
		assertThatThrownBy(service(dump.toString(), 3)::runBackup).isInstanceOf(BackupFailedException.class)
				.hasMessageContaining("incomplete");
		assertThat(service(dump.toString(), 3).listBackups()).isEmpty();
	}

	@Test
	void aMissingMysqldumpSaysSo() {
		assertThatThrownBy(service(temp.resolve("no-such-mysqldump").toString(), 3)::runBackup)
				.isInstanceOf(BackupFailedException.class)
				.hasMessageContaining("Could not start mysqldump");
	}

	@Test
	void onlyTheNewestBackupsAreKept() throws IOException {
		Path dump = script("fake-dump", "echo \"-- Dump completed\"\n");
		BackupService service = service(dump.toString(), 2);
		List<String> names = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			names.add(service.runBackup().name());
			clock.advance(Duration.ofHours(1));
		}
		assertThat(service.listBackups()).extracting(BackupSummary::name).containsExactly(names.get(2), names.get(1));
	}

	@Test
	void twoBackupsInTheSameSecondGetDistinctNames() throws IOException {
		BackupService service = service(script("fake-dump", "echo \"-- Dump completed\"\n").toString(), 5);
		String first = service.runBackup().name();
		String second = service.runBackup().name();
		assertThat(second).isEqualTo(first + "-2");
	}

	@Test
	void aBackupOlderThanADayIsNoLongerHealthy() throws IOException {
		BackupService service = service(script("fake-dump", "echo \"-- Dump completed\"\n").toString(), 3);
		assertThat(service.isHealthy()).as("no backup yet").isFalse();
		service.runBackup();
		clock.advance(Duration.ofHours(25));
		assertThat(service.isHealthy()).isTrue();
		clock.advance(Duration.ofHours(2));
		assertThat(service.isHealthy()).isFalse();
	}

	@Test
	void aNonMysqlUrlIsRefusedWithAClearMessage() {
		DatabaseTarget h2 = new DatabaseTarget("school", "jdbc:h2:mem:test", "sa", "");
		assertThatThrownBy(() -> BackupService.parse(h2)).isInstanceOf(BackupFailedException.class)
				.hasMessageContaining("Only jdbc:mysql://");
	}

	private BackupService service(String mysqldump, int keep) {
		List<DatabaseTarget> databases = List.of(
				new DatabaseTarget("school", "jdbc:mysql://db.example:3307/operion_school?useSSL=false", "operion", PASSWORD),
				new DatabaseTarget("platform", "jdbc:mysql://db.example:3307/operion_platform", "operion", PASSWORD));
		return new BackupService(databases, backups, uploads, keep, true, "0 30 2 * * *", mysqldump, JsonMapper.builder().build(), clock);
	}

	private Path script(String name, String body) throws IOException {
		Path script = temp.resolve(name);
		Files.writeString(script, "#!/bin/sh\n" + body);
		Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
		return script;
	}

	private static String gunzip(Path file) throws IOException {
		try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<String> zipEntries(Path file) throws IOException {
		List<String> names = new ArrayList<>();
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
				names.add(entry.getName());
			}
		}
		return names;
	}

	private static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
