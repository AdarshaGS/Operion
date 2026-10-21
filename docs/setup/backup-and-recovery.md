# Backup and recovery

Operion's durable state is the two MySQL databases (schema managed by Flyway) and the uploads
folder (`STORAGE_BASE_DIR`: logos, stamps, signatures, documents and proof files). Redis is not used. Signing secrets (`JWT_SECRET`, `PLATFORM_JWT_SECRET`) and
gateway/email keys live in the deployment environment, not the database — keep them in a
secrets manager, and note that losing them only forces re-login / key re-issue, not data loss.

## What to back up

| Item | How | Why |
|---|---|---|
| MySQL `operion_school` and `operion_platform` databases | Built-in local backup (below) + binary logs | The whole tenant dataset — every organisation shares these two schemas |
| Uploads folder (`STORAGE_BASE_DIR`) | Built-in local backup (zipped with the dumps) | Logos, stamps, signatures and uploaded documents are not in the database |
| Deployment secrets | Secrets manager (versioned) | Needed to restart the app; not recoverable from the DB |

## Built-in local backups

The application takes its own backups (`BackupService`), so a single-server school install is covered
without extra tooling. Each backup is one folder, `BACKUP_DIR/operion-backup-<yyyyMMdd-HHmmss>/`:

| File | Contents |
|---|---|
| `school.sql.gz` | `mysqldump --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF` of the School database |
| `platform.sql.gz` | The same for the Platform database |
| `uploads.zip` | Everything under `STORAGE_BASE_DIR` |
| `manifest.json` | When it was taken, the database names, and the size and SHA-256 of each file |

- A backup is written to `<name>.partial` and renamed only after both dumps exited cleanly, end with
  mysqldump's `-- Dump completed` line and read back as valid gzip. A folder without `.partial` is complete.
  An abandoned `.partial` (crash mid-backup) is removed by the next run once it is 12 hours old.
- The database password reaches mysqldump through a private (0600) option file, never the command line.
- The two dumps run one after the other, minutes apart, not inside one transaction (see *Two databases*).
- `mysqldump` (MySQL 8 client, matching the server) must be installed on the app server; set
  `BACKUP_MYSQLDUMP` if it is not on the `PATH`. MariaDB's mysqldump does not accept `--set-gtid-purged`.

| Setting | Default | Meaning |
|---|---|---|
| `BACKUP_ENABLED` | `false` | Run the nightly backup. **Set to `true` on every real install.** |
| `BACKUP_CRON` / `BACKUP_ZONE` | `0 30 2 * * *` / server's time zone | When it runs (Spring cron: sec min hour day month weekday); set `BACKUP_ZONE` (e.g. `Asia/Kolkata`) if the server's clock is UTC but the night should be the school's |
| `BACKUP_DIR` | `./data/backups` | Where backups go — use a different disk from the database |
| `BACKUP_KEEP` | `14` | How many backups to keep; older ones are deleted after each successful backup |

**Monitoring.** Platform admin → System Health shows a **Backups** status: operational only when the
newest backup is under 26 hours old and no attempt has failed since. The Backups panel below it lists the
backups on disk, the last error, and has **Back up now** (`POST /api/v1/platform/backups`;
`GET /api/v1/platform/backups` returns the same status as JSON).

**Get copies off the machine.** A backup on the same server does not survive a dead disk or a stolen
laptop. Copy `BACKUP_DIR` elsewhere every day (an external disk, another server with `rsync`, or a
cloud bucket), encrypted — it holds personal data.

## Backup policy (production targets)

- **Full logical backup nightly**: the built-in local backup above (`BACKUP_ENABLED=true`), copied off the server. `--single-transaction` gives a consistent InnoDB snapshot without locking writes.
- **Binary logging on** (`log_bin`, `binlog_expire_logs_seconds` ≥ 7 days) for point-in-time recovery between nightlies. Target RPO: ≤ 15 minutes with binlog shipping; ≤ 24 hours without.
- **Retention**: 7 daily, 4 weekly, 12 monthly. Store off the database host, in a different account/region, encrypted at rest.
- **Managed MySQL** (RDS/Cloud SQL): enable automated snapshots + PITR instead of the above dump/binlog setup, and still take a weekly logical dump for portability.
- Backups contain personal data of students, guardians and staff (incl. bank details): encrypt, restrict access, and delete expired ones.
- Target RTO: ≤ 4 hours.

