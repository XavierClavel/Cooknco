# Shared helpers for the default -> cooknco namespace migration.
# Sourced by the numbered scripts; not meant to be run directly.

set -euo pipefail

SRC_NS="${SRC_NS:-default}"
DST_NS="${DST_NS:-cooknco}"
OVERLAY="${OVERLAY:-k8s/overlays/prod}"
HELPER_IMAGE="${HELPER_IMAGE:-busybox:1.36}"

# The full catalogue. Which of these actually need migrating is discovered at
# run time, not assumed: parts of the release may already live in $DST_NS. The
# mail service and its database, for instance, are already in `cooknco`.
#
# App tier is stopped for the cutover; the database tier is handled separately
# because the dumps are taken while it is still running.
ALL_APP=(cooknco-backend cooknco-frontend cooknco-mail-service)
ALL_DB=(cooknco-database mail-service-database)
ALL_CACHE=(cooknco-redis)  # stopped with the database tier, never dumped
ALL_DATA_PVCS=(pictures)   # copied to $DST_NS
ALL_ARCH_PVCS=(logs)       # archived into the backup only, never restored
# redis-data is deliberately absent: redis is a cache, so it is not migrated.

PLAN_FILE="${PLAN_FILE:-k8s/migration/.migration-plan}"

# The three objects 03-cutover.sh must delete to free nodePort 30080 and the
# cooknco.eu host, and that 90-rollback.sh therefore has to recreate.
CONTENDED=(svc/cooknco-frontend ingress/cooknco-frontend certificate/cooknco)

say()  { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }

confirm() {
  [ "${ASSUME_YES:-0}" = "1" ] && return 0
  local reply=""
  printf '\033[1;33m%s\033[0m [y/N] ' "$1"
  read -r reply || true
  case "$reply" in y|Y|yes|YES) return 0 ;; *) die "aborted by operator" ;; esac
}

# Guard against pointing a destructive step at the wrong cluster.
show_context() {
  say "kube context: $(kubectl config current-context)"
  kubectl cluster-info 2>/dev/null | head -2 || true
}

# Strip every field bound to a live object's identity, so the result can be
# re-created cleanly. A raw `kubectl get -o yaml` cannot be fed back to `apply`:
# the API server rejects a create that carries resourceVersion, and a recorded
# clusterIP fails if the address has since been reused.
#
# Reads JSON on stdin, writes JSON on stdout. Pass "strip-ns" to drop the
# namespace too (for cross-namespace copies).
#
# ownerReferences MUST go: cooknco-tls is owned by the Certificate in the source
# namespace, and a copy that kept that reference would be garbage-collected the
# moment the old Certificate is deleted.
clean_manifest() {
  local strip_ns="${1:-keep-ns}"
  STRIP_NS="$strip_ns" python3 -c '
import json, os, sys

strip_ns = os.environ["STRIP_NS"] == "strip-ns"
DROP_META = ("uid", "resourceVersion", "generation", "creationTimestamp",
             "selfLink", "managedFields", "ownerReferences", "finalizers")
DROP_ANN  = ("kubectl.kubernetes.io/last-applied-configuration",
             "deployment.kubernetes.io/revision",
             "cert-manager.io/certificate-name",
             "cert-manager.io/certificate-revision")

def clean(o):
    m = o.setdefault("metadata", {})
    for f in DROP_META:
        m.pop(f, None)
    if strip_ns:
        m.pop("namespace", None)
    ann = m.get("annotations", {})
    for a in DROP_ANN:
        ann.pop(a, None)
    if not ann:
        m.pop("annotations", None)
    o.pop("status", None)
    # A recreated Service must be allocated a fresh cluster IP; nothing refers
    # to these by address. nodePort is deliberately kept.
    if o.get("kind") == "Service":
        for f in ("clusterIP", "clusterIPs", "ipFamilies", "ipFamilyPolicy"):
            o.get("spec", {}).pop(f, None)
    return o

d = json.load(sys.stdin)
if d.get("kind", "").endswith("List"):
    d["items"] = [clean(i) for i in d.get("items", [])]
    d.pop("metadata", None)
else:
    clean(d)
json.dump(d, sys.stdout, indent=2)
'
}

