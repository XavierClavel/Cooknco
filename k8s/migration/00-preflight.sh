#!/usr/bin/env bash
# Read-only. Records what is running today and checks the assumptions the rest
# of the runbook depends on. Run this first and read the output.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
need python3
show_context

say "workloads in $SRC_NS"
kubectl -n "$SRC_NS" get deploy,svc,ingress,pvc -o wide || true

say "cert-manager Certificates in $SRC_NS"
kubectl -n "$SRC_NS" get certificate 2>/dev/null || warn "no Certificate CRD or none present"

say "config the manifests expect but do not contain"
for s in cooknco-secrets cooknco-config mail-database-secrets cooknco-tls; do
  if kubectl -n "$SRC_NS" get secret "$s" >/dev/null 2>&1; then
    printf '  secret/%-24s present (keys: %s)\n' "$s" \
      "$(kubectl -n "$SRC_NS" get secret "$s" -o go-template='{{range $k,$v := .data}}{{$k}} {{end}}')"
  else
    warn "secret/$s MISSING in $SRC_NS — the new namespace will not start without it"
  fi
done
if kubectl -n "$SRC_NS" get configmap smtp-secrets >/dev/null 2>&1; then
  printf '  configmap/%-21s present (keys: %s)\n' smtp-secrets \
    "$(kubectl -n "$SRC_NS" get configmap smtp-secrets -o go-template='{{range $k,$v := .data}}{{$k}} {{end}}')"
else
  warn "configmap/smtp-secrets MISSING in $SRC_NS"
fi

say "who holds nodePort 30080 (must be free before the new Service is created)"
kubectl get svc --all-namespaces \
  -o jsonpath='{range .items[*]}{.metadata.namespace}/{.metadata.name}{" "}{.spec.ports[*].nodePort}{"\n"}{end}' \
  | awk '/(^| )30080( |$)/ {print "  " $1}' || true

say "storage backing the volumes to be migrated"
kubectl -n "$SRC_NS" get pvc -o custom-columns=\
NAME:.metadata.name,STATUS:.status.phase,CAPACITY:.status.capacity.storage,CLASS:.spec.storageClassName,PV:.spec.volumeName

say "Kafka topics: are the CRs in $SRC_NS actually reconciled?"
# A KafkaTopic only reconciles in the namespace of its Kafka cluster. The CRs in
# `default` are labelled for a cluster named `cooknco-kafka`, which does not
# exist — the real cluster is `kafka-kraft` in namespace `kafka`. An empty
# .status confirms they have never been acted on, so deleting them is safe.
kubectl -n "$SRC_NS" get kafkatopic -o custom-columns=\
NAME:.metadata.name,CLUSTER:.metadata.labels.strimzi\\.io/cluster,READY:.status.conditions[0].type 2>/dev/null \
  || warn "no KafkaTopic CRD or none present in $SRC_NS"
kubectl -n kafka get kafka,kafkatopic 2>/dev/null || warn "cannot read namespace kafka"

say "in-cluster DNS assumptions"
echo "  All app-to-app references use short Service names (cooknco-backend,"
echo "  cooknco-database, cooknco-redis), which resolve within whichever"
echo "  namespace the pod runs in. No application config change is required."

say "preflight complete — review warnings above before running 01-copy-config.sh"
