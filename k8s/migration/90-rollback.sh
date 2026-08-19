#!/usr/bin/env bash
# Returns service to the $SRC_NS namespace. Valid at any point until
# 99-cleanup.sh has run, because everything in $SRC_NS is only scaled to zero
# until then — no data in $SRC_NS is ever deleted by 01-05.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

BACKUP_DIR="${BACKUP_DIR:-$(cat k8s/migration/.last-backup 2>/dev/null || true)}"
[ -n "$BACKUP_DIR" ] && [ -d "$BACKUP_DIR" ] || die "set BACKUP_DIR to the directory written by 02-freeze-and-backup.sh"

confirm "Roll back to $SRC_NS and stop serving from $DST_NS?"

# Free nodePort 30080 and the cooknco.eu host again, in the other direction.
say "removing the new Service and Ingress"
kubectl -n "$DST_NS" delete ingress cooknco-frontend --ignore-not-found
kubectl -n "$DST_NS" delete svc cooknco-frontend --ignore-not-found
say "scaling $DST_NS to zero"
scale_deploys "$DST_NS" 0 "${APP_DEPLOYS[@]}" "${DB_DEPLOYS[@]}"
wait_gone "$DST_NS" "${APP_DEPLOYS[@]}" "${DB_DEPLOYS[@]}"

# 03-cutover.sh deleted exactly these three; everything else in $SRC_NS was
# only scaled to zero and is still there.
say "recreating the legacy Service, Ingress and Certificate from the snapshot"
shopt -s nullglob
restored=0
for f in "$BACKUP_DIR"/restore/*.json; do
  kubectl -n "$SRC_NS" apply -f "$f"
  restored=$((restored + 1))
done
shopt -u nullglob
[ "$restored" -gt 0 ] || die "no snapshots in $BACKUP_DIR/restore — cannot rebuild the Service/Ingress"

say "restarting $SRC_NS (databases first)"
scale_deploys "$SRC_NS" 1 "${DB_DEPLOYS[@]}"
for d in "${DB_DEPLOYS[@]}"; do kubectl -n "$SRC_NS" rollout status "deploy/$d" --timeout=5m; done
scale_deploys "$SRC_NS" 1 "${APP_DEPLOYS[@]}"
for d in "${APP_DEPLOYS[@]}"; do kubectl -n "$SRC_NS" rollout status "deploy/$d" --timeout=5m; done

kubectl -n "$SRC_NS" get deploy,svc,ingress
cat <<'EOF'

Rolled back. Note that any writes made while $DST_NS was serving are NOT in
$SRC_NS — they are in the cooknco-namespace volumes, which this script left in
place. Dump them before re-attempting the migration if they matter.

Remember to revert the image-tag bump if CI pushed one, and to disable the
deploy job until you retry.
EOF
