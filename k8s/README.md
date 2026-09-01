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

## Current namespace state

The release is **partly migrated already**: `cooknco-mail-service` and
`mail-service-database` run in `cooknco`, while the backend, frontend, database
and redis are still in `default`. The overlay describes the whole release in
`cooknco`, so applying it adopts the two components that are already there and
creates the rest. `k8s/migration/` handles the move and deliberately leaves the
already-migrated components running — see its README.

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
   renders both roots on the runner, and pipes them into `kubectl apply` over
   SSH: `--dry-run=server` first, then the apply, then all six rollouts.

The cluster API is never exposed to the internet. The only inbound door is
sshd on the cluster host, and the only credential GitHub holds is the deploy
key — no kubeconfig is stored in the repository or in Actions. The kubeconfig
lives on the host, owned by the deploy user, and the runner only ever sends it
rendered manifests. Those manifests are checked for `kind: Secret` before they
leave the runner: every Secret the app needs is created out-of-band on the
cluster (see the table above), so one appearing in the render means a generator
crept back into the overlay, and the job fails rather than shipping credentials
through GitHub's infrastructure.

### Repository setup the deploy job needs

Four secrets, all under *Settings → Secrets and variables → Actions*:

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | Hostname or IP of the cluster host running sshd |
| `DEPLOY_USER` | Login the deploy key belongs to; owns `~/.kube/config` on that host |
| `DEPLOY_SSH_KEY` | The **private** half of a dedicated deploy keypair, in full PEM form |
| `DEPLOY_SSH_KNOWN_HOSTS` | The host's public key line, so the runner pins it |

Generating and installing the key:

```sh
ssh-keygen -t ed25519 -f ~/.ssh/cooknco_deploy -C "github-actions cooknco deploy" -N ''
ssh-copy-id -i ~/.ssh/cooknco_deploy.pub <deploy-user>@<host>

pbcopy    < ~/.ssh/cooknco_deploy     # -> DEPLOY_SSH_KEY   (private key, whole file)
ssh-keyscan -H <host> | pbcopy        # -> DEPLOY_SSH_KNOWN_HOSTS
```

`ssh-keyscan` is trust-on-first-use: run it from somewhere you trust the path,
and check the fingerprint against `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`
on the host itself. The key is pinned rather than `StrictHostKeyChecking=no`
because this channel carries manifests an interceptor could rewrite into
anything the deploy user is allowed to apply.

On the host, `<deploy-user>` needs a working `~/.kube/config`. Point it at a
dedicated ServiceAccount scoped to the `cooknco` and `kafka` namespaces rather
than cluster-admin credentials.

The workflow assumes sshd on port 22. If it listens elsewhere, change
`DEPLOY_PORT` in the `deploy` job's `env:` block.

Two more repository-level notes:

- `master` must accept pushes from `github-actions[bot]`. If branch protection
  blocks it, either add the bot as an exception or drop the *Commit image tag
  bump* step — the `deploy` job re-pins the tag itself, so deployments still
  work; only the git-recorded history of deployed versions is lost.
- The `deploy` job declares `environment: production`. GitHub creates that
  environment on the first run; attaching a required reviewer to it in the
  repository settings puts a manual approval gate in front of prod.

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
