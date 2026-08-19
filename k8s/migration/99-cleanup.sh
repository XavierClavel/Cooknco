#!/usr/bin/env bash
# Point of no return: deletes the old namespace's copy of the app and its data.
# Only run this once the new namespace has been serving happily for a while and
# the backup directory is archived somewhere off-cluster.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

kubectl -n "$DST_NS" get deploy -o wide
confirm "Is $DST_NS above healthy, and is the backup archived off-cluster?"
confirm "DELETE the cooknco workloads AND PersistentVolumeClaims from $SRC_NS? This destroys the old data."

load_plan
say "deleting legacy workloads from $SRC_NS"
for d in ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"} \
         ${MIGRATE_DB_A[@]+"${MIGRATE_DB_A[@]}"} \
         ${MIGRATE_CACHE_A[@]+"${MIGRATE_CACHE_A[@]}"}; do
  kubectl -n "$SRC_NS" delete "deploy/$d" --ignore-not-found
done
for s in cooknco-backend cooknco-database cooknco-redis mail-service-database; do
  kubectl -n "$SRC_NS" delete "svc/$s" --ignore-not-found
done

say "deleting legacy PersistentVolumeClaims from $SRC_NS"
for p in postgres-data redis-data pictures logs mail-service-data; do
  kubectl -n "$SRC_NS" delete "pvc/$p" --ignore-not-found
done

say "deleting the legacy TLS secret and remaining config from $SRC_NS"
for s in cooknco-tls cooknco-secrets cooknco-config mail-database-secrets; do
  kubectl -n "$SRC_NS" delete "secret/$s" --ignore-not-found
done
kubectl -n "$SRC_NS" delete configmap smtp-secrets --ignore-not-found

# These KafkaTopic CRs were never reconciled: they are labelled for a cluster
# named `cooknco-kafka` that does not exist, in a namespace the topic operator
# does not watch. 00-preflight.sh prints their .status — confirm it is empty
# before agreeing here, because deleting a *reconciled* KafkaTopic deletes the
# real topic and its messages.
say "inert KafkaTopic CRs in $SRC_NS"
kubectl -n "$SRC_NS" get kafkatopic -o custom-columns=\
NAME:.metadata.name,CLUSTER:.metadata.labels.strimzi\\.io/cluster,STATUS:.status 2>/dev/null || true
if kubectl -n "$SRC_NS" get kafkatopic >/dev/null 2>&1; then
  confirm "Is the STATUS column above empty/<none> for every row (never reconciled)?"
  for t in cooknco-users cooknco-recipes cooknco-pictures; do
    kubectl -n "$SRC_NS" delete "kafkatopic/$t" --ignore-not-found
  done
fi

say "remaining cooknco-related objects in $SRC_NS (should be empty)"
kubectl -n "$SRC_NS" get all,pvc,ingress,certificate 2>/dev/null | grep -E 'cooknco|mail-service' || echo "  none"

cat <<'EOF'

Cleanup done. Two follow-ups outside the cluster:

  1. Delete the cooknco/ directory from the kubeconfig repo — it is superseded
     by k8s/ in this repo and will otherwise drift:
       git -C <kubeconfig> rm -r cooknco
       git -C <kubeconfig> commit -m "cooknco: manifests moved to the app repo (k8s/)"

  2. The deploy job's pre-flight check now passes, so master pushes deploy
     automatically. Confirm the KUBE_CONFIG secret is set.
EOF
