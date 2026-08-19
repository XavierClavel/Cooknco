#!/usr/bin/env bash
# The destructive step. Releases the cluster-unique resources held by $SRC_NS,
# then brings the new namespace up with its app tier held at zero replicas so
# 04-restore.sh can load the data before anything connects.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

[ -f k8s/migration/.last-backup ] || die "no backup recorded — run 02-freeze-and-backup.sh first"
say "using backup: $(cat k8s/migration/.last-backup)"

confirm "Delete the legacy Ingress, Service and Certificate from $SRC_NS?"

# nodePort 30080 and host cooknco.eu are both unique cluster-wide. The new
# Service and Ingress cannot be created while the old pair still holds them,
# so these go before the apply, not after.
say "releasing the frontend Service (nodePort 30080) and Ingress"
kubectl -n "$SRC_NS" delete ingress cooknco-frontend --ignore-not-found
kubectl -n "$SRC_NS" delete svc cooknco-frontend --ignore-not-found

# Deleting the Certificate does NOT delete cooknco-tls: 01-copy-config.sh
# stripped the ownerReference from the copy, and the original secret stays in
# $SRC_NS for rollback.
say "releasing the Certificate"
kubectl -n "$SRC_NS" delete certificate cooknco --ignore-not-found

say "applying $OVERLAY"
kubectl apply -k "$OVERLAY" --dry-run=server >/dev/null
kubectl apply -k "$OVERLAY"

# Held at zero so the backend cannot create an Ebean schema in the fresh
# database while 04-restore.sh is dropping and recreating it. (04 recreates the
# database anyway, so this is belt-and-braces rather than a hard race.)
say "holding the app tier at zero replicas until the data is restored"
scale_deploys "$DST_NS" 0 "${APP_DEPLOYS[@]}"

say "waiting for the database tier in $DST_NS"
for d in "${DB_DEPLOYS[@]}"; do
  kubectl -n "$DST_NS" rollout status "deploy/$d" --timeout=5m
done

# Applied as its own root: Strimzi's topic operator only reconciles KafkaTopics
# in the namespace of the Kafka cluster, so these belong in `kafka`, not
# `cooknco`, and the prod overlay's namespace transformer cannot express that.
say "applying Kafka topics to the kafka namespace"
kubectl apply -k k8s/kafka-topics

say "new namespace state"
kubectl -n "$DST_NS" get deploy,svc,ingress,pvc
say "cutover applied. Next: 04-restore.sh"
