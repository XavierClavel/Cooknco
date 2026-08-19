#!/usr/bin/env bash
# Stops the app tier (start of downtime), then takes a full backup while the
# databases are still running. Nothing is deleted here, so this step is safe to
# abandon: 90-rollback.sh brings the old namespace straight back up.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

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

confirm "Scale down the app tier in $SRC_NS? THIS STARTS THE OUTAGE."
say "stopping the app tier (databases stay up for the dumps)"
scale_deploys "$SRC_NS" 0 "${APP_DEPLOYS[@]}"
wait_gone "$SRC_NS" "${APP_DEPLOYS[@]}"

# Taken after the app tier is down, so the dumps are consistent with no writers.
dump_db() {
  local deploy="$1" out="$2"
  say "pg_dump $deploy -> $out"
  kubectl -n "$SRC_NS" exec "deploy/$deploy" -- \
    sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$out"
  [ -s "$out" ] || die "$out is empty — dump failed"
  ls -lh "$out"
}
dump_db cooknco-database      "$BACKUP_DIR/cooknco.pgc"
dump_db mail-service-database "$BACKUP_DIR/mail-service.pgc"

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
tar_pvc pictures "$BACKUP_DIR/pictures.tar"
tar_pvc logs     "$BACKUP_DIR/logs.tar"

# redis is deliberately not migrated: it is a cache/session store rebuilt on
# demand. The visible effect is that logged-in users must sign in again.

say "shutting down the database tier in $SRC_NS"
scale_deploys "$SRC_NS" 0 "${DB_DEPLOYS[@]}"
wait_gone "$SRC_NS" "${DB_DEPLOYS[@]}"

echo "$BACKUP_DIR" > k8s/migration/.last-backup
say "backup complete. Pass BACKUP_DIR=$BACKUP_DIR to 04-restore.sh (or let it read .last-backup)."
