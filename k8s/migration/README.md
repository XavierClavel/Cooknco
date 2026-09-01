# Migrating Cook&co from `default` to `cooknco`

One-time runbook. Expect **20–40 minutes of downtime**, most of it spent copying
data. Every script is idempotent and safe to re-run, and nothing in `default` is
deleted until `99-cleanup.sh` — so the migration is reversible right up to that
last step.

## The release is already partly migrated

**The mail service and its database already run in `cooknco`.** Nothing here
assumes otherwise: `00-preflight.sh` discovers where each component actually
lives and writes a plan to `.migration-plan`, which steps 02–05 and 99 then act
on. Components already in `cooknco` are **left running throughout** — no dump is
taken for them, no database of theirs is dropped, and they are never scaled down
or relocated. Only what is still in `default` moves.

For the current cluster that resolves to:

| Component | Where it is | What happens |
|---|---|---|
| `cooknco-backend`, `cooknco-frontend` | `default` | migrated (stopped, then recreated in `cooknco`) |
| `cooknco-database` | `default` | dumped, then restored into `cooknco` |
| `cooknco-redis` | `default` | stopped and recreated empty — data not migrated |
| `cooknco-mail-service` | `cooknco` | **left running**; the overlay adopts it |
| `mail-service-database` | `cooknco` | **left running**; its data is never touched |

Because the overlay covers the whole release, applying it also adopts the two
components already in `cooknco`: they pick up the common labels and their image
tag is pinned (`mail-service` moves from `latest` to `1.2.9`, which is a normal
rollout of the same content). `00-preflight.sh` runs `kubectl diff -k` so you can
see exactly that before anything happens.

> **The one thing to check by hand.** `cooknco-secrets` already exists in
> `cooknco` because the mail service needs it, and `01-copy-config.sh` will not
> overwrite an object that is already there. If that copy only carries the mail
> service's keys, the backend will crash-loop after the cutover looking for
> `redis-password` or `postgres-*`. `00-preflight.sh` compares the key sets in
> both namespaces and tells you if any are missing — fix it before starting.

Run everything from the repository root, against the cluster you mean:

```sh
kubectl config current-context     # check this first, every script prints it too
```

## Why data has to be copied rather than re-bound

A namespace change means new PersistentVolumeClaims. The tempting shortcut is to
set the PVs to `Retain`, delete the old claims, clear `spec.claimRef` and re-bind
the same PVs into `cooknco`. That works, but it forces the `volumeName`
(`pvc-<uuid>`, cluster-specific) into `overlays/prod`, where it does not belong
and would break any future rebuild of the cluster.

So the data is copied instead. A pod can only mount PVCs from its own namespace,
which means the copy cannot happen inside the cluster — it streams through the
machine running these scripts:

- **Databases** — `pg_dump -Fc` out, `pg_restore` in. Safer than a file-level
  copy and independently verifiable.
- **`pictures`** (user uploads, the one irreplaceable volume) — `tar` out of a
  helper pod in `default`, `tar` into a helper pod in `cooknco`.
- **`logs`** — archived into the backup directory, not restored.
- **`redis`** — not migrated, only stopped and recreated empty. It is a cache and
  session store. **Logged-in users will be signed out and must log in again.** If
  that is unacceptable, copy it the same way `pictures` is copied, with the
  deployment scaled to zero.
- **The mail service's database and volume** — not copied at all: they are
  already in `cooknco`.

The backup directory this produces is a genuine off-cluster backup. Keep it.
It is gitignored, because it contains database dumps and user uploads:

```
k8s/migration/backup-<timestamp>/
├── cooknco.pgc        pg_dump -Fc of the application database
├── mail-service.pgc   pg_dump -Fc of the mail-service database
├── pictures.tar       the pictures volume
├── logs.tar           the logs volume (archived, not restored)
├── inventory.yaml     raw live export of `default`, for reference only
└── restore/           cleaned, re-appliable copies of the three objects that
                       03 deletes — what 90-rollback.sh replays
```

## What forces the ordering

`nodePort: 30080` on the frontend Service is unique cluster-wide, and the host
`cooknco.eu` can only be claimed by one Ingress. The old pair must be deleted
before the new pair can be created, which is why the two namespaces cannot serve
side by side and why downtime is unavoidable without first dropping the nodePort.

## Steps

