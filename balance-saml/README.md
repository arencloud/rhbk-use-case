# Balance SAML

Balance SAML is the second banking demo application for the Arencloud RHBK use case. It is a Quarkus application that acts as a SAML 2.0 Service Provider and uses Red Hat Build of Keycloak as the SAML Identity Provider.

## Runtime Values

| Item | Value |
| --- | --- |
| Public URL | `https://balance-saml.arencloud.com` |
| SAML SP entity ID | `balance-saml` |
| ACS endpoint | `/saml/acs` |
| SP metadata endpoint | `/saml/metadata` |
| Local logout | `/logout` |
| IdP metadata | `https://sso.arencloud.com/realms/arencloud/protocol/saml/descriptor` |
| Image | `quay.io/arencloud/balance:saml-0.1.2` |

## Authorization

The application reads SAML attribute `Role` and expects the same realm role names used by the OIDC Balance app:

| Role | Capability |
| --- | --- |
| `balance_user` | View accounts, record balance checks, request approval |
| `balance_approver` | View and decide approval requests |
| `balance_auditor` | View audit feed |
| `balance_admin` | Full lab access |

## Local Build

```bash
./mvnw package
```

Rootless Podman build:

```bash
./scripts/build-image.sh 0.1.0 single
./scripts/build-image.sh 0.1.0 multiarch
```

## OpenShift

GitOps manifests are in:

```text
clusters/cl03/apps/balance-saml
clusters/cl02/apps/balance-saml
```

cl03 is the active public site. cl02 is prepared as a passive deployment and excludes its HTTPRoute from ExternalDNS so it does not publish `balance-saml.arencloud.com` until failover is intentional.

The deployment follows OpenShift-friendly defaults:

- non-root pod
- dropped Linux capabilities
- no service account token mount
- read-only root filesystem
- temporary writable `/tmp`
- Gateway API ingress
- cert-manager Vault certificate
- OpenShift trusted CA bundle injection for RHBK SAML metadata fetch

## SAML Notes

Quarkus does not provide a native SAML service-provider extension equivalent to `quarkus-oidc`. This app uses the OneLogin Java SAML core toolkit inside Quarkus to implement:

- SP-initiated login
- ACS response validation
- signed assertion requirement
- SP metadata generation
- local application session cookie

The app intentionally keeps session state in memory because this is a single-replica lab test application.
