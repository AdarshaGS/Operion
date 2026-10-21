#!/usr/bin/env bash
# Restores one Operion local backup (written by BackupService into BACKUP_DIR) - the School and Platform
# databases and the uploads folder. See docs/setup/backup-and-recovery.md.
#
#   STOP THE APPLICATION FIRST. This drops and recreates both databases.
#
# Usage:
#   scripts/restore-backup.sh <backup-folder> [--yes]
#
# Environment (defaults match application.properties):
#   DB_HOST (localhost)  DB_PORT (3306)  DB_USERNAME (root)  DB_PASSWORD (empty)
#   SCHOOL_DB (operion_school)  PLATFORM_DB (operion_platform)
#   STORAGE_BASE_DIR (./data/uploads)  - the existing folder is kept, renamed *.before-restore-<time>
#   SKIP_UPLOADS=1 to restore the databases only
set -euo pipefail

usage() { sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

[[ $# -ge 1 ]] || usage
backup="${1%/}"
confirmed="${2:-}"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"
SCHOOL_DB="${SCHOOL_DB:-operion_school}"
PLATFORM_DB="${PLATFORM_DB:-operion_platform}"
STORAGE_BASE_DIR="${STORAGE_BASE_DIR:-./data/uploads}"

fail() { echo "restore-backup: $*" >&2; exit 1; }

[[ -d "$backup" ]] || fail "no such folder: $backup"
[[ "$backup" != *.partial ]] || fail "$backup is an unfinished backup (.partial) - pick a complete one"
[[ -f "$backup/manifest.json" ]] || fail "$backup has no manifest.json - not a complete Operion backup"
for f in school.sql.gz platform.sql.gz uploads.zip; do
	[[ -f "$backup/$f" ]] || fail "$backup/$f is missing"
done
for tool in mysql gunzip unzip; do
	command -v "$tool" >/dev/null || fail "$tool is not installed"
done
if command -v sha256sum >/dev/null; then sha() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null; then sha() { shasum -a 256 "$1" | cut -d' ' -f1; }
else fail "sha256sum or shasum is needed to check the backup"; fi

# Every file must match the checksum recorded when the backup was taken.
for f in school.sql.gz platform.sql.gz uploads.zip; do
	# One {...} entry per file; field order is not relied on.
	expected=$(tr -d '\n ' < "$backup/manifest.json" | grep -o "{[^{}]*\"name\":\"$f\"[^{}]*}" | grep -o '"sha256":"[0-9a-f]*"' | cut -d'"' -f4)
	[[ -n "$expected" ]] || fail "manifest.json has no checksum for $f"
	[[ "$(sha "$backup/$f")" == "$expected" ]] || fail "$f does not match its checksum - the backup is damaged"
	gunzip -t "$backup/$f" 2>/dev/null || [[ "$f" == *.zip ]] || fail "$f is not a valid gzip file"
done
echo "Backup $(basename "$backup") checked: all files match the manifest."

echo
echo "This will REPLACE:"
echo "  database $SCHOOL_DB   on $DB_HOST:$DB_PORT  <- school.sql.gz"
echo "  database $PLATFORM_DB on $DB_HOST:$DB_PORT  <- platform.sql.gz"
[[ "${SKIP_UPLOADS:-}" == "1" ]] || echo "  folder   $STORAGE_BASE_DIR  <- uploads.zip (current folder kept as a copy)"
if [[ "$confirmed" != "--yes" ]]; then
	read -r -p "Is the application stopped, and do you want to continue? Type RESTORE: " answer
	[[ "$answer" == "RESTORE" ]] || fail "cancelled"
fi

# The password goes in a private option file, not the command line.
cnf=$(mktemp)
chmod 600 "$cnf"
trap 'rm -f "$cnf"' EXIT
printf '[client]\nhost=%s\nport=%s\nuser=%s\npassword="%s"\n' "$DB_HOST" "$DB_PORT" "$DB_USERNAME" \
	"$(printf '%s' "$DB_PASSWORD" | sed 's/\\/\\\\/g; s/"/\\"/g')" > "$cnf"

restore_db() {
	local name=$1 file=$2
	echo "Restoring $name ..."
	mysql --defaults-extra-file="$cnf" -e "DROP DATABASE IF EXISTS \`$name\`; CREATE DATABASE \`$name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
	gunzip -c "$file" | mysql --defaults-extra-file="$cnf" --default-character-set=utf8mb4 "$name"
	local tables
	tables=$(mysql --defaults-extra-file="$cnf" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$name'")
	echo "  $name restored: $tables tables and views."
}

restore_db "$SCHOOL_DB" "$backup/school.sql.gz"
restore_db "$PLATFORM_DB" "$backup/platform.sql.gz"

if [[ "${SKIP_UPLOADS:-}" != "1" ]]; then
	if [[ -e "$STORAGE_BASE_DIR" ]]; then
		kept="$STORAGE_BASE_DIR.before-restore-$(date +%Y%m%d-%H%M%S)"
		mv "$STORAGE_BASE_DIR" "$kept"
		echo "Previous uploads kept at $kept"
	fi
	mkdir -p "$STORAGE_BASE_DIR"
	unzip -q "$backup/uploads.zip" -d "$STORAGE_BASE_DIR"
	echo "Uploads restored to $STORAGE_BASE_DIR ($(find "$STORAGE_BASE_DIR" -type f | wc -l | tr -d ' ') files)."
fi

echo
echo "Done. The reporting_ro database login is not part of a dump; if this is a new MySQL server, recreate it"
echo "(docs/setup/backup-and-recovery.md). Start the application and run the verification checklist."
