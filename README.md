# RHBK Identity Use Case

This repository documents and tracks the identity architecture using:

- Microsoft Active Directory as the enterprise directory and credential store
- Evolveum midPoint as identity governance and provisioning
- Red Hat Build of Keycloak as the application-facing identity provider

Start with the architecture document:

- [AD + midPoint + RHBK Architecture](docs/ad-midpoint-rhbk-architecture.md)
- [cl03 RHBK GitOps deployment](docs/rhbk-cl03-gitops-deployment.md)
- [Balance AD and midPoint baseline](docs/balance-ad-midpoint-baseline.md)
- [midPoint Balance usage guide](docs/midpoint-balance-usage-guide.md)
- [midPoint admin operating model](docs/midpoint-admin-operating-model.md)

## Balance Application

The `balance/` directory contains the Quarkus application used to validate the end-to-end authorization flow.

cl03 GitOps deployment:

- Argo CD application: `clusters/cl03/applications/balance.yaml`
- Kubernetes manifests: `clusters/cl03/apps/balance/`
- Public hostname: `https://balance.arencloud.com`
- Image: `quay.io/arencloud/balance:0.1.2`
- Runtime secret source: Vault path `arencloud/cl03/balance/app`

Required Vault keys for the Balance application:

```text
OIDC_CLIENT_SECRET
DB_USERNAME
DB_PASSWORD
```

ExternalDNS should publish the Cloudflare record from the `HTTPRoute` after the Gateway has an address.
