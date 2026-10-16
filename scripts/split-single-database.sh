#!/bin/bash
# One-off: copies the data of a pre-split single Operion database into the two new databases
# (Platform DB + School DB). Run once per environment that already has data; a brand-new
# environment does not need it - just start the app and Flyway builds both empty schemas.
#
#   scripts/split-single-database.sh <source_db> <school_db> <platform_db>
#   e.g. scripts/split-single-database.sh operion operion_school operion_platform
#
# Prerequisites:
#   1. Both target databases exist and have been migrated - create them empty
#      (CREATE DATABASE ... CHARACTER SET utf8mb4), start the app once against them (it runs
#      Flyway for each), then stop it.
#   2. The target databases hold only the Flyway reference data (no school data yet).
# The source database is only read, never modified - keep it as the backup until you are happy.
#
# Connection: MYSQL_ARGS (default "-uroot"), e.g. MYSQL_ARGS="-uroot -psecret -h127.0.0.1".
set -euo pipefail

SRC=${1:?source database}
SCHOOL=${2:?school database}
PLATFORM=${3:?platform database}
MYSQL_ARGS=${MYSQL_ARGS:--uroot}

PLATFORM_TABLES="crm_activities crm_contacts crm_leads crm_tasks external_services organisation_external_services organisation_module_settings organisations plans platform_admins platform_invoices platform_settings subscriptions support_tickets timezones"

q() { mysql $MYSQL_ARGS -N -B "$@"; }

exists() { [ "$(q -e "select count(*) from information_schema.tables where table_schema='$1' and table_name='$2'")" = "1" ]; }

# Identities are per school now: a user with memberships in more than one organisation would
# have to be split into one account per school, with every reference to it remapped. Refuse
# rather than guess.
multi=$(q "$SRC" -e "select count(*) from (select user_id from organisation_memberships group by user_id having count(distinct organisation_id) > 1) t")
if [ "$multi" != "0" ]; then
  echo "ERROR: $multi user(s) belong to more than one organisation; split them manually first." >&2
  exit 1
fi

dump() { # dump <target_db> <table...>  (from $SRC, REPLACE so reference rows are overwritten)
  local target=$1; shift
  { echo "SET FOREIGN_KEY_CHECKS=0; SET UNIQUE_CHECKS=0; SET sql_mode='NO_AUTO_VALUE_ON_ZERO';"
    mysqldump $MYSQL_ARGS --no-create-info --replace --complete-insert --skip-comments --skip-triggers \
      --no-tablespaces --hex-blob --skip-add-locks --skip-disable-keys "$SRC" "$@"
    echo "SET FOREIGN_KEY_CHECKS=1;"; } | mysql $MYSQL_ARGS "$target"
}

echo "== Platform tables -> $PLATFORM"
present=()
for t in $PLATFORM_TABLES; do exists "$SRC" "$t" && present+=("$t"); done
dump "$PLATFORM" "${present[@]}"

echo "== School tables -> $SCHOOL"
school=()
for t in $(q -e "select table_name from information_schema.tables where table_schema='$SRC' and table_type='BASE TABLE' order by 1"); do
  case " $PLATFORM_TABLES flyway_schema_history users login_attempts " in *" $t "*) continue;; esac
  exists "$SCHOOL" "$t" && school+=("$t")
done
dump "$SCHOOL" "${school[@]}"

echo "== users (organisation_id taken from the user's membership)"
cols=$(q -e "select group_concat(concat('\`',column_name,'\`') order by ordinal_position) from information_schema.columns where table_schema='$SRC' and table_name='users'")
ucols=$(echo "$cols" | sed 's/`//g; s/,/,u./g; s/^/u./')
q -e "SET FOREIGN_KEY_CHECKS=0;
  REPLACE INTO \`$SCHOOL\`.users (organisation_id, $cols)
  SELECT (SELECT MIN(m.organisation_id) FROM \`$SRC\`.organisation_memberships m WHERE m.user_id = u.id), $ucols
  FROM \`$SRC\`.users u
  WHERE EXISTS (SELECT 1 FROM \`$SRC\`.organisation_memberships m WHERE m.user_id = u.id);"
orphans=$(q -e "select count(*) from \`$SRC\`.users u where not exists (select 1 from \`$SRC\`.organisation_memberships m where m.user_id=u.id)")
echo "   users without a membership (not copied, no school to belong to): $orphans"

echo "== row counts (source -> target)"
for t in organisations plans subscriptions; do
  echo "   $t: $(q -e "select count(*) from \`$SRC\`.$t") -> $(q -e "select count(*) from \`$PLATFORM\`.$t")"
done
for t in users students roles organisation_memberships; do
  exists "$SRC" "$t" && echo "   $t: $(q -e "select count(*) from \`$SRC\`.$t") -> $(q -e "select count(*) from \`$SCHOOL\`.$t")"
done
echo "Done. login_attempts and refresh tokens were not special-cased: failed-login counters restart empty."
