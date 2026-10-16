# Backup and recovery

Operion's only durable state is the MySQL database (schema managed by Flyway). There is no
file/object storage yet (see #196); when it lands, its bucket/volume must be added to this
procedure. Redis is not used. Signing secrets (`JWT_SECRET`, `PLATFORM_JWT_SECRET`) and
gateway/email keys live in the deployment environment, not the database — keep them in a
secrets manager, and note that losing them only forces re-login / key re-issue, not data loss.

## What to back up

| Item | How | Why |
|---|---|---|
| MySQL `operion` database | Logical dump + binary logs | The whole tenant dataset — every organisation shares one schema |
| Deployment secrets | Secrets manager (versioned) | Needed to restart the app; not recoverable from the DB |

## Backup policy (production targets)

- **Full logical backup nightly**: `mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF operion | gzip`. `--single-transaction` gives a consistent InnoDB snapshot without locking writes.
- **Binary logging on** (`log_bin`, `binlog_expire_logs_seconds` ≥ 7 days) for point-in-time recovery between nightlies. Target RPO: ≤ 15 minutes with binlog shipping; ≤ 24 hours without.
- **Retention**: 7 daily, 4 weekly, 12 monthly. Store off the database host, in a different account/region, encrypted at rest.
- **Managed MySQL** (RDS/Cloud SQL): enable automated snapshots + PITR instead of the above dump/binlog setup, and still take a weekly logical dump for portability.
- Backups contain personal data of students, guardians and staff (incl. bank details): encrypt, restrict access, and delete expired ones.
- Target RTO: ≤ 4 hours.

## Restore procedure

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
