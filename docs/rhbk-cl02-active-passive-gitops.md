# RHBK cl02 Active/Passive GitOps Deployment

## Purpose

This document describes the GitOps manifests for preparing Red Hat Build of Keycloak on OpenShift cluster `cl02` as the passive disaster-recovery site for the active RHBK deployment on `cl03`.

This is an active/passive DR design, not active/active multi-cluster session replication. The cl02 deployment is prepared to serve the same issuer hostname, `sso.arencloud.com`, but its HTTPRoute is excluded from ExternalDNS so it does not take production traffic until failover is intentionally performed.

## Deployment Model

| Site | Mode | Hostname configured in RHBK | DNS publication |
| --- | --- | --- | --- |
| `cl03` | Active | `sso.arencloud.com` | ExternalDNS publishes active record |
| `cl02` | Passive | `sso.arencloud.com` | HTTPRoute has `external-dns.alpha.kubernetes.io/exclude: "true"` |

The passive site uses the same public issuer hostname so applications such as Balance can continue to use:

```text
https://sso.arencloud.com/realms/arencloud
```

During failover, Cloudflare/DNS must be moved from the cl03 Gateway address to the cl02 Gateway address, or the ExternalDNS exclusion must be removed from cl02 and applied to cl03.

## Manifest Layout

```text
clusters/cl02/
  applications/
    kustomization.yaml
    cl02-dns.yaml
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
    externalsecret-pgsql.yaml
    externalsecret-ad-bind.yaml
    vault-ca-bundle-configmap.yaml
    certificate.yaml
    keycloak.yaml
    gateway.yaml
    httproute.yaml
    realm-import.yaml
```

Apply from a shell logged into cl02:

```bash
oc apply -k clusters/cl02/applications
```

## Required cl02 Platform Services

The cl02 cluster must already have the same platform capabilities used on cl03:

| Requirement | Expected object |
| --- | --- |
| OpenShift GitOps | `openshift-gitops` namespace and Argo CD |
| External Secrets Operator | `ClusterSecretStore/vault` |
| cert-manager | `ClusterIssuer/vault` |
| Gateway API / Istio gateway class | `gatewayClassName: istio` |
| OpenShift trusted CA bundle | Proxy trusted CA configured so `vault-ca-bundle` can be injected |
| DNS forwarding for AD | `clusters/cl02/bootstrap/dns` forwards `ad.arencloud.com` to AD01 |

ExternalDNS is optional for passive preparation. If it is installed on cl02, the RHBK HTTPRoute is excluded from DNS publication by default.

## Vault Inputs

Create these Vault paths before syncing the cl02 RHBK runtime app:

| Vault logical path | Required keys | Notes |
| --- | --- | --- |
| `arencloud/cl02/rhbk/pgsql` | `username`, `password` | PostgreSQL credentials for database `rhbk-cl02` |
| `arencloud/cl02/rhbk/ad-bind` | `bindCredential` | Same AD LDAP bind password used by cl03 unless a separate cl02 bind account is created |

The ExternalSecret remote keys are relative to the `arencloud` Vault mount:

```text
cl02/rhbk/pgsql
cl02/rhbk/ad-bind
```

## Database

The cl02 manifest currently uses:

```text
Host: 10.10.30.5
Port: 5432
Database: rhbk-cl02
```

For passive DR this gives cl02 its own RHBK database. That is simpler and safer for the lab than running two independent RHBK clusters against the same database without a supported multi-cluster cache/session design.

Before production failover expectations are defined, decide whether cl02 is:

- **Warm standby with re-login**: independent database, same GitOps realm config, users may need to authenticate again after failover.
- **Supported multi-cluster HA**: Red Hat-reviewed architecture with database replication and the required cache/session design.

## Critical Parity Requirements

For Balance to work after failover without application changes, cl02 must match cl03 on these items:

| Item | Why it matters |
| --- | --- |
| Issuer hostname `sso.arencloud.com` | Balance validates tokens against this issuer |
| Realm name `arencloud` | Balance uses `/realms/arencloud` |
| Client ID `balance` | Balance OIDC client configuration |
| Balance client secret | Balance token exchange fails if cl02 has a different confidential client secret |
| Realm signing keys | Existing tokens and JWKS validation depend on key continuity |
| AD LDAP federation | Users and groups must resolve the same way |
| Group-to-role mappings | Authorization roles must be identical |

The GitOps realm import creates the same realm structure, roles, groups, LDAP config, and Balance client. However, generated secrets and signing keys must be handled deliberately. After cl02 is first deployed, either set the cl02 `balance` client secret to match cl03 or update the Balance app secret during failover. For smoother failover, export/import or otherwise synchronize the realm keys according to the supported RHBK operational process.

## Validation Without Taking Traffic

Because cl02 is passive, do not publish `sso.arencloud.com` to cl02 during validation. Instead:

1. Find the cl02 Gateway address.
2. Test with `curl --resolve` so the Host header and TLS SNI still use `sso.arencloud.com`.

Example:

```bash
CL02_GW_IP=<cl02-gateway-ip>

curl -vk --resolve sso.arencloud.com:443:${CL02_GW_IP} \
  https://sso.arencloud.com/realms/master/.well-known/openid-configuration

curl -vk --resolve sso.arencloud.com:443:${CL02_GW_IP} \
  https://sso.arencloud.com/realms/arencloud/.well-known/openid-configuration
```

Check cl02 resources:

```bash
oc get application -n openshift-gitops rhbk-operator-cl02 rhbk-cl02 cl02-dns
oc get subscription,csv,installplan -n rhbk
oc get externalsecret,secret,certificate,keycloak,keycloakrealmimport,gateway,httproute -n rhbk
oc get pods -n rhbk
```

## Failover Runbook

Use this only after cl02 validation passes.

1. Confirm cl03 RHBK is unhealthy or failover is approved.
2. Confirm cl02 RHBK is healthy through `curl --resolve`.
3. Confirm cl02 Balance client secret and realm keys are aligned with the chosen failover model.
4. Move `sso.arencloud.com` from cl03 Gateway address to cl02 Gateway address in Cloudflare, or switch ExternalDNS exclusion annotations.
5. Wait for DNS propagation or lower TTL before planned failover.
6. Validate:

```bash
curl -vk https://sso.arencloud.com/realms/arencloud/.well-known/openid-configuration
curl -vk https://balance.arencloud.com/
```

7. Have users re-authenticate if using the warm-standby model.

## Failback

Failback is the reverse operation:

1. Restore and validate cl03.
2. Ensure any cl02-only realm/client changes are reconciled back to GitOps or cl03.
3. Move `sso.arencloud.com` back to the cl03 Gateway address.
4. Reapply passive DNS exclusion to cl02.

## Support Boundary

This cl02 deployment is a practical active/passive lab DR pattern on bare-metal OpenShift. It is not claiming Red Hat-supported seamless multi-cluster session continuity. For production-grade cross-cluster session continuity on bare metal, get a Red Hat design review and support confirmation before relying on it.
