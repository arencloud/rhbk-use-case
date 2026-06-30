# cl03 RHBK GitOps Readiness

## Purpose

This document records what is needed on OpenShift cluster `cl03` to deploy Red Hat Build of Keycloak with OpenShift GitOps later.

The intended model is:

```text
OpenShift GitOps / Argo CD
  -> installs or manages RHBK operator inputs
  -> deploys RHBK Keycloak CR
  -> uses External Secrets for credentials
  -> connects RHBK to external PostgreSQL and AD over LDAPS
```

## Cluster Snapshot

| Item | Value |
| --- | --- |
| Cluster API | `https://api.cl03.arencloud.com:6443` |
| OpenShift version | `4.21.15` |
| Logged-in user during analysis | `kube:admin` |
| Nodes | 3 compact control-plane/worker nodes |
| Node network | `10.10.20.10`, `10.10.20.11`, `10.10.20.12` |
| Ingress domain | `apps.cl03.arencloud.com` |
| Ingress strategy | `HostNetwork` |
| Public/app route pattern | `*.apps.cl03.arencloud.com` |
| Default StorageClass | `truenas-iscsi` |

All cluster operators were healthy at the time of analysis.

## Current Operator State

Installed subscriptions:

| Operator | Namespace | Channel |
| --- | --- | --- |
| cert-manager Operator for Red Hat OpenShift | `cert-manager-operator` | `stable-v1` |
| External Secrets Operator for Red Hat OpenShift | `external-secrets-operator` | `stable-v1` |
| Red Hat OpenShift Service Mesh 3 | `openshift-operators` | `stable` |
| Red Hat OpenShift GitOps | `openshift-operators` | `latest` |
| MetalLB Operator | `metallb-system` | `stable` |

Not installed yet:

| Operator | Available package | Recommended channel/version |
| --- | --- | --- |
| Red Hat Build of Keycloak Operator | `rhbk-operator` | `stable-v26.6`, currently `rhbk-operator.v26.6.4-opr.1` |

RHBK Operator install modes from the catalog:

```text
OwnNamespace: supported
SingleNamespace: supported
MultiNamespace: not supported
AllNamespaces: not supported
```

OpenShift GitOps Operator install mode:

```text
AllNamespaces: supported
```

## Existing Supporting Services

### External Secrets

External Secrets is installed and the `vault` ClusterSecretStore is ready.

```text
ClusterSecretStore: vault
Status: Valid
Message: store validated
Capabilities: ReadWrite
Vault server: https://vault.svc.arencloud.com
Vault path: arencloud
Vault version: v2
Kubernetes auth mount: cl03
Vault role: eso-cl03-role
```

On 2026-06-30 the store temporarily failed because Vault was sealed. After Vault was unsealed and the `external-secrets` deployment was restarted, the store validated again and dependent ExternalSecrets synced.

The RHBK PostgreSQL secret path was tested through External Secrets and synced successfully:

```text
Vault logical path: arencloud/cl03/rhbk/pgsql
ExternalSecret remote key: cl03/rhbk/pgsql
Target Secret keys: username, password
Status: SecretSynced=True
```

See [RHBK cl03 PostgreSQL Vault Secret](rhbk-cl03-postgresql-vault-secret.md).

### cert-manager

cert-manager is installed. For RHBK HTTPS routes, we can either:

- use OpenShift router default certificates initially, or
- create a dedicated RHBK TLS Secret with cert-manager/Vault/private CA, then reference it from the Keycloak CR.

### OpenShift GitOps

OpenShift GitOps is installed cluster-wide:

```text
Subscription namespace: openshift-operators
Installed CSV: openshift-gitops-operator.v1.21.0
Argo CD namespace: openshift-gitops
Argo CD instance: openshift-gitops
Argo CD status: Available
Argo CD version: v3.4.3
Route: https://openshift-gitops-server-openshift-gitops.apps.cl03.arencloud.com
```

