# RHBK Identity Use Case

This repository contains the GitOps manifests and operating notes for the Arencloud identity lab. The design connects Microsoft Active Directory, Evolveum midPoint, Red Hat Build of Keycloak, and a demo banking application named Balance.

The main goal is to prove this flow end to end:

```text
midPoint assignment -> directory group -> RHBK realm role -> OIDC token -> application authorization
```

## Current Shape

| Area | Implementation |
| --- | --- |
| Directory and passwords | Microsoft Active Directory `ad.arencloud.com`, with 389 Directory Server as a second managed LDAP directory |
| Identity governance | Evolveum midPoint |
| Application identity provider | Red Hat Build of Keycloak, realm `arencloud` |
| Public issuer | `https://sso.arencloud.com` |
| Demo applications | Balance OIDC `https://balance.arencloud.com`, Balance SAML `https://balance-saml.arencloud.com` |
| Public DNS | Cloudflare, managed through ExternalDNS |
| Secrets | Vault through External Secrets Operator |
| TLS | cert-manager using `ClusterIssuer/vault` |
| North-south routing | Gateway API with Istio Gateway class |
| LoadBalancer IPs | MetalLB L2 pools |
| Deployment model | cl03 active, cl02 passive DR |

## Cluster Roles

| Cluster | Role | RHBK hostname | DNS behavior |
| --- | --- | --- | --- |
| `cl03` | Active site | `sso.arencloud.com` | ExternalDNS publishes the active record |
| `cl02` | Passive standby | `sso.arencloud.com` | RHBK HTTPRoute is excluded from ExternalDNS |

cl02 intentionally uses the same RHBK issuer hostname as cl03. That keeps application configuration stable during failover. While cl02 is passive, validate it with `curl --resolve` instead of publishing DNS to it.

## Repository Map

```text
clusters/
  cl03/
    applications/       Argo CD Applications for active site
    bootstrap/dns/      DNS forwarding for AD lookup
    operators/rhbk/     RHBK Operator subscription
    apps/rhbk/          Active RHBK runtime manifests
    apps/balance/       Balance application manifests
  cl02/
    applications/       Argo CD Applications for passive site
    bootstrap/dns/      DNS forwarding for AD lookup
    operators/rhbk/     RHBK Operator subscription
    apps/rhbk/          Passive RHBK runtime manifests
balance/                Quarkus OIDC demo banking application
balance-saml/           Quarkus SAML demo banking application
docs/                   Architecture, deployment, and operations notes
```

## Start Here

| Document | Use it for |
| --- | --- |
| [AD + midPoint + RHBK Architecture](docs/ad-midpoint-rhbk-architecture.md) | Overall design and responsibility split |
| [cl03 RHBK GitOps deployment](docs/rhbk-cl03-gitops-deployment.md) | Active RHBK deployment details |
| [cl02 active/passive GitOps deployment](docs/rhbk-cl02-active-passive-gitops.md) | Passive DR deployment, validation, failover notes |
| [RHBK 389 DS GitOps integration](docs/rhbk-389ds-gitops-integration.md) | Second LDAP federation source for RHBK |
| [cl03 GitOps readiness notes](docs/cl03-rhbk-gitops-readiness.md) | Platform readiness checks for the active site |
| [Microsoft AD configuration](docs/microsoft-ad-configuration.md) | AD OU layout, groups, service accounts, LDAPS, validation |
| [389 Directory Server configuration](docs/ldap389-directory-server.md) | LDAP389 baseline, Vault TLS, service accounts, ACLs, validation |
| [midPoint configuration](docs/midpoint-configuration.md) | AD resource, Balance roles, admin personas, provisioning checks |
| [Balance AD and midPoint baseline](docs/balance-ad-midpoint-baseline.md) | Balance authorization model and identity baseline |
| [midPoint Balance usage guide](docs/midpoint-balance-usage-guide.md) | How to assign Balance access through midPoint |
| [midPoint admin operating model](docs/midpoint-admin-operating-model.md) | Operating practices for midPoint admins |
| [AD01 LDAPS configuration](docs/ad01-ldaps-configuration.md) | LDAPS and Vault PKI notes for AD01 |

## GitOps Bootstrap

Run these from a shell logged into the target cluster.

Active site:

```bash
oc apply -k clusters/cl03/applications
```

Passive site:

```bash
oc apply -k clusters/cl02/applications
```

Useful status checks:

```bash
oc get application -n openshift-gitops
oc get pods -n rhbk
oc get externalsecret,certificate,keycloak,keycloakrealmimport,gateway,httproute -n rhbk
oc get gateway rhbk -n rhbk
```

## Required Platform Services

