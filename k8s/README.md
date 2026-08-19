# Kubernetes manifests

Kustomize tree for Cook&co. These manifests replace `cooknco/` in the
[kubeconfig](https://github.com/XavierClavel/kubeconfig) repo, which should be
deleted once the migration below has completed. Cluster-scoped and shared
infrastructure — the Traefik config, the `letsencrypt-prod` ClusterIssuer, the
Strimzi Kafka cluster, the dashboard — stays in that repo; only what belongs to
this application moved here.

```
k8s/
├── base/                 namespace-agnostic application manifests
├── overlays/prod/        namespace: cooknco + the image tags CI rewrites
├── kafka-topics/         KafkaTopics, applied to the `kafka` namespace
└── migration/            one-time default -> cooknco runbook
```

## Rendering and applying

```sh
kustomize build k8s/overlays/prod       # or: kubectl kustomize k8s/overlays/prod
kubectl apply -k k8s/overlays/prod
kubectl apply -k k8s/kafka-topics
```

`base/` carries no image tags on purpose: `overlays/prod/kustomization.yaml` is
the single place a deployed version is recorded, which is what makes the CI bump
and `git log -- k8s/overlays/prod` a usable deployment history.

### Why Kafka topics are a separate root

Strimzi's topic operator only reconciles `KafkaTopic` resources in the namespace
of the Kafka cluster it manages — `kafka-kraft` in namespace `kafka`. The prod
overlay's `namespace: cooknco` transformer rewrites every resource it renders,
so the topics cannot live under it and are applied as their own root.

## Config this tree does not contain

These exist only in the cluster and must be present in the target namespace
before the app will start. `k8s/migration/01-copy-config.sh` copies them from
`default`; `k8s/migration/00-preflight.sh` reports which are missing.

| Object | Kind | Consumed by |
|---|---|---|
| `cooknco-secrets` | Secret | backend (`redis-password`), database (`postgres-db`, `postgres-user`, `postgres-password`), mail-service (`envFrom`) |
| `cooknco-config` | Secret | backend, mounted at `/app/config` |
| `mail-database-secrets` | Secret | mail-service and its database (`envFrom`) |
| `smtp-secrets` | ConfigMap | mail-service (`envFrom`) |
| `cooknco-tls` | Secret | Ingress TLS; produced by the `cooknco` Certificate |

## CI

Two workflows, both in `.github/workflows/`.

**`manifests.yml`** — runs on any PR or push touching `k8s/**`:

1. `kustomize build` each root.
2. `kubeconform -strict` against the Kubernetes schemas plus the
   [CRDs-catalog](https://github.com/datreeio/CRDs-catalog) for the
   out-of-tree kinds (`Certificate`, `KafkaTopic`).
3. Rejects any image reference that is untagged or pinned to `:latest`, so a
   rollout is always reproducible and always rollback-able.

**`build.yml`** — on a push to `master`, after the images are pushed:

1. `kustomize edit set image` rewrites the three `newTag` values in
   `overlays/prod` to the version from `./gradlew printVersion`, and commits
   that back to `master` as `ci: pin cooknco images to <version> [skip ci]`.
   The push uses `GITHUB_TOKEN`, which by design does not re-trigger workflows,
   so this cannot loop.
2. The `deploy` job re-pins to the version this run actually built (so a race
   between the bump commit and its own checkout cannot deploy a stale tag),
   runs `--dry-run=server`, applies both roots, and waits on all six rollouts.

### Repository setup the deploy job needs

- **`KUBE_CONFIG`** secret — a base64-encoded kubeconfig:
  `base64 -w0 < ~/.kube/config` (`base64 -i ~/.kube/config` on macOS).
  Use a dedicated ServiceAccount scoped to the `cooknco` and `kafka`
  namespaces rather than cluster-admin credentials.
- The cluster API server must be reachable from GitHub-hosted runners. If it
  is not exposed publicly, switch the `deploy` job to a self-hosted runner on
  the cluster's network, or join the runner to a Tailscale/WireGuard network.
- `master` must accept pushes from `github-actions[bot]`. If branch protection
  blocks it, either add the bot as an exception or drop the *Commit image tag
  bump* step — the `deploy` job re-pins the tag itself, so deployments still
  work; only the git-recorded history of deployed versions is lost.
- Optional but recommended: move the `deploy` job behind a GitHub
  `environment: production` to get a manual approval gate.

The `deploy` job refuses to run while nodePort 30080 is held outside the
`cooknco` namespace. That is the pre-migration state, so **complete
`migration/README.md` before the first automated deploy**.

## Known issues carried over, and one fix

Reproduced from the original manifests without change, flagged rather than
silently altered:

- **`nodePort: 30080` on the frontend Service.** Cluster-unique, and redundant
  now that Traefik fronts the app through the Ingress. Dropping it would remove
  the main constraint on the migration ordering and on running two copies side
  by side. Worth deleting separately once confirmed unused.
- **`kubernetes.io/ingress.class: traefik` annotation** is deprecated in favour
  of `spec.ingressClassName`. Left alone so the namespace migration changes one
  thing at a time.
- **Two TLS mechanisms at once**: `cert-manager.io/cluster-issuer` plus
  `traefik.ingress.kubernetes.io/router.tls.certresolver: letsencrypt`. Both
  will try to obtain a certificate for `cooknco.eu`. Pick one.
- **No resource requests or limits** on any container. A `LimitRange` in the
  new namespace would be a good follow-up.

Fixed, because both were already broken:

- The Certificate carried `commonName: "example.com"`, which is not one of its
  `dnsNames`. Let's Encrypt cannot authorise it, so the order fails. Removed —
  cert-manager derives the CN from `dnsNames`.
- The KafkaTopics were labelled `strimzi.io/cluster: cooknco-kafka`, a cluster
  that does not exist; the real one is `kafka-kraft`. Combined with living in a
  namespace the topic operator does not watch, they were inert, and the topics
  the backend uses were only ever auto-created by the broker. Relabelled to
  `kafka-kraft` and moved to the `kafka` namespace.