Cluster-wide configuration:

```text
AppProject: openshift-gitops/cluster-wide
Application controller service account: openshift-gitops-argocd-application-controller
Cluster-admin binding: cluster-admin-0
OpenShift OAuth SSO: enabled
```

The `cluster-wide` AppProject allows all source repositories, all destinations, and all Kubernetes resource kinds. This is intentionally broad for platform bootstrap and RHBK deployment.

### MetalLB

MetalLB is installed and configured for L2 mode:

```text
Subscription namespace: metallb-system
Installed CSV: metallb-operator.v4.21.0-202606090112
MetalLB instance: metallb-system/metallb
IPAddressPool: metallb-system/cl03-l2-pool
L2Advertisement: metallb-system/cl03-l2-advertisement
Address range: 10.10.20.200-10.10.20.254
```

Runtime components:

```text
controller deployment: 1/1 ready
speaker daemonset: 3/3 ready
```

Validation:

```text
Temporary LoadBalancer service received external IP 10.10.20.200.
```

### ExternalDNS / Cloudflare

ExternalDNS is installed for automatic Cloudflare DNS records from Gateway API HTTPRoutes.

```text
Namespace: external-dns
Helm release: external-dns-cloudflare
Chart: external-dns/external-dns 1.21.1
App version: external-dns 0.21.0
Provider: cloudflare
Source: gateway-httproute
Domain filter: arencloud.com
Registry: txt
TXT owner ID: cl03
Policy: upsert-only
Cloudflare proxy mode: false
Deployment status: 1/1 ready
```

Cloudflare credentials are synced from Vault through External Secrets:

```text
Vault logical path: arencloud/cl03/dns/cloudflare
ExternalSecret remote key: cl03/dns/cloudflare
Namespace: external-dns
ExternalSecret: cloudflare-api-token
Target Secret: cloudflare-api-token
Secret key used by ExternalDNS: token
Status: SecretSynced=True
```

OpenShift security context note:

```text
Namespace UID/GID range: 1000910000/10000
ExternalDNS pod UID/GID/fsGroup: 1000910000
SCC used: restricted-v2
```

The upstream chart defaults to fixed UID/GID values that are outside this namespace range. The Helm values for cl03 must override `podSecurityContext` and container `securityContext` to use IDs from the namespace range.

RHBK DNS automation expectation:

```text
When RHBK is exposed through Gateway API, attach the HTTPRoute to a Gateway
that has an address and create an HTTPRoute host such as:
sso.arencloud.com

ExternalDNS will watch the HTTPRoute and create/update Cloudflare records under:
arencloud.com
```

Validation:

```text
ExternalDNS created Gateway API and Kubernetes clients successfully.
Current log state: All records are already up to date.
Gateway RBAC: can list HTTPRoutes and Gateways cluster-wide.
```

ExternalDNS will only publish the record after `Gateway/rhbk` has an address. With the current cl03 design, that address should be assigned by the Gateway implementation and MetalLB.

## Current Gaps

### 1. RHBK Operator is not installed

The `rhbk-operator` package is available, but no RHBK CRDs are installed yet.

Needed:

```text
Create namespace: rhbk
Create OperatorGroup targeting rhbk
Create Subscription for rhbk-operator on stable-v26.6
Wait for Keycloak CRDs to appear
```

### 2. AD DNS forwarding is missing

Pod-level network can reach AD by IP, but pods currently cannot resolve `ad01.ad.arencloud.com`.

Observed from a temporary pod:

```text
10.10.30.11:636  OPEN
ad01.ad.arencloud.com DNS lookup: no result
```

Needed:

```text
Configure OpenShift DNS forwarding for ad.arencloud.com to 10.10.30.11.
```

Recommended DNS Operator configuration:

```yaml
apiVersion: operator.openshift.io/v1
kind: DNS
metadata:
  name: default
spec:
  servers:
    - name: ad-arencloud
      zones:
        - ad.arencloud.com
      forwardPlugin:
        upstreams:
          - 10.10.30.11
```

