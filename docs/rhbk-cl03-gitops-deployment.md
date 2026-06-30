# RHBK cl03 GitOps Deployment

## Purpose

This document describes the GitOps manifests for deploying Red Hat Build of Keycloak on OpenShift cluster `cl03`.

The deployment follows the current architecture:

- RHBK runs in namespace `rhbk`.
- RHBK public hostname is `sso.arencloud.com`.
- PostgreSQL is external at `10.10.30.5:5432`.
- Database credentials come from Vault through External Secrets.
- RHBK is exposed through Gateway API, not an Operator-managed Ingress.
- Gateway TLS is issued by cert-manager from `ClusterIssuer/vault`.
- Cloudflare DNS is managed by ExternalDNS from Gateway API HTTPRoutes.
- RHBK trusts the Vault CA bundle used by AD01 LDAPS.
- RHBK federates users and groups from AD over LDAPS.
- midPoint remains the identity lifecycle authority and provisions users/groups into AD.

## Manifest Layout

```text
clusters/cl03/
  applications/
    kustomization.yaml
    cl03-dns.yaml
    rhbk-operator.yaml
    rhbk.yaml
  bootstrap/dns/
    kustomization.yaml
    dns-forward-ad.yaml
  operators/rhbk/
    kustomization.yaml
    namespace.yaml
    operatorgroup.yaml
    subscription.yaml
  apps/rhbk/
    kustomization.yaml
    namespace.yaml
    externalsecret-pgsql.yaml
    externalsecret-ad-bind.yaml
    vault-ca-bundle-configmap.yaml
    certificate.yaml
    keycloak.yaml
    gateway.yaml
    httproute.yaml
    realm-import.yaml
```

## Sync Order

Apply the Argo CD applications:

```bash
oc apply -k clusters/cl03/applications
```

The RHBK Operator subscription uses automatic InstallPlan approval for this lab. Wait for CRDs:

```bash
oc get csv -n rhbk
oc get crd | grep -i keycloak
```

After the Operator is installed, sync the `rhbk` Argo CD application.

## Important Resources

| Resource | Purpose |
| --- | --- |
| `DNS/default` | Forwards `ad.arencloud.com` lookups to AD01 at `10.10.30.11` |
| `Subscription/rhbk-operator` | Installs RHBK Operator channel `stable-v26.6` |
| `ExternalSecret/rhbk-pgsql` | Syncs DB username/password from Vault |
| `ExternalSecret/rhbk-ad-bind-vault` | Syncs the AD LDAP bind password from Vault as a Keycloak file-vault entry |
| `ConfigMap/vault-ca-bundle` | Requests OpenShift to inject the cluster trusted CA bundle used for AD LDAPS trust |
| `Certificate/rhbk-gateway-tls` | Creates TLS Secret for the Gateway |
| `Keycloak/rhbk` | Runs one RHBK instance with external PostgreSQL |
| `Gateway/rhbk` | Provides HTTPS entrypoint through Gateway API |
| `HTTPRoute/rhbk` | Routes `sso.arencloud.com` to RHBK |
| `KeycloakRealmImport/arencloud` | Creates the initial `arencloud` realm, AD federation, roles, groups, and `balance` client |

## Vault Inputs

These Vault paths are required before the RHBK runtime application is synced:

| Vault logical path | Required keys | Used by |
| --- | --- | --- |
| `arencloud/cl03/rhbk/pgsql` | `username`, `password` | PostgreSQL connection |
| `arencloud/cl03/rhbk/ad-bind` | `bindCredential` | AD LDAP bind through Keycloak file vault |

The Kubernetes Secret generated for AD bind is mounted into RHBK as a file vault. The Secret key is:

```text
arencloud_ldapBc
```

For the `arencloud` realm, Keycloak resolves this in the LDAP provider as:

```text
${vault.ldapBc}
```

## Expected DNS Flow

1. cert-manager creates `Secret/rhbk-gateway-tls`.
2. Istio Gateway controller assigns an address to `Gateway/rhbk`.
3. `HTTPRoute/rhbk` attaches to the Gateway.
4. ExternalDNS sees host `sso.arencloud.com`.
5. ExternalDNS creates or updates the Cloudflare DNS record under `arencloud.com`.

ExternalDNS will create the Cloudflare record only after the Gateway has an address. On cl03, that address should come from the Gateway implementation and MetalLB. If the Gateway status has no address, ExternalDNS has no target to publish.

## AD Federation

The realm import configures LDAP federation to:

```text
Connection URL: ldaps://ad01.ad.arencloud.com:636
Bind DN: CN=RHBK LDAP Bind,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
Users DN: OU=Users,OU=Arencloud,DC=ad,DC=arencloud,DC=com
Groups DN: OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
Edit mode: READ_ONLY
Import users: true
```

The bind password is not committed to Git. It is read from Vault and mounted into RHBK through the Keycloak file vault.

midPoint remains responsible for creating and maintaining users and groups in AD. RHBK reads those identities from AD and maps AD groups to application-facing roles.

## Balance Client

The initial realm import creates OIDC client:

```text
Client ID: balance
Redirect URI: https://balance.arencloud.com/*
Web origin: https://balance.arencloud.com
```

Role path:

```text
midPoint assignment -> AD group -> RHBK group -> RHBK role -> balance token claim
```

Initial group and role mapping:

| AD/RHBK group | RHBK realm role | Application meaning |
| --- | --- | --- |
| `balance-users` | `balance_user` | Standard bank employee access to Balance |
| `balance-approvers` | `balance_approver` | Privileged Balance workflow approval |
| `balance-auditors` | `balance_auditor` | Balance read-only/reporting |
| `balance-admins` | `balance_admin` | Balance administration |

## Post-Deploy Validation

```bash
oc get subscription,csv,installplan -n rhbk
oc get externalsecret,secret,certificate,keycloak,keycloakrealmimport,gateway,httproute -n rhbk
oc get pods -n rhbk
oc get gateway rhbk -n rhbk
oc get httproute rhbk -n rhbk
dig +short sso.arencloud.com
curl -vk https://sso.arencloud.com/realms/master/.well-known/openid-configuration
curl -vk https://sso.arencloud.com/realms/arencloud/.well-known/openid-configuration
```

## Known Follow-Up

The RHBK Operator creates a temporary bootstrap admin Secret named `rhbk-initial-admin` if no explicit `spec.bootstrapAdmin` is configured. After deployment, create a permanent administrative model and remove dependency on the temporary admin account.
