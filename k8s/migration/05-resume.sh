#!/usr/bin/env bash
# Ends the outage: brings the app tier up in the new namespace and checks it.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl
show_context

load_plan

# Only what 03 held at zero. Anything already in $DST_NS kept running throughout
# and is left alone.
say "starting [${MIGRATE_APP:-none}] in $DST_NS"
scale_deploys "$DST_NS" 1 ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}
for d in ${MIGRATE_APP_A[@]+"${MIGRATE_APP_A[@]}"}; do
  kubectl -n "$DST_NS" rollout status "deploy/$d" --timeout=5m
done

say "pods"
kubectl -n "$DST_NS" get pods -o wide

# Probed from the frontend pod: it is nginx:alpine and has busybox wget, while
# the backend image is a bare JRE with neither wget nor curl. This also proves
# Service DNS resolves inside the new namespace, which is the thing most likely
# to be broken by a namespace move.
say "frontend -> backend health (in-cluster, via Service DNS)"
kubectl -n "$DST_NS" exec "deploy/cooknco-frontend" -- \
  sh -c 'wget -qO- http://cooknco-backend:8080/api/v1/health' \
  || warn "backend health check FAILED — see: kubectl -n $DST_NS logs deploy/cooknco-backend"

say "frontend serves its own root"
kubectl -n "$DST_NS" exec "deploy/cooknco-frontend" -- \
  sh -c 'wget -qS -O /dev/null http://localhost/ 2>&1 | head -3' \
  || warn "frontend root check FAILED"

say "TLS secret and Certificate"
kubectl -n "$DST_NS" get secret cooknco-tls -o custom-columns=NAME:.metadata.name,TYPE:.type 2>/dev/null || warn "cooknco-tls missing"
kubectl -n "$DST_NS" get certificate 2>/dev/null || true

say "ingress"
kubectl -n "$DST_NS" get ingress -o wide

cat <<'EOF'

Now verify from outside the cluster:
  curl -sS -o /dev/null -w '%{http_code} %{ssl_verify_result}\n' https://cooknco.eu/
  curl -sS https://cooknco.eu/api/v1/health
  openssl s_client -connect cooknco.eu:443 -servername cooknco.eu </dev/null 2>/dev/null \
    | openssl x509 -noout -subject -dates

Then log in, open a recipe with a picture, and confirm an image loads (that
exercises the restored `pictures` volume). Expect to be signed out: redis was
not migrated, so existing sessions are gone.

Leave the old namespace untouched for a few days. When you are satisfied, run
99-cleanup.sh. If something is wrong, run 90-rollback.sh.
EOF