After applying, validate from a pod:

```bash
getent hosts ad01.ad.arencloud.com
getent hosts ad.arencloud.com
```

Expected:

```text
10.10.30.11 ad01.ad.arencloud.com
10.10.30.11 ad.arencloud.com
```

### 3. Vault ClusterSecretStore operational note

```text
ClusterSecretStore: vault
Status: Valid
Message: store validated
```

If Vault is sealed, the store changes to `InvalidProviderConfig` and ExternalSecrets stop refreshing. Existing Kubernetes Secrets remain present, but new or rotated values will not sync until Vault is unsealed and ESO reconciles again.

### 4. RHBK must trust the Vault root CA

AD01 LDAPS uses a Vault-issued certificate:

```text
Issuer: CN=intermediate_midpoint_ca
Root CA: CN=My Company Root CA
LDAPS endpoint: ldaps://ad01.ad.arencloud.com:636
```

RHBK must trust the Vault root CA to connect to AD over LDAPS. On cl03, the Vault root CA is already configured as the OpenShift cluster Proxy trusted CA:

```yaml
apiVersion: config.openshift.io/v1
kind: Proxy
metadata:
  name: cluster
spec:
  trustedCA:
    name: vault-root-ca
```

RHBK therefore uses an injected ConfigMap instead of publishing the CA bundle in Git:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: vault-ca-bundle
  namespace: rhbk
  annotations:
    config.openshift.io/inject-trusted-cabundle: "true"
```

OpenShift injects the trusted CA bundle into the ConfigMap, and the Keycloak CR consumes it through `spec.truststores`.

## Pod Connectivity Results

Temporary pod test from `rhbk-preflight`:

```text
AD LDAPS: 10.10.30.11:636 OPEN
PostgreSQL: 10.10.30.5:5432 OPEN
Vault: vault.svc.arencloud.com:443 OPEN
Vault DNS: vault.svc.arencloud.com -> 10.10.30.5
AD DNS: ad01.ad.arencloud.com not resolved yet
```

Conclusion:

```text
Network path is OK.
DNS forwarding for AD is the immediate cluster networking gap.
```

## Completed GitOps Bootstrap

OpenShift GitOps was bootstrapped once by cluster-admin.

### OpenShift GitOps Operator

```yaml
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: openshift-gitops-operator
  namespace: openshift-operators
spec:
  channel: latest
  installPlanApproval: Automatic
  name: openshift-gitops-operator
  source: redhat-operators
  sourceNamespace: openshift-marketplace
```

The operator created the default Argo CD instance:

```text
Namespace: openshift-gitops
ArgoCD: openshift-gitops
Route: openshift-gitops-server-openshift-gitops.apps.cl03.arencloud.com
```

### Cluster-Wide Permissions

```bash
oc adm policy add-cluster-role-to-user cluster-admin \
  -z openshift-gitops-argocd-application-controller \
  -n openshift-gitops
```

Result:

```text
ClusterRoleBinding: cluster-admin-0
Subject: system:serviceaccount:openshift-gitops:openshift-gitops-argocd-application-controller
```

### Cluster-Wide AppProject

```yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: cluster-wide
  namespace: openshift-gitops
spec:
  description: Cluster-wide GitOps project for cl03 platform and application deployments.
  sourceRepos:
    - '*'
  destinations:
    - namespace: '*'
      server: '*'
  clusterResourceWhitelist:
    - group: '*'
      kind: '*'
  namespaceResourceWhitelist:
    - group: '*'
      kind: '*'
