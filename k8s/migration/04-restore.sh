#!/usr/bin/env bash
# Loads the backup into the new namespace. Idempotent: each database is dropped
# and recreated, so re-running it is safe and overwrites any schema the app may
# have created in the meantime.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

BACKUP_DIR="${BACKUP_DIR:-$(cat k8s/migration/.last-backup 2>/dev/null || true)}"
[ -n "$BACKUP_DIR" ] && [ -d "$BACKUP_DIR" ] || die "set BACKUP_DIR to the directory written by 02-freeze-and-backup.sh"
load_plan
print_plan
say "restoring from $BACKUP_DIR"

# The migrated app tier must be down: PostgreSQL refuses to drop a database with
# live connections, and a half-restored schema behind a running backend is worse
# than an outage. Deployments already in $DST_NS are neither checked nor touched
# — none of their databases are being restored.
for d in ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}; do
  n="$(kubectl -n "$DST_NS" get "deploy/$d" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo 0)"
  if [ "${n:-0}" != "0" ]; then
    confirm "deploy/$d is at $n replicas in $DST_NS. Scale the migrated app tier to 0 and continue?"
    scale_deploys "$DST_NS" 0 ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}
    wait_gone    "$DST_NS" ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}
    break
  fi
done

restore_db() {
  local deploy="$1" dump="$2"
  [ -s "$dump" ] || die "$dump missing or empty"
  say "recreating the database inside $deploy"
  kubectl -n "$DST_NS" rollout status "deploy/$deploy" --timeout=5m >/dev/null
  # Connects to the `postgres` maintenance database so the target can be dropped.
  # One psql invocation per statement: DROP DATABASE cannot run inside a
  # transaction block, and whether repeated -c flags share one implicit
  # transaction is version-dependent. WITH (FORCE) (PostgreSQL 13+) terminates
  # any straggler connection itself, so no pg_terminate_backend dance is needed.
  kubectl -n "$DST_NS" exec "deploy/$deploy" -- sh -c '
    set -e
    export PGPASSWORD="$POSTGRES_PASSWORD"
    psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
      -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE);"
    psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
      -c "CREATE DATABASE \"$POSTGRES_DB\" OWNER \"$POSTGRES_USER\";"
  '
  say "pg_restore $dump -> $deploy"
  # --no-owner/--no-acl: role names need not match across the two clusters.
  kubectl -n "$DST_NS" exec -i "deploy/$deploy" -- sh -c '
    export PGPASSWORD="$POSTGRES_PASSWORD"
    pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error
  ' < "$dump"
}
# Only databases that were actually dumped from $SRC_NS. A database already
# living in $DST_NS is never dropped — its data is already in the right place.
for d in ${MIGRATE_DB_A[@]+"${MIGRATE_DB_A[@]}"}; do
  restore_db "$d" "$BACKUP_DIR/$(dump_name_for "$d")"
done

for v in ${MIGRATE_DATA_PVCS_A[@]+"${MIGRATE_DATA_PVCS_A[@]}"}; do
  say "restoring pvc/$v"
  if [ ! -s "$BACKUP_DIR/$v.tar" ]; then
    warn "$v.tar is empty — nothing to restore"
    continue
  fi
  pod="$(helper_pod_start "$DST_NS" "$v")"
  kubectl -n "$DST_NS" exec -i "$pod" -- tar xf - -C /data < "$BACKUP_DIR/$v.tar"
  say "restored file count: $(kubectl -n "$DST_NS" exec "$pod" -- sh -c 'find /data -type f | wc -l')"
  helper_pod_stop "$DST_NS" "$pod"
done

# pvc/logs is intentionally not restored — the archive in $BACKUP_DIR is the
# historical record and the new volume starts clean.

say "restore complete. Next: 05-resume.sh"
