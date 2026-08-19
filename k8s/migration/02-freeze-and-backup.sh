#!/usr/bin/env bash
# Stops the app tier (start of downtime), then takes a full backup while the
# databases are still running. Nothing is deleted here, so this step is safe to
# abandon: 90-rollback.sh brings the old namespace straight back up.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

# Discover placement before anything is touched. Parts of the release may
# already be in $DST_NS (the mail service and its database are), and those must
# not be dumped, dropped or scaled down.
rm -f "$PLAN_FILE"
discover_plan
[ -n "${MIGRATE_APP}${MIGRATE_DB}${MIGRATE_CACHE}" ] || die "nothing left to migrate from $SRC_NS"

BACKUP_DIR="${BACKUP_DIR:-k8s/migration/backup-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$BACKUP_DIR"
say "backup directory: $BACKUP_DIR"

# Snapshot first. Two different artefacts:
#
#   restore/*.json  cleaned, individually re-appliable copies of the only three
#                   objects 03 deletes. This is what 90-rollback.sh replays.
#   inventory.yaml  the raw live export, for reference only — it is NOT
#                   re-appliable (the API server rejects a create carrying
#                   resourceVersion).
say "snapshotting the live $SRC_NS objects"
kubectl -n "$SRC_NS" get deploy,svc,ingress,pvc,configmap -o yaml > "$BACKUP_DIR/inventory.yaml"
{ echo "---"; kubectl -n "$SRC_NS" get certificate -o yaml; } \
  >> "$BACKUP_DIR/inventory.yaml" 2>/dev/null || true

mkdir -p "$BACKUP_DIR/restore"
for ref in "${CONTENDED[@]}"; do
  snapshot_object "$SRC_NS" "$ref" "$BACKUP_DIR/restore/$(echo "$ref" | tr / -).json"
done

confirm "Scale down [${MIGRATE_APP:-none}] in $SRC_NS? THIS STARTS THE OUTAGE."
say "stopping the app tier (databases stay up for the dumps)"
scale_deploys "$SRC_NS" 0 ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}
wait_gone "$SRC_NS" ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}

# Taken after the app tier is down, so the dumps are consistent with no writers.
dump_db() {
  local deploy="$1" out="$2"
  say "pg_dump $deploy -> $out"
  kubectl -n "$SRC_NS" exec "deploy/$deploy" -- \
    sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$out"
  [ -s "$out" ] || die "$out is empty — dump failed"
  ls -lh "$out"
}
for d in ${MIGRATE_DB_A[@]+"${MIGRATE_DB_A[@]}"}; do
  dump_db "$d" "$BACKUP_DIR/$(dump_name_for "$d")"
done

# `pictures` holds user uploads and is the one volume that cannot be rebuilt.
# `logs` is archived for completeness but not restored by 04.
tar_pvc() {
  local pvc="$1" out="$2"
  say "archiving pvc/$pvc -> $out"
  local pod; pod="$(helper_pod_start "$SRC_NS" "$pvc")"
  kubectl -n "$SRC_NS" exec "$pod" -- tar cf - -C /data . > "$out"
  helper_pod_stop "$SRC_NS" "$pod"
  [ -s "$out" ] || warn "$out is empty — was pvc/$pvc empty?"
  ls -lh "$out"
}
for v in ${MIGRATE_DATA_PVCS_A[@]+"${MIGRATE_DATA_PVCS_A[@]}"} \
         ${MIGRATE_ARCH_PVCS_A[@]+"${MIGRATE_ARCH_PVCS_A[@]}"}; do
  tar_pvc "$v" "$BACKUP_DIR/$v.tar"
done

# redis is deliberately not migrated: it is a cache/session store rebuilt on
# demand. The visible effect is that logged-in users must sign in again.

# redis goes down here too. Its data is not migrated (it is a cache), but
# leaving it running would keep $SRC_NS half-live and hold its volume open.
say "shutting down the database and cache tiers in $SRC_NS"
scale_deploys "$SRC_NS" 0 ${MIGRATE_DB_A[@]+"${MIGRATE_DB_A[@]}"} \
                          ${MIGRATE_CACHE_A[@]+"${MIGRATE_CACHE_A[@]}"}
wait_gone "$SRC_NS" ${MIGRATE_DB_A[@]+"${MIGRATE_DB_A[@]}"} \
                    ${MIGRATE_CACHE_A[@]+"${MIGRATE_CACHE_A[@]}"}

echo "$BACKUP_DIR" > k8s/migration/.last-backup
say "backup complete. Pass BACKUP_DIR=$BACKUP_DIR to 04-restore.sh (or let it read .last-backup)."
