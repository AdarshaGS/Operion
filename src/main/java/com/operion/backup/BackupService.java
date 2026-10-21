package com.operion.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

/**
 * Local backups: a mysqldump of the School and Platform databases plus a zip of the uploaded files, written
 * as one folder per backup under app.backup.dir. A backup is built in a ".partial" folder and only renamed
 * into place once every dump exited cleanly, ends with mysqldump's "Dump completed" line and reads back as a
 * valid gzip - so a folder without ".partial" is always a complete backup. The newest app.backup.keep
 * backups are kept. scripts/restore-backup.sh restores one (docs/setup/backup-and-recovery.md).
 *
 * The DB password goes to mysqldump through a 0600 option file, never the command line, where any user on
 * the host could read it from the process list. Both dumps use --single-transaction (InnoDB-consistent
 * without locking the school out) but are taken one after the other, so they are minutes, not a
 * transaction, apart - the same trade-off the recovery doc already describes for the two databases.
 */
@Service
public class BackupService {

	private static final Logger log = LoggerFactory.getLogger(BackupService.class);

	static final String PREFIX = "operion-backup-";
	static final String PARTIAL_SUFFIX = ".partial";
	static final String MANIFEST = "manifest.json";
	static final String UPLOADS_ZIP = "uploads.zip";
	/** A daily schedule plus slack: older than this and the last backup is overdue. */
	static final Duration HEALTHY_WITHIN = Duration.ofHours(26);
	/** A ".partial" folder this old is left over from a crash, never one another process is still writing. */
	private static final Duration ABANDONED_AFTER = Duration.ofHours(12);
	private static final long DUMP_TIMEOUT_MINUTES = 120;
	private static final String DUMP_COMPLETED = "-- Dump completed";
	private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
	private static final Pattern JDBC_MYSQL = Pattern.compile("^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?/]+)");

	public record BackupFile(String name, long sizeBytes, String sha256) {
	}

	public record BackupSummary(String name, Instant createdAt, long totalBytes, List<BackupFile> files) {
	}

	public record BackupStatus(boolean scheduleEnabled, String schedule, int keep, String directory, Instant lastAttemptAt,
			String lastError, Instant lastSuccessAt, boolean healthy, List<BackupSummary> backups) {
	}

	/** What a database dump needs: where it is and how to log in. */
	record DatabaseTarget(String label, String url, String username, String password) {
	}

	private final List<DatabaseTarget> databases;
	private final Path backupDir;
	private final Path uploadsDir;
	private final int keep;
	private final boolean scheduleEnabled;
	private final String schedule;
	private final String mysqldump;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	private volatile Instant lastAttemptAt;
	private volatile String lastError;

	@Autowired
	public BackupService(@Qualifier("schoolDataSourceProperties") DataSourceProperties school,
			@Qualifier("platformDataSourceProperties") DataSourceProperties platform, ObjectMapper objectMapper,
			@Value("${app.backup.dir:./data/backups}") String backupDir,
			@Value("${app.storage.local.base-dir:./data/uploads}") String uploadsDir,
			@Value("${app.backup.keep:14}") int keep,
			@Value("${app.backup.enabled:false}") boolean scheduleEnabled,
			@Value("${app.backup.cron:0 30 2 * * *}") String schedule,
			@Value("${app.backup.mysqldump:mysqldump}") String mysqldump) {
		this(List.of(target("school", school), target("platform", platform)), Path.of(backupDir), Path.of(uploadsDir), keep,
				scheduleEnabled, schedule, mysqldump, objectMapper, Clock.systemUTC());
	}