```

## RHBK Operator Bootstrap

RHBK Operator can be installed by GitOps after OpenShift GitOps is running, or manually as part of the initial bootstrap.

Recommended namespace:

```text
rhbk
```

Operator install:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: rhbk
---
apiVersion: operators.coreos.com/v1
kind: OperatorGroup
metadata:
  name: rhbk-operatorgroup
  namespace: rhbk
spec:
  targetNamespaces:
    - rhbk
---
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: rhbk-operator
  namespace: rhbk
spec:
  channel: stable-v26.6
  installPlanApproval: Automatic
  name: rhbk-operator
  source: redhat-operators
  sourceNamespace: openshift-marketplace
```

Validate:

```bash
oc get csv -n rhbk
oc api-resources | grep -i keycloak
```

GitOps manifests were created in:

```text
clusters/cl03/operators/rhbk
```

The subscription uses:

```text
Channel: stable-v26.6
Starting CSV: rhbk-operator.v26.6.4-opr.1
InstallPlan approval: Manual
```

Manual approval is intentional so an operator sync cannot silently upgrade the Keycloak server runtime.

## RHBK Runtime Inputs

Before creating the Keycloak CR, GitOps should manage these resources in namespace `rhbk`:

| Resource | Purpose |
| --- | --- |
| `ExternalSecret/rhbk-pgsql` | Creates DB credential Secret from Vault |
| `Secret/rhbk-pgsql` | Generated by External Secrets with `username` and `password` |
| `Secret` or `ConfigMap` for Vault root CA | Trust for AD LDAPS |
| Optional TLS Secret | Public HTTPS endpoint for RHBK route |
| `Keycloak/rhbk` | RHBK instance |

Minimal ExternalSecret:

```yaml
apiVersion: external-secrets.io/v1
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

Minimal Keycloak DB stanza:

```yaml
spec:
  db:
    vendor: postgres
    host: 10.10.30.5
    port: 5432
    database: rhbk-cl03
    usernameSecret:
      name: rhbk-pgsql
      key: username
    passwordSecret:
      name: rhbk-pgsql
      key: password
```

Runtime GitOps manifests were created in:

```text
clusters/cl03/apps/rhbk
```

They include:

```text
ExternalSecret/rhbk-pgsql
ExternalSecret/rhbk-ad-bind-vault
ConfigMap/vault-ca-bundle
Certificate/rhbk-gateway-tls
Keycloak/rhbk
Gateway/rhbk
HTTPRoute/rhbk
KeycloakRealmImport/arencloud
```

The Keycloak CR disables Operator-managed Ingress and exposes RHBK through Gateway API:

```text
Hostname: sso.arencloud.com
GatewayClass: istio
Gateway TLS Secret: rhbk-gateway-tls
HTTPRoute backend: rhbk-service:8080
ExternalDNS source: gateway-httproute
```

The realm import also configures:

```text
Realm: arencloud
Client: balance
AD federation: ldaps://ad01.ad.arencloud.com:636
AD bind DN: CN=RHBK LDAP Bind,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
AD bind Secret source: Vault path arencloud/cl03/rhbk/ad-bind
AD bind Secret key in Kubernetes: arencloud_ldapBc
Keycloak vault expression: ${vault.ldapBc}
Group-to-role path: AD groups managed by midPoint -> RHBK groups -> realm roles
```

## Recommended Git Repository Layout

Suggested future GitOps layout:

```text
clusters/
  cl03/
    bootstrap/
      openshift-gitops/
      dns/
    operators/
      rhbk/
    apps/
      rhbk/
        namespace.yaml
        externalsecret-pgsql.yaml
        truststore.yaml
        keycloak.yaml
        kustomization.yaml
