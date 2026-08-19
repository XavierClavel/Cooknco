# Shared helpers for the default -> cooknco namespace migration.
# Sourced by the numbered scripts; not meant to be run directly.

set -euo pipefail

SRC_NS="${SRC_NS:-default}"
DST_NS="${DST_NS:-cooknco}"
OVERLAY="${OVERLAY:-k8s/overlays/prod}"
HELPER_IMAGE="${HELPER_IMAGE:-busybox:1.36}"

# App tier: stopped for the whole cutover. The database tier is handled
# separately because the dumps are taken while it is still running.
APP_DEPLOYS=(cooknco-backend cooknco-frontend cooknco-mail-service)
DB_DEPLOYS=(cooknco-database mail-service-database)

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

wait_gone() {
  local ns="$1"; shift
  say "waiting for pods to terminate in $ns"
  local tries=0 remaining
  while :; do
    remaining="$(kubectl -n "$ns" get pods -o name 2>/dev/null \
      | grep -cE 'cooknco|mail-service' || true)"
    [ "${remaining:-0}" = "0" ] && break
    tries=$((tries + 1))
    if [ "$tries" -gt 60 ]; then
      warn "$remaining pod(s) still present after 5 minutes:"
      kubectl -n "$ns" get pods
      break
    fi
    sleep 5
  done
}