| # | Script | Effect | Reversible |
|---|---|---|---|
| 0 | `00-preflight.sh` | Read-only. Discovers what lives where, writes `.migration-plan`, diffs the overlay against the cluster, compares secret keys | — |
| 1 | `01-copy-config.sh` | Creates `cooknco`; copies the 4 secrets + 1 configmap | yes, additive |
| 2 | `02-freeze-and-backup.sh` | **Outage starts.** Snapshots `default`, dumps both databases, tars `pictures`/`logs`, stops `default` | yes |
| 3 | `03-cutover.sh` | Deletes the old Service/Ingress/Certificate, applies the overlay, holds the app tier at 0 | via `90-rollback.sh` |
| 4 | `04-restore.sh` | Drops/recreates both databases, restores dumps and `pictures` | yes, re-runnable |
| 5 | `05-resume.sh` | **Outage ends.** Starts the app tier, runs in-cluster checks | via `90-rollback.sh` |
| — | `90-rollback.sh` | Returns service to `default` | — |
| 9 | `99-cleanup.sh` | **Irreversible.** Deletes the old workloads, PVCs and secrets | no |

```sh
k8s/migration/00-preflight.sh          # read the warnings before continuing
k8s/migration/01-copy-config.sh
k8s/migration/02-freeze-and-backup.sh  # prompts before the outage starts
k8s/migration/03-cutover.sh            # prompts before deleting anything
k8s/migration/04-restore.sh
k8s/migration/05-resume.sh
# ... verify from outside the cluster, live with it for a few days ...
k8s/migration/99-cleanup.sh
```

`ASSUME_YES=1` skips the prompts. `SRC_NS`, `DST_NS`, `BACKUP_DIR`, `PLAN_FILE`
and `HELPER_IMAGE` are all overridable. Step 2 records its backup directory in
`.last-backup` and re-derives `.migration-plan`; steps 3, 4, 5, 99 and the
rollback read both automatically. Every step prints the plan it is acting on
before it does anything.

## Preflight findings to resolve before starting

`00-preflight.sh` will tell you, but two are worth knowing up front:

- **The four secrets and the configmap must exist in one of the two
  namespaces.** They are not in git. Preflight prints a presence table for both;
  anything missing from `cooknco` and absent from `default` has to be found
  before the outage starts, not during it.
- **The `KafkaTopic` CRs in `default` are inert.** They are labelled for a
  cluster named `cooknco-kafka` that does not exist, in a namespace Strimzi's
  topic operator does not watch, so the topics the backend uses were only ever
  auto-created by the broker. Step 3 applies corrected ones to the `kafka`
  namespace. `99-cleanup.sh` offers to delete the old CRs but checks with you
  first: deleting a *reconciled* KafkaTopic deletes the real topic and its
  messages, so confirm the `STATUS` column is empty before agreeing.

## TLS

`01-copy-config.sh` copies the existing `cooknco-tls` secret instead of letting
cert-manager issue a fresh one. Two reasons: TLS works the instant the new
Ingress goes live, and a new order for the same name would spend one of the five
duplicate certificates Let's Encrypt allows per week — which matters if the
migration has to be retried. The copy has its `ownerReferences` stripped, so
deleting the old Certificate in step 3 does not garbage-collect it. cert-manager
in `cooknco` then adopts the secret for the Certificate of the same `secretName`
and takes over renewal.

Confirm renewal actually transferred before deleting anything:

```sh
kubectl -n cooknco describe certificate cooknco    # Status should reach Ready=True
```

## Verifying

`05-resume.sh` runs the in-cluster checks and prints the external ones. The one
that matters most is loading a recipe picture in the browser — that is the only
check that exercises the restored `pictures` volume end to end.

## Rolling back

```sh
k8s/migration/90-rollback.sh
```

Deletes the new Service and Ingress, scales `cooknco` to zero, replays the
snapshot to restore the old Service/Ingress/Certificate, and brings `default`
back up. Valid any time before `99-cleanup.sh`.

Two things it cannot do for you: writes that landed while `cooknco` was serving
stay in the `cooknco` volumes (dump them before retrying if they matter), and if
CI pushed an image-tag bump you should revert that commit and disable the
`deploy` job until you retry.

## After cleanup

1. Delete `cooknco/` from the kubeconfig repo — it is superseded by `k8s/` here
   and will otherwise drift:
   ```sh
   git -C <kubeconfig> rm -r cooknco
   git -C <kubeconfig> commit -m "cooknco: manifests moved to the app repo (k8s/)"
   ```
2. Set the four deploy secrets if you have not already — `DEPLOY_HOST`,
   `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_SSH_KNOWN_HOSTS`; see
   [../README.md](../README.md#repository-setup-the-deploy-job-needs). The
   `deploy` job's pre-flight check passes once nodePort 30080 is held only by
   `cooknco`, so the next push to `master` deploys automatically.
3. The overlay is still pinned to the pre-migration versions (backend and
   frontend `1.1.0`, mail-service `1.2.9` — which is what its `latest` tag
   already resolved to) so that this migration changed the namespace and nothing
   else. The first `master` build after cleanup bumps all
   three to the current project version — a normal deploy, separately
   observable, which is the point.
