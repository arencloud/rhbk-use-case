# Balance

Balance is the demo banking application for the Arencloud identity use case.

It validates the flow:

```text
midPoint role -> AD group -> RHBK realm role -> Balance UI/API authorization
```

The application uses:

- Quarkus `3.37.0`
- RHBK / Keycloak OIDC realm `arencloud`
- OIDC client `balance`
- PostgreSQL for runtime data
- H2 only for local tests and Quarkus dev mode

## Roles

| RHBK role | Application access |
| --- | --- |
| `balance_user` | View accounts, check balances, request approvals |
| `balance_approver` | View accounts, approve or reject pending requests |
| `balance_auditor` | View accounts and audit events |
| `balance_admin` | Full lab access |

## Runtime Configuration

Set these variables when running against RHBK and PostgreSQL:

```bash
export OIDC_AUTH_SERVER_URL=https://sso.arencloud.com/realms/arencloud
export OIDC_CLIENT_ID=balance
export OIDC_CLIENT_SECRET='<client-secret-from-rhbk>'
export DB_JDBC_URL=jdbc:postgresql://<pgsql-host>:5432/balance
export DB_USERNAME=balance
export DB_PASSWORD='<pgsql-password>'
export QUARKUS_HTTP_PROXY_PROXY_ADDRESS_FORWARDING=true
export QUARKUS_HTTP_PROXY_ALLOW_X_FORWARDED=true
export QUARKUS_OIDC_AUTHENTICATION_FORCE_REDIRECT_HTTPS_SCHEME=true
```

The app is configured as an OIDC `hybrid` application, so browser login uses authorization code flow and API tests can use bearer tokens.

## Run Locally

```bash
./mvnw quarkus:dev
```

Open:

```text
http://localhost:8080/
```

Dev mode uses H2 and disables OIDC so the code can be developed without external services. Production/test deployment should use the RHBK and PostgreSQL variables above.

Local dev users:

| Username | Password | Roles |
| --- | --- | --- |
| `employee` | `employee` | `balance_user` |
| `approver` | `approver` | `balance_user`, `balance_approver` |
| `auditor` | `auditor` | `balance_auditor` |
| `admin` | `admin` | `balance_admin` |

## API

All API endpoints require one of the Balance roles unless noted.

```text
GET  /api/me
GET  /api/accounts
GET  /api/accounts/{id}
POST /api/accounts/{id}/balance-checks
POST /api/accounts/{id}/approval-requests
GET  /api/approvals
POST /api/approvals/{id}/approve
POST /api/approvals/{id}/reject
GET  /api/audit
```

Example request:

```bash
curl -k https://balance.arencloud.com/api/me \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

Create an approval request:

```bash
curl -k https://balance.arencloud.com/api/accounts/1/approval-requests \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"amount":1500.00,"reason":"Customer requested balance confirmation"}'
```

Approve it:

```bash
curl -k https://balance.arencloud.com/api/approvals/1/approve \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"note":"Approved by branch supervisor"}'
```

## Verify

```bash
./mvnw test
./mvnw package
```

## Build With Rootless Podman

The container image is built from the JVM package output. Use rootless Podman, not Docker and not `sudo podman`.

Confirm Podman is running rootless:

```bash
podman info --format '{{.Host.Security.Rootless}}'
```

The expected output is:

```text
true
```

```bash
./mvnw package
export APP_VERSION=0.1.3
export IMAGE=quay.io/arencloud/balance:${APP_VERSION}
podman build -f src/main/container/Containerfile -t "${IMAGE}" .
podman run --rm --userns=keep-id --user "$(id -u):0" --read-only --tmpfs /tmp:rw,size=128m -p 8080:8080 \
  -e OIDC_AUTH_SERVER_URL=https://sso.arencloud.com/realms/arencloud \
  -e OIDC_CLIENT_ID=balance \
  -e OIDC_CLIENT_SECRET='<client-secret-from-rhbk>' \
  -e DB_JDBC_URL=jdbc:postgresql://<pgsql-host>:5432/balance \
  -e DB_USERNAME=balance \
  -e DB_PASSWORD='<pgsql-password>' \
  "${IMAGE}"
```

Or use the helper:

```bash
./scripts/build-image.sh 0.1.3
```

Build and push a multi-architecture image for OpenShift:

```bash
podman login quay.io
./scripts/build-image.sh 0.1.3 multiarch
```

## OpenShift Security

The OpenShift manifests in `openshift/` are written for the restricted security profile:

- no privileged container
- no host network, host PID, or host IPC
- `runAsNonRoot: true`
- `allowPrivilegeEscalation: false`
- all Linux capabilities dropped
- `RuntimeDefault` seccomp profile
- read-only root filesystem
- writable `/tmp` provided by `emptyDir`
- service account token is not mounted
- credentials are expected from a Kubernetes Secret, not Git
- no fixed `runAsUser`; OpenShift may assign the namespace UID from its restricted SCC range
- image files are readable by arbitrary non-root UIDs

Create the required secret before deployment if applying the local manifests directly:

```bash
oc -n balance create secret generic balance-secrets \
  --from-literal=OIDC_CLIENT_SECRET='<client-secret-from-rhbk>' \
  --from-literal=DB_USERNAME='balance' \
  --from-literal=DB_PASSWORD='<pgsql-password>'
```

Deploy the base manifests:

```bash
oc apply -k openshift/
```

## GitOps Deployment

The cl03 deployment is managed by OpenShift GitOps:

```text
clusters/cl03/applications/balance.yaml
clusters/cl03/apps/balance/
```

The GitOps manifests deploy:

- namespace `balance`
- restricted Deployment using `quay.io/arencloud/balance:0.1.3`
- Service on port `8080`
- OpenShift injected trusted CA bundle for outbound TLS to RHBK
- cert-manager Certificate from `ClusterIssuer/vault`
- Gateway API HTTPS endpoint for `balance.arencloud.com`
- HTTPRoute watched by ExternalDNS for Cloudflare record creation
- ExternalSecret sourcing runtime credentials from Vault

Store runtime secrets in Vault KV v2 path `arencloud/cl03/balance/app`. The ExternalSecret remote key is `cl03/balance/app` because the `ClusterSecretStore/vault` is mounted at `arencloud`.

Required Vault keys:

```text
OIDC_CLIENT_SECRET
DB_USERNAME
DB_PASSWORD
```

Update the GitOps image tag when releasing a new version. Do not deploy `latest`; use immutable version tags such as `quay.io/arencloud/balance:0.1.3`.