# Copy a Secret or ConfigMap between namespaces.
copy_resource() {
  local kind="$1" name="$2"
  if ! kubectl -n "$SRC_NS" get "$kind" "$name" >/dev/null 2>&1; then
    warn "$kind/$name not found in $SRC_NS — skipping"
    return 0
  fi
  if kubectl -n "$DST_NS" get "$kind" "$name" >/dev/null 2>&1; then
    say "$kind/$name already exists in $DST_NS — leaving it alone"
    return 0
  fi
  kubectl -n "$SRC_NS" get "$kind" "$name" -o json \
    | clean_manifest strip-ns \
    | kubectl -n "$DST_NS" create -f -
  say "copied $kind/$name -> $DST_NS"
}

# Write a cleaned, re-appliable manifest for one object. Used to capture the
# contended objects before 03 deletes them.
snapshot_object() {
  local ns="$1" ref="$2" out="$3"
  if ! kubectl -n "$ns" get "$ref" >/dev/null 2>&1; then
    warn "$ref not found in $ns — nothing to snapshot"
    return 0
  fi
  kubectl -n "$ns" get "$ref" -o json | clean_manifest keep-ns > "$out"
  say "snapshotted $ns/$ref -> $out"
}

# Run a throwaway pod that mounts one PVC at /data, so its contents can be
# streamed with tar. A pod can only mount PVCs from its own namespace, which is
# why the data has to travel through the operator's machine.
helper_pod_start() {
  local ns="$1" pvc="$2" pod="pvc-helper-$pvc"
  if kubectl -n "$ns" get pod "$pod" >/dev/null 2>&1; then
    kubectl -n "$ns" delete pod "$pod" --wait=true >/dev/null
  fi
  kubectl -n "$ns" run "$pod" --restart=Never --image="$HELPER_IMAGE" \
    --overrides="$(cat <<JSON
{"spec":{"containers":[{"name":"helper","image":"$HELPER_IMAGE",
  "command":["sleep","7200"],
  "volumeMounts":[{"name":"data","mountPath":"/data"}]}],
 "volumes":[{"name":"data","persistentVolumeClaim":{"claimName":"$pvc"}}]}}
JSON
)" >/dev/null
  kubectl -n "$ns" wait --for=condition=Ready "pod/$pod" --timeout=180s >/dev/null
  printf '%s' "$pod"
}

helper_pod_stop() {
  local ns="$1" pod="$2"
  kubectl -n "$ns" delete pod "$pod" --wait=false >/dev/null 2>&1 || true
}

scale_deploys() {
  local ns="$1" replicas="$2"; shift 2
  for d in "$@"; do
    if kubectl -n "$ns" get "deploy/$d" >/dev/null 2>&1; then
      kubectl -n "$ns" scale "deploy/$d" --replicas="$replicas"
    else
      warn "deploy/$d not found in $ns — skipping"
    fi
  done
}

# Waits for the pods of the NAMED deployments only. Matching on a name pattern
# instead would hang in $DST_NS, where the already-migrated mail service is
# legitimately running and must keep running. Every deployment here labels its
# pods `app: <deployment name>`.
wait_gone() {
  local ns="$1"; shift
  [ "$#" -gt 0 ] || return 0
  say "waiting for pods of [$*] to terminate in $ns"
  local tries=0 remaining d n
  while :; do
    remaining=0
    for d in "$@"; do
      n="$(kubectl -n "$ns" get pods -l "app=$d" -o name 2>/dev/null | grep -c . || true)"
      remaining=$((remaining + n))
    done
    [ "$remaining" = "0" ] && break
    tries=$((tries + 1))
    if [ "$tries" -gt 60 ]; then
      warn "$remaining pod(s) still present after 5 minutes:"
      for d in "$@"; do kubectl -n "$ns" get pods -l "app=$d" 2>/dev/null || true; done
      break
    fi
    sleep 5
  done
}

# --- what actually needs migrating -------------------------------------------
#
# bash here is 3.2 (macOS), where `set -u` makes expanding an empty array fatal.
# Every expansion of a discovered list therefore uses the ${a[@]+"${a[@]}"}
# guard, and lists are persisted as plain space-separated strings.

dump_name_for() {
  case "$1" in
    cooknco-database)      printf 'cooknco.pgc' ;;
    mail-service-database) printf 'mail-service.pgc' ;;
    *) die "no dump filename mapped for $1" ;;
  esac
}

