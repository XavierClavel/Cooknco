#!/usr/bin/env bash
# Creates the target namespace and copies over the config that lives only in the
# cluster (never in git). Non-destructive: existing objects are left untouched.
cd "$(dirname "$0")/../.." || exit 1
. k8s/migration/lib.sh

need kubectl; need python3
show_context

say "creating namespace $DST_NS if absent"
kubectl create namespace "$DST_NS" --dry-run=client -o yaml | kubectl apply -f -

say "copying secrets and configmaps"
copy_resource secret    cooknco-secrets
copy_resource secret    cooknco-config
copy_resource secret    mail-database-secrets
copy_resource configmap smtp-secrets

# Copied rather than re-issued on purpose: a fresh Let's Encrypt order for
# cooknco.eu counts against the duplicate-certificate rate limit (5/week), and
# copying means TLS works the instant the new Ingress goes live. cert-manager in
# $DST_NS then adopts this secret for the Certificate of the same secretName and
# takes over renewal.
say "copying the existing TLS certificate"
copy_resource secret cooknco-tls

say "$DST_NS now contains:"
kubectl -n "$DST_NS" get secret,configmap