```

## Readiness Checklist

| Item | Status | Notes |
| --- | --- | --- |
| Cluster healthy | Ready | OpenShift 4.21.15 |
| External Secrets Operator | Ready | Operator pods are running |
| Vault ClusterSecretStore | Ready | `vault`, path `arencloud`, KV v2 |
| RHBK PostgreSQL secret in Vault | Ready | `arencloud/cl03/rhbk/pgsql` |
| Pod to PostgreSQL network | Ready | `10.10.30.5:5432` open |
| Pod to AD LDAPS network | Ready | `10.10.30.11:636` open |
| Pod to Vault network | Ready | `vault.svc.arencloud.com:443` open |
| AD DNS from pods | Manifest ready | `clusters/cl03/bootstrap/dns` forwards `ad.arencloud.com` to `10.10.30.11`; apply/sync required |
| StorageClass | Ready | `truenas-iscsi` default, `truenas-nfs` also available |
| OpenShift GitOps Operator | Ready | `openshift-gitops-operator.v1.21.0` |
| OpenShift GitOps Argo CD | Ready | `openshift-gitops`, status `Available` |
| Argo CD cluster-wide RBAC | Ready | Application controller bound to `cluster-admin` |
| Argo CD cluster-wide project | Ready | `openshift-gitops/cluster-wide` |
| MetalLB Operator | Ready | `metallb-operator.v4.21.0-202606090112` |
| MetalLB L2 pool | Ready | `10.10.20.200-10.10.20.254`; test service received `10.10.20.200` |
| ExternalDNS Cloudflare credential | Ready | Synced from Vault path `arencloud/cl03/dns/cloudflare` |
| ExternalDNS Cloudflare controller | Ready | Helm release `external-dns-cloudflare`, source `gateway-httproute` |
| RHBK GitOps manifests | Ready | Created under `clusters/cl03` |
| RHBK public hostname | Manifest ready | `sso.arencloud.com` through Gateway API and ExternalDNS |
| RHBK AD federation | Manifest ready | Uses AD LDAPS and Keycloak file vault for bind password |
| RHBK AD bind credential in Vault | Ready | `arencloud/cl03/rhbk/ad-bind`, key `bindCredential` |
| Balance OIDC client | Manifest ready | Client ID `balance` |
| midPoint AD resource | Ready | `Arencloud AD`, OID `8f1c6c56-35b7-4a48-9d96-f6b6cc9f3c03`, connection test successful |
| midPoint Balance roles | Ready | `Balance User`, `Balance Approver`, `Balance Auditor`, `Balance Admin` |
| RHBK Operator | Missing | Install `rhbk-operator`, channel `stable-v26.6` |
| Vault root CA trust for RHBK | Manifest ready | OpenShift Proxy trusted CA injection through `ConfigMap/vault-ca-bundle` and Keycloak `truststores` |

## Next Actions

1. Apply the Argo CD application manifests from `clusters/cl03/applications`.
2. Approve the RHBK Operator InstallPlan in namespace `rhbk`.
3. Sync the RHBK runtime application after the Keycloak CRDs exist.
4. Validate RHBK pod can:
   - connect to PostgreSQL `10.10.30.5:5432`
   - resolve and connect to `ldaps://ad01.ad.arencloud.com:636`
   - trust the AD01 LDAPS certificate
5. Validate ExternalDNS creates the Cloudflare DNS record for the RHBK HTTPRoute.

## References

- OpenShift GitOps Operator package available in `redhat-operators`: `openshift-gitops-operator.v1.21.0`
- Red Hat Build of Keycloak Operator package available in `redhat-operators`: `rhbk-operator.v26.6.4-opr.1`
- OpenShift DNS Operator supports per-zone forwarding with `spec.servers` and upstream DNS servers: <https://docs.redhat.com/en/documentation/openshift_container_platform/4.10/html/networking/dns-operator>
- ExternalDNS upstream Helm chart: <https://github.com/kubernetes-sigs/external-dns/tree/master/charts/external-dns>
- Red Hat Build of Keycloak Operator database configuration uses separate Secrets for username/password: <https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.6/pdf/operator_guide/Red_Hat_build_of_Keycloak-26.6-Operator_Guide-en-US.pdf>
- Red Hat Build of Keycloak Operator supports `truststores` in the Keycloak CR for trusted CA Secrets or ConfigMaps: <https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/24.0/html/operator_guide/advanced-configuration->