## Restoring a built-in backup

Stop the application, then run, with the same database settings the app uses:

```bash
DB_HOST=localhost DB_PORT=3306 DB_USERNAME=root DB_PASSWORD=... \
SCHOOL_DB=operion_school PLATFORM_DB=operion_platform STORAGE_BASE_DIR=/srv/operion/uploads \
  scripts/restore-backup.sh /srv/operion/backups/operion-backup-20261020-023000
```

The script checks every file against `manifest.json` (and refuses a damaged or `.partial` backup), asks you
to type `RESTORE` (or pass `--yes`), drops and recreates both databases from the dumps, and replaces the
uploads folder (the old one is kept as `<folder>.before-restore-<time>`; `SKIP_UPLOADS=1` leaves it alone).
To rehearse without touching live data, point `SCHOOL_DB` / `PLATFORM_DB` at scratch names and
`STORAGE_BASE_DIR` at a scratch folder. Dumps do not contain MySQL users: on a new server, recreate the
application login and `reporting_ro` (school/V1 migration) before starting the app. Then run the
verification checklist below.

## Restore procedure (manual / point-in-time)

1. Stop the application (or scale to zero) so nothing writes during restore.
2. Create an empty target database: `CREATE DATABASE operion_restore CHARACTER SET utf8mb4;`
3. Load the most recent full backup: `gunzip -c operion-YYYYMMDD.sql.gz | mysql operion_restore`.
4. For point-in-time recovery, replay binlogs from the backup's position up to just before the incident: `mysqlbinlog --start-position=<pos> --stop-datetime="<ts>" binlog.* | mysql operion_restore`.
5. Verify (below). Then either point `DB_URL` at `operion_restore`, or swap database names.
6. Start the app. Flyway will confirm `flyway_schema_history` matches the deployed migrations; a restore from an older backup will apply any newer migrations on boot — expected.
7. Record the incident, data-loss window and time-to-recover.

## Two databases

Operion stores data in two databases, `operion_platform` and `operion_school`, and each needs its own backup. There is no transaction across them, so take both dumps at the same point in time (or restore both to the same binlog position) — otherwise an organisation can exist without its school data, or the reverse. Restore, `DB_URL` / `PLATFORM_DB_URL` and the verification checks below apply to each database separately (`organisations` is in the platform database; `students`, `invoices`, `payments`, `audit_logs` are in the school database).

## Verification after any restore

- `SELECT COUNT(*)` on `organisations`, `students`, `invoices`, `payments`, `audit_logs` roughly matches production expectations for the backup time.
- `SELECT MAX(version) FROM flyway_schema_history WHERE success = 1` equals the newest migration the backup's build shipped.
- Log in as a known org owner and open the dashboard, a student record and a fee invoice.
- Latest `audit_logs.occurred_at` is close to the intended recovery point.

## Recovery drill (must actually be rehearsed)

A backup that has never been restored is not a backup.

- **Quarterly**: restore the latest nightly into a scratch database/instance following the steps above, run the verification checklist, and record date, backup used, elapsed time, and problems found in the log below.
- **After** any change to backup tooling, MySQL major version, or hosting: run an extra drill.
- Once a year, include a point-in-time restore, not just a full-dump restore.
- A drill that exceeds the RTO, or fails verification, is a P1 to fix before the next quarter.

### Drill log

| Date | Backup restored | Elapsed | Result / issues | Done by |
|---|---|---|---|---|
| _none yet — first drill due before first production go-live_ | | | | |

## Local/dev note

The e2e harness's throwaway database (`operion_e2e`) is not stateful data; wipe and recreate rather than restoring it.