	BackupService(List<DatabaseTarget> databases, Path backupDir, Path uploadsDir, int keep, boolean scheduleEnabled, String schedule,
			String mysqldump, ObjectMapper objectMapper, Clock clock) {
		if (keep < 1) {
			throw new IllegalArgumentException("app.backup.keep must be at least 1");
		}
		this.databases = databases;
		this.backupDir = backupDir.toAbsolutePath().normalize();
		this.uploadsDir = uploadsDir.toAbsolutePath().normalize();
		this.keep = keep;
		this.scheduleEnabled = scheduleEnabled;
		this.schedule = schedule;
		this.mysqldump = mysqldump;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	private static DatabaseTarget target(String label, DataSourceProperties properties) {
		return new DatabaseTarget(label, properties.getUrl(), properties.getUsername(), properties.getPassword());
	}

	@Scheduled(cron = "${app.backup.cron:0 30 2 * * *}", zone = "${app.backup.zone:}")
	public void scheduledBackup() {
		if (!scheduleEnabled) {
			return;
		}
		try {
			BackupSummary summary = runBackup();
			log.info("Backup {} written ({} bytes)", summary.name(), summary.totalBytes());
		} catch (BackupFailedException e) {
			log.error("Scheduled backup failed: {}", e.getMessage());
		}
	}

	/** Takes a backup now. One at a time: a second caller waits for the first to finish. */
	public synchronized BackupSummary runBackup() {
		Instant startedAt = clock.instant();
		lastAttemptAt = startedAt;
		Path work = null;
		try {
			Files.createDirectories(backupDir);
			removeAbandonedPartials();
			String name = uniqueName(startedAt);
			work = Files.createDirectory(backupDir.resolve(name + PARTIAL_SUFFIX));
			List<BackupFile> files = new ArrayList<>();
			for (DatabaseTarget database : databases) {
				files.add(dump(database, work));
			}
			files.add(zipUploads(work));
			BackupSummary summary = new BackupSummary(name, startedAt, files.stream().mapToLong(BackupFile::sizeBytes).sum(), files);
			Map<String, Object> manifest = new LinkedHashMap<>();
			manifest.put("name", name);
			manifest.put("createdAt", startedAt.toString());
			manifest.put("databases", databases.stream().map(d -> Map.of("label", d.label(), "database", parse(d).database())).toList());
			manifest.put("files", files);
			Files.writeString(work.resolve(MANIFEST), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
			Files.move(work, backupDir.resolve(name), StandardCopyOption.ATOMIC_MOVE);
			work = null;
			prune();
			lastError = null;
			return summary;
		} catch (IOException | RuntimeException e) {
			String message = e instanceof BackupFailedException ? e.getMessage() : "Backup failed: " + e.getMessage();
			lastError = message;
			throw e instanceof BackupFailedException failed ? failed : new BackupFailedException(message, e);
		} finally {
			if (work != null) {
				deleteQuietly(work);
			}
		}
	}

	/** Complete backups, newest first. */
	public List<BackupSummary> listBackups() {
		if (!Files.isDirectory(backupDir)) {
			return List.of();
		}
		try (Stream<Path> entries = Files.list(backupDir)) {
			return entries.filter(this::isCompleteBackup)
					.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
					.map(this::readManifest)
					.toList();
		} catch (IOException e) {
			throw new BackupFailedException("Cannot read the backup folder " + backupDir + ": " + e.getMessage(), e);
		}
	}

	public BackupStatus status() {
		List<BackupSummary> backups = listBackups();
		Instant lastSuccessAt = backups.isEmpty() ? null : backups.get(0).createdAt();
		boolean recent = lastSuccessAt != null && lastSuccessAt.isAfter(clock.instant().minus(HEALTHY_WITHIN));
		boolean failedSince = lastError != null && lastAttemptAt != null && (lastSuccessAt == null || lastAttemptAt.isAfter(lastSuccessAt));
		return new BackupStatus(scheduleEnabled, schedule, keep, backupDir.toString(), lastAttemptAt, lastError, lastSuccessAt,
				recent && !failedSince, backups);
	}

	/** Healthy when the newest backup is under a day (plus slack) old and nothing has failed since. */
	public boolean isHealthy() {
		try {
			return status().healthy();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private BackupFile dump(DatabaseTarget database, Path work) throws IOException {
		JdbcTarget jdbc = parse(database);
		Path target = work.resolve(database.label() + ".sql.gz");
		Path optionFile = Files.createFile(work.resolve("." + database.label() + ".cnf"),
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		Path stderr = work.resolve("." + database.label() + ".err");
		try {
			Files.writeString(optionFile, "[client]\npassword=\"" + escapeOption(database.password()) + "\"\n");
			List<String> command = List.of(mysqldump, "--defaults-extra-file=" + optionFile, "--host=" + jdbc.host(),
					"--port=" + jdbc.port(), "--user=" + (database.username() == null ? "" : database.username()), "--single-transaction",
					"--routines", "--triggers", "--no-tablespaces", "--set-gtid-purged=OFF", "--default-character-set=utf8mb4",
					jdbc.database());
			Process process;
			try {
				process = new ProcessBuilder(command).redirectError(stderr.toFile()).start();
			} catch (IOException e) {
				throw new BackupFailedException("Could not start mysqldump (" + mysqldump + ") - is it installed? " + e.getMessage(), e);
			}
			String tail;
			try (InputStream out = process.getInputStream();
					OutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
				tail = copyKeepingTail(out, gzip);
			}
			if (!process.waitFor(DUMP_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				throw new BackupFailedException("mysqldump of the " + database.label() + " database did not finish in " + DUMP_TIMEOUT_MINUTES + " minutes");
			}
			if (process.exitValue() != 0) {
				throw new BackupFailedException("mysqldump of the " + database.label() + " database failed (exit " + process.exitValue() + "): "
						+ firstLine(stderr));
			}
			if (!tail.contains(DUMP_COMPLETED)) {
				throw new BackupFailedException("The " + database.label() + " dump is incomplete: it does not end with \"" + DUMP_COMPLETED + "\"");
			}
			return fileEntry(verifyGzip(target));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BackupFailedException("Backup interrupted", e);
		} finally {
			Files.deleteIfExists(optionFile);
			Files.deleteIfExists(stderr);
		}
	}

	private BackupFile zipUploads(Path work) throws IOException {
		Path target = work.resolve(UPLOADS_ZIP);
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(target)))) {
			if (Files.isDirectory(uploadsDir)) {
				List<Path> files;
				try (Stream<Path> walk = Files.walk(uploadsDir)) {
					// The backup folder is skipped in case it was configured inside the uploads folder.
					files = walk.filter(Files::isRegularFile).filter(p -> !p.toAbsolutePath().startsWith(backupDir)).sorted().toList();
				}
				for (Path file : files) {
					ZipEntry entry = new ZipEntry(uploadsDir.relativize(file).toString().replace('\\', '/'));
					entry.setLastModifiedTime(Files.getLastModifiedTime(file));
					zip.putNextEntry(entry);
					Files.copy(file, zip);
					zip.closeEntry();
				}
			}
		}
		return fileEntry(target);
	}

	/** Copies the dump into the gzip stream and returns its last few hundred characters. */
	private static String copyKeepingTail(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[64 * 1024];
		byte[] tail = new byte[512];
		int tailLength = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
			if (read >= tail.length) {
				System.arraycopy(buffer, read - tail.length, tail, 0, tail.length);
				tailLength = tail.length;
			} else {
				int keepOld = Math.min(tailLength, tail.length - read);
				System.arraycopy(tail, tailLength - keepOld, tail, 0, keepOld);
				System.arraycopy(buffer, 0, tail, keepOld, read);
				tailLength = keepOld + read;
			}
		}
		return new String(tail, 0, tailLength, StandardCharsets.UTF_8);
	}

	/** Reads the whole file back through gzip, which checks its CRC and length. */
	private static Path verifyGzip(Path file) throws IOException {
		try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
			in.transferTo(OutputStream.nullOutputStream());
		} catch (IOException e) {
			throw new BackupFailedException("The dump " + file.getFileName() + " does not read back as valid gzip: " + e.getMessage(), e);
		}
		return file;
	}

	private BackupFile fileEntry(Path file) throws IOException {
		MessageDigest digest = sha256();
		try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
			in.transferTo(OutputStream.nullOutputStream());
		}
		return new BackupFile(file.getFileName().toString(), Files.size(file), HexFormat.of().formatHex(digest.digest()));
	}

