# RHBK cl03 PostgreSQL Vault Secret

## Purpose

This document records the PostgreSQL credential format for Red Hat Build of Keycloak on OpenShift cluster `cl03`.

The credential is stored in Vault now and is intended to be projected later into OpenShift with External Secrets Operator.

## Source Requirement

Red Hat Build of Keycloak Operator 26.6 expects database credentials to be referenced from Kubernetes Secrets through the Keycloak CR `spec.db.usernameSecret` and `spec.db.passwordSecret` fields.

The Keycloak CR also carries non-secret database connection details such as `host`, `database`, and `port`.

Reference format:

```yaml
apiVersion: k8s.keycloak.org/v2beta1
kind: Keycloak
metadata:
  name: rhbk
spec:
  db:
    vendor: postgres
    usernameSecret:
      name: rhbk-pgsql
      key: username
    passwordSecret:
      name: rhbk-pgsql
      key: password
    host: 10.10.30.5
    database: rhbk-cl03
    port: 5432
```

## Vault Location

Logical Vault path:

```text
arencloud/cl03/rhbk/pgsql
```

Vault engine:

```text
arencloud/ - KV v2
```

API path:

```text
arencloud/data/cl03/rhbk/pgsql
```

Stored keys:

| Key | Purpose |
| --- | --- |
| `host` | PostgreSQL host |
| `port` | PostgreSQL port |
| `database` | PostgreSQL database name |
| `username` | PostgreSQL username |
| `password` | PostgreSQL password |

The actual password is stored only in Vault and must not be committed to Git.

## Stored Values

| Key | Value |
| --- | --- |
| `host` | `10.10.30.5` |
| `port` | `5432` |
| `database` | `rhbk-cl03` |
| `username` | `pgsql` |
| `password` | Stored in Vault |

## External Secrets Target

Recommended resulting Kubernetes Secret:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: rhbk-pgsql
  namespace: rhbk
type: Opaque
stringData:
  username: pgsql
  password: <from Vault>
```

Only `username` and `password` are required in the Secret for the RHBK Operator database credential references. The other values can remain in the Keycloak CR or be templated later if we decide to generate the CR with GitOps.

## ExternalSecret Example

This example assumes a `ClusterSecretStore` named `vault` already exists and points to Vault.

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: rhbk-pgsql
  namespace: rhbk
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault
    kind: ClusterSecretStore
  target:
    name: rhbk-pgsql
    creationPolicy: Owner
  data:
    - secretKey: username
      remoteRef:
        key: cl03/rhbk/pgsql
        property: username
    - secretKey: password
      remoteRef:
        key: cl03/rhbk/pgsql
        property: password
```

For KV v2, the `ClusterSecretStore` should be configured with:

```yaml
provider:
  vault:
    path: arencloud
    version: v2
```

## Optional GitOps Template Values

If we later want External Secrets to project non-secret fields as well, the same Vault object also contains:

```yaml
host: 10.10.30.5
port: "5432"
database: rhbk-cl03
```

Those values are not required in the database Secret referenced by the Keycloak CR.

## Validation

Vault write was verified by reading metadata and key names only.

```text
stored_path=arencloud/cl03/rhbk/pgsql
engine=kv-v2
api_path=arencloud/data/cl03/rhbk/pgsql
version=1
keys=database,host,password,port,username
```

## References

- Red Hat Build of Keycloak 26.6 Operator Guide, database configuration: `spec.db.usernameSecret`, `spec.db.passwordSecret`, `spec.db.host`, `spec.db.database`, `spec.db.port`