has_deploy() { kubectl -n "$1" get "deploy/$2" >/dev/null 2>&1; }
has_pvc()    { kubectl -n "$1" get "pvc/$2"    >/dev/null 2>&1; }

# Classify every catalogued object as: to migrate (in $SRC_NS), already done
# (only in $DST_NS), or absent entirely. Writes $PLAN_FILE so that steps 03-05
# and 99 act on the same plan — re-discovering after 03 has applied the overlay
# would classify everything as "already done".
discover_plan() {
  local app="" db="" cache="" pvcs="" arch="" preserved="" absent=""
  local d
  for d in "${ALL_APP[@]}"; do
    if   has_deploy "$SRC_NS" "$d"; then app="$app $d"
    elif has_deploy "$DST_NS" "$d"; then preserved="$preserved $d"
    else absent="$absent $d"; fi
  done
  for d in "${ALL_DB[@]}"; do
    if   has_deploy "$SRC_NS" "$d"; then db="$db $d"
    elif has_deploy "$DST_NS" "$d"; then preserved="$preserved $d"
    else absent="$absent $d"; fi
  done
  for d in "${ALL_CACHE[@]}"; do
    if   has_deploy "$SRC_NS" "$d"; then cache="$cache $d"
    elif has_deploy "$DST_NS" "$d"; then preserved="$preserved $d"
    else absent="$absent $d"; fi
  done
  for d in "${ALL_DATA_PVCS[@]}"; do
    has_pvc "$SRC_NS" "$d" && pvcs="$pvcs $d" || true
  done
  for d in "${ALL_ARCH_PVCS[@]}"; do
    has_pvc "$SRC_NS" "$d" && arch="$arch $d" || true
  done

  cat > "$PLAN_FILE" <<PLAN
# Generated by discover_plan(). Delete this file to re-discover.
SRC_NS="$SRC_NS"
DST_NS="$DST_NS"
MIGRATE_APP="${app# }"
MIGRATE_DB="${db# }"
MIGRATE_CACHE="${cache# }"
MIGRATE_DATA_PVCS="${pvcs# }"
MIGRATE_ARCH_PVCS="${arch# }"
PRESERVED="${preserved# }"
ABSENT="${absent# }"
PLAN
  load_plan
  print_plan
}

load_plan() {
  [ -f "$PLAN_FILE" ] || die "$PLAN_FILE missing — run 00-preflight.sh (or 02) first"
  # shellcheck disable=SC1090
  . "$PLAN_FILE"
  read -ra MIGRATE_APP_A       <<< "${MIGRATE_APP:-}"
  read -ra MIGRATE_DB_A        <<< "${MIGRATE_DB:-}"
  read -ra MIGRATE_CACHE_A     <<< "${MIGRATE_CACHE:-}"
  read -ra MIGRATE_DATA_PVCS_A <<< "${MIGRATE_DATA_PVCS:-}"
  read -ra MIGRATE_ARCH_PVCS_A <<< "${MIGRATE_ARCH_PVCS:-}"
  read -ra PRESERVED_A         <<< "${PRESERVED:-}"
}

print_plan() {
  say "migration plan"
  printf '  migrate from %s : app  %s\n' "$SRC_NS" "${MIGRATE_APP:-(none)}"
  printf '  migrate from %s : db   %s\n' "$SRC_NS" "${MIGRATE_DB:-(none)}"
  printf '  migrate from %s : cache %s\n' "$SRC_NS" "${MIGRATE_CACHE:-(none)}"
  printf '  copy volumes         : %s\n' "${MIGRATE_DATA_PVCS:-(none)}"
  printf '  archive volumes only : %s\n' "${MIGRATE_ARCH_PVCS:-(none)}"
  printf '  already in %-9s : %s\n' "$DST_NS" "${PRESERVED:-(none)}"
  printf '  not found anywhere   : %s\n' "${ABSENT:-(none)}"
  if [ -n "${PRESERVED:-}" ]; then
    cat <<EOF

  Objects already in $DST_NS are LEFT RUNNING and their data is NOT touched:
  no dump is taken for them, no database of theirs is dropped, and they are not
  scaled down. Applying the overlay will still adopt them (adding the common
  labels and pinning their image tag), which is a normal rollout.
EOF
  fi
  if [ -n "${ABSENT:-}" ]; then
    warn "not found in either namespace: ${ABSENT}. If that is unexpected, stop here."
  fi
}