Each cluster needs these capabilities before the RHBK application can converge:

| Capability | Expected object or behavior |
| --- | --- |
| OpenShift GitOps | `openshift-gitops` namespace and Argo CD instance |
| RHBK Operator | `Subscription/rhbk-operator` in namespace `rhbk` |
| External Secrets | `ClusterSecretStore/vault` |
| cert-manager | `ClusterIssuer/vault` |
| Gateway API | `GatewayClass/istio` |
| MetalLB | L2 IP pool for Gateway `LoadBalancer` services |
| AD DNS forwarding | `ad.arencloud.com` resolves from cluster workloads |
| Trusted CA bundle | OpenShift trusted CA injection for AD and 389 DS LDAPS trust |

## Identity System Configuration

Detailed setup for the non-OpenShift identity components is kept in dedicated documents:

| Document | Covers |
| --- | --- |
| [Microsoft AD configuration](docs/microsoft-ad-configuration.md) | AD domain baseline, OU layout, Balance groups, service accounts, LDAPS, validation |
| [389 Directory Server configuration](docs/ldap389-directory-server.md) | Secondary LDAP directory baseline, Vault certificate renewal, midPoint/RHBK bind model |
| [midPoint configuration](docs/midpoint-configuration.md) | AD resource, trust store, Balance role inducements, admin personas, provisioning validation |
| [AD01 LDAPS configuration](docs/ad01-ldaps-configuration.md) | Certificate issuance/import, Windows scheduled renewal, LDAPS testing |

At a high level, midPoint owns managed access:

```text
Create/update user in midPoint
  -> assign Balance role
  -> midPoint updates AD and 389 DS group membership
  -> RHBK reads the selected LDAP federation source over LDAPS
  -> Balance receives roles in OIDC token
```

## RHBK LDAP Federation Ownership

The `arencloud` realm has two LDAP user federation providers:

| Provider | Directory | Normal role |
| --- | --- | --- |
| `arencloud-ad` | Microsoft AD | Primary enterprise directory and normal login source |
| `arencloud-389ds` | 389 Directory Server | Secondary LDAP source for validation and DR-style testing |

Both directories can contain the same usernames because midPoint provisions the same managed identities and Balance group memberships to both targets. Keycloak/RHBK does not merge duplicate federated users. A local imported user has exactly one `Federation link`, so the first provider that imports a username owns that local Keycloak user record.

Operationally:

- Keeping both providers enabled is allowed, but duplicate usernames must be managed deliberately.
- Provider priority decides lookup order for users that are not already imported.
- Existing imported users do not automatically move from AD to 389 DS, or from 389 DS back to AD.
- To switch ownership, remove imported users for the current provider, then sync the target provider.
- Removing imported users only clears Keycloak's local imported cache. It does not delete users from AD or 389 DS.

Switch a user set from AD to 389 DS:

```text
RHBK Admin Console
  -> User federation
  -> arencloud-ad
  -> Action: Remove imported users
  -> arencloud-389ds
  -> Action: Sync all users
```

Switch a user set from 389 DS back to AD:

```text
RHBK Admin Console
  -> User federation
  -> arencloud-389ds
  -> Action: Remove imported users
  -> arencloud-ad
  -> ensure Enabled is on
  -> Action: Sync all users
```

After switching, validate a user in `Users` and check:

```text
Enabled: true
Federation link: arencloud-ad or arencloud-389ds
Groups: expected Balance groups
```

## Secret Inputs

Secrets are not stored in Git. They are read from Vault by External Secrets.

| Site | Vault logical path | Required keys |
| --- | --- | --- |
| cl03 | `arencloud/cl03/rhbk/pgsql` | `username`, `password` |
| cl03 | `arencloud/cl03/rhbk/ad-bind` | `bindCredential` |
| cl02 | `arencloud/cl02/rhbk/pgsql` | `username`, `password` |
| cl02 | `arencloud/cl02/rhbk/ad-bind` | `bindCredential` |
| cl03 | `arencloud/cl03/balance/app` | `OIDC_CLIENT_SECRET`, `DB_USERNAME`, `DB_PASSWORD` |
| shared | `arencloud/shared/ldap389/rhbk-bind` | `bindCredential`, `password`, `bindDn`, `uid` |
| shared | `arencloud/shared/ldap389/midpoint-bind` | `password`, `bindDn`, `uid` |

The ExternalSecret remote keys are relative to the Vault mount configured in `ClusterSecretStore/vault`.

## Balance Application

The `balance/` directory contains the Quarkus application used to validate the identity and authorization flow.

cl03 GitOps deployment:

| Item | Value |
| --- | --- |
| Argo CD application | `clusters/cl03/applications/balance.yaml` |
| Kubernetes manifests | `clusters/cl03/apps/balance/` |
| Public hostname | `https://balance.arencloud.com` |
| Image | `quay.io/arencloud/balance:0.1.5` |
| Runtime secret source | `arencloud/cl03/balance/app` |

Balance authorizes users with RHBK realm roles:

| Role | Meaning |
| --- | --- |
| `balance_user` | Standard employee access |
| `balance_approver` | Approve or reject pending requests |
| `balance_auditor` | Read-only audit/reporting access |
| `balance_admin` | Full lab access |

The realm also includes a SAML client for testing SAML service-provider integration:

| Item | Value |
| --- | --- |
| Client ID / SP entity ID | `balance-saml` |
| Protocol | SAML |
| ACS POST URL | `https://balance-saml.arencloud.com/saml/acs` |
| SLO POST URL | `https://balance-saml.arencloud.com/saml/logout` |
| Valid redirect URI | `https://balance-saml.arencloud.com/saml/*` |
| NameID format | `username` |
| Assertion signature | enabled |
| Role attribute | `Role` |
| User attributes | `email`, `firstName`, `lastName` |

Use the RHBK realm SAML descriptor as the IdP metadata source:

```text
https://sso.arencloud.com/realms/arencloud/protocol/saml/descriptor
```

The SAML client is used by the second demo application in [balance-saml](balance-saml/README.md).

See [balance/README.md](balance/README.md) for local development, API routes, and browser test flows.

## Balance SAML Application

The `balance-saml/` directory contains the second banking demo application. It tests RHBK as a SAML Identity Provider with a Quarkus service-provider application.

cl03 GitOps deployment:

| Item | Value |
| --- | --- |
| Argo CD application | `clusters/cl03/applications/balance-saml.yaml` |
| Kubernetes manifests | `clusters/cl03/apps/balance-saml/` |
| Public hostname | `https://balance-saml.arencloud.com` |
| Image | `quay.io/arencloud/balance:saml-0.1.1` |
| SAML SP entity ID | `balance-saml` |

Passive cl02 GitOps deployment:

| Item | Value |
| --- | --- |
| Argo CD application | `clusters/cl02/applications/balance-saml.yaml` |
| Kubernetes manifests | `clusters/cl02/apps/balance-saml/` |
| Public hostname | `https://balance-saml.arencloud.com` |
| DNS behavior | HTTPRoute is excluded from ExternalDNS; validate with `curl --resolve` |
| IdP metadata source | local cl02 RHBK service, `rhbk-service.rhbk.svc.cluster.local` |

See [balance-saml/README.md](balance-saml/README.md) for local build, SAML endpoints, and OpenShift runtime notes.

## Validation Examples

Active site DNS validation:

```bash
dig +short sso.arencloud.com
curl -vk https://sso.arencloud.com/realms/arencloud/.well-known/openid-configuration
curl -vk https://sso.arencloud.com/realms/arencloud/protocol/saml/descriptor
curl -vk https://balance.arencloud.com/
curl -vk https://balance-saml.arencloud.com/
```

Passive cl02 validation without moving DNS:

```bash
CL02_GW_IP=$(oc get gateway rhbk -n rhbk -o jsonpath='{.status.addresses[0].value}')

curl -vk --resolve sso.arencloud.com:443:${CL02_GW_IP} \
  https://sso.arencloud.com/realms/arencloud/.well-known/openid-configuration
```

Gateway and LoadBalancer checks:

```bash
oc get gateway -n rhbk rhbk
oc get svc -A --field-selector spec.type=LoadBalancer
oc get ipaddresspool,l2advertisement -n metallb-system
```

## Failover Notes

cl02 is prepared as a passive site, not an active/active Keycloak cluster.

Before failover, confirm:

- cl02 RHBK is healthy through `curl --resolve`.
- The `balance` client secret and realm signing key strategy match the chosen DR model.
- Cloudflare or ExternalDNS ownership is changed deliberately so only the intended site publishes `sso.arencloud.com`.
- Users may need to sign in again in the warm-standby model.

The detailed runbook is in [cl02 active/passive GitOps deployment](docs/rhbk-cl02-active-passive-gitops.md).

## Operational Guardrails

- Do not commit secrets, tokens, exported client secrets, or generated admin passwords.
- Keep platform bootstrap and application manifests separate.
- Treat `sso.arencloud.com` as the stable issuer; change DNS publication, not application issuer configuration, during failover.
- Validate passive cl02 with `curl --resolve` until failover is approved.
- Keep generated RHBK realm secrets and signing keys under deliberate operational control before claiming seamless failover.