	private void prune() throws IOException {
		List<BackupSummary> backups = listBackups();
		for (BackupSummary old : backups.subList(Math.min(keep, backups.size()), backups.size())) {
			deleteQuietly(backupDir.resolve(old.name()));
		}
	}

	private void removeAbandonedPartials() throws IOException {
		Instant cutoff = clock.instant().minus(ABANDONED_AFTER);
		try (Stream<Path> entries = Files.list(backupDir)) {
			for (Path partial : entries.filter(p -> p.getFileName().toString().endsWith(PARTIAL_SUFFIX)).toList()) {
				FileTime modified = Files.getLastModifiedTime(partial);
				if (modified.toInstant().isBefore(cutoff)) {
					deleteQuietly(partial);
				}
			}
		}
	}

	private String uniqueName(Instant at) {
		String base = PREFIX + NAME_TIME.format(at);
		String name = base;
		for (int n = 2; Files.exists(backupDir.resolve(name)) || Files.exists(backupDir.resolve(name + PARTIAL_SUFFIX)); n++) {
			name = base + "-" + n;
		}
		return name;
	}

	private boolean isCompleteBackup(Path path) {
		String name = path.getFileName().toString();
		return Files.isDirectory(path) && name.startsWith(PREFIX) && !name.endsWith(PARTIAL_SUFFIX) && Files.isRegularFile(path.resolve(MANIFEST));
	}

	private BackupSummary readManifest(Path folder) {
		try {
			Map<?, ?> manifest = objectMapper.readValue(Files.readString(folder.resolve(MANIFEST)), Map.class);
			List<BackupFile> files = ((List<?>) manifest.get("files")).stream().map(f -> objectMapper.convertValue(f, BackupFile.class)).toList();
			return new BackupSummary(folder.getFileName().toString(), Instant.parse((String) manifest.get("createdAt")),
					files.stream().mapToLong(BackupFile::sizeBytes).sum(), files);
		} catch (IOException | RuntimeException e) {
			throw new BackupFailedException("Unreadable backup manifest in " + folder.getFileName() + ": " + e.getMessage(), e);
		}
	}

	record JdbcTarget(String host, int port, String database) {
	}

	static JdbcTarget parse(DatabaseTarget database) {
		Matcher matcher = JDBC_MYSQL.matcher(database.url() == null ? "" : database.url());
		if (!matcher.find()) {
			throw new BackupFailedException("Only jdbc:mysql:// URLs can be backed up; the " + database.label() + " database URL is " + database.url());
		}
		return new JdbcTarget(matcher.group(1), matcher.group(2) == null ? 3306 : Integer.parseInt(matcher.group(2)), matcher.group(3));
	}

	private static String escapeOption(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String firstLine(Path file) throws IOException {
		if (!Files.exists(file)) {
			return "(no error output)";
		}
		// mysqldump warns about using a password on the command line etc.; the first real error is what helps.
		return Files.readAllLines(file).stream().filter(l -> !l.isBlank() && !l.contains("[Warning]")).findFirst().orElse("(no error output)");
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static void deleteQuietly(Path path) {
		try (Stream<Path> walk = Files.walk(path)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		} catch (IOException e) {
			log.warn("Could not delete {}: {}", path, e.getMessage());
		}
	}
}
