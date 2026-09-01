#!/usr/bin/env bash
# Read-only. Records what is running today and checks the assumptions the rest
# of the runbook depends on. Run this first and read the output.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
need python3
show_context

# Nothing here assumes where a component lives: parts of the release are already
# in $DST_NS (the mail service and its database), and the plan is derived from
# what is actually running.
rm -f "$PLAN_FILE"
discover_plan

say "workloads in $SRC_NS"
kubectl -n "$SRC_NS" get deploy,svc,ingress,pvc -o wide || true

say "workloads already in $DST_NS"
kubectl -n "$DST_NS" get deploy,svc,ingress,pvc -o wide 2>/dev/null \
  || echo "  namespace $DST_NS does not exist yet"

# The overlay covers the whole release, including whatever already runs in
# $DST_NS, so applying it will also adopt those objects. This shows exactly what
# that would change before any of it happens — and would surface an immutable
# field conflict (a differing PVC size, say) while it is still cheap to fix.
say "what applying the overlay would change in $DST_NS"
if kubectl get ns "$DST_NS" >/dev/null 2>&1; then
  kubectl diff -k "$OVERLAY" || true
else
  echo "  namespace $DST_NS does not exist yet — nothing to diff"
fi

say "cert-manager Certificates in $SRC_NS"
kubectl -n "$SRC_NS" get certificate 2>/dev/null || warn "no Certificate CRD or none present"

# Checked in BOTH namespaces: components already migrated brought their config
# with them, so a secret may legitimately exist only in $DST_NS. What matters is
# that it ends up in $DST_NS — 01-copy-config.sh copies whatever is only in
# $SRC_NS and leaves the rest alone.
say "config the manifests expect but do not contain"
check_config() {
  local kind="$1" name="$2" in_src="no" in_dst="no"
  kubectl -n "$SRC_NS" get "$kind" "$name" >/dev/null 2>&1 && in_src="yes"
  kubectl -n "$DST_NS" get "$kind" "$name" >/dev/null 2>&1 && in_dst="yes"
  printf '  %-10s %-24s %s=%-4s %s=%-4s' "$kind" "$name" "$SRC_NS" "$in_src" "$DST_NS" "$in_dst"
  if [ "$in_dst" = "yes" ] && [ "$in_src" = "yes" ]; then
    printf ' -> in both\n'
    # This is the trap. cooknco-secrets already exists in $DST_NS because the
    # mail service needs it, but that copy was only ever required to hold the
    # mail service's keys. 01-copy-config.sh will not overwrite it, so a key the
    # backend needs (redis-password, postgres-*) could be missing and the backend
    # would crash-loop after the cutover. Compare the key sets now.
    local src_keys dst_keys
    src_keys="$(keys_of "$SRC_NS" "$kind" "$name")"
    dst_keys="$(keys_of "$DST_NS" "$kind" "$name")"
    local missing=""
    for k in $src_keys; do
      case " $dst_keys " in *" $k "*) : ;; *) missing="$missing $k" ;; esac
    done
    if [ -n "$missing" ]; then
      warn "$kind/$name in $DST_NS is MISSING keys present in $SRC_NS:$missing"
      warn "  fix before the cutover, e.g.: kubectl -n $DST_NS delete $kind $name"
      warn "  then let 01-copy-config.sh copy the complete object across."
    else
      printf '     keys match (%s)\n' "$(echo $dst_keys | tr ' ' ',')"
    fi
  elif [ "$in_dst" = "yes" ]; then
    printf ' -> already in place\n'
  elif [ "$in_src" = "yes" ]; then
    printf ' -> will be copied by 01\n'
  else
    printf '\n'
    warn "$kind/$name is in NEITHER namespace — $DST_NS will not start without it"
  fi
}

keys_of() {
  kubectl -n "$1" get "$2" "$3" \
    -o go-template='{{range $k,$v := .data}}{{$k}} {{end}}' 2>/dev/null || true
}
for n in cooknco-secrets cooknco-config mail-database-secrets cooknco-tls; do
  check_config secret "$n"
done
check_config configmap smtp-secrets

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
