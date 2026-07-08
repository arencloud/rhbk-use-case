# RHBK 389 DS GitOps Integration

## Purpose

This document describes how 389 Directory Server is added to Red Hat Build of Keycloak as a second LDAP user federation source.

The design keeps midPoint as the write owner:

```text
midPoint assignment
  -> AD group membership
  -> 389 DS group membership
  -> RHBK LDAP federation
  -> Balance realm role
  -> Balance application authorization
```

AD remains the primary RHBK LDAP provider. 389 DS is configured as the second provider with lower priority.

## GitOps Manifests

The 389 DS integration is included in both active and passive RHBK GitOps trees:

```text
clusters/cl03/apps/rhbk/
clusters/cl02/apps/rhbk/
```

Files added or changed:

| File | Purpose |
| --- | --- |
| `externalsecret-ldap389-bind.yaml` | Syncs the 389 DS RHBK bind credential from Vault |
| `keycloak.yaml` | Projects AD and 389 DS bind secrets into the Keycloak file vault directory |
| `realm-import.yaml` | Adds `arencloud-389ds` LDAP provider and group mapper |
| `kustomization.yaml` | Includes the new ExternalSecret |

## Vault Secret

External Secrets reads the shared 389 DS RHBK bind credential:

```text
arencloud/shared/ldap389/rhbk-bind
```

Required Vault key:

```text
bindCredential
```

The Kubernetes Secret created by External Secrets is:

```text
rhbk-ldap389-bind-vault
```

The file vault key projected into Keycloak is:

```text
arencloud_ldap389Bc
```

The realm import references it as:

```text
${vault.ldap389Bc}
```

## DNS

No additional OpenShift DNS forwarder is required for 389 DS.

The existing bootstrap DNS forwarder is only for the AD private zone:

```text
ad.arencloud.com -> 10.10.30.11
```

389 DS uses:

```text
ldap01.arencloud.com -> 10.10.30.12
```

That name is in the normal `arencloud.com` DNS zone and resolves through the cluster's default upstream resolvers. Do not add a broad `arencloud.com` DNS forwarder to the AD server, because Cloudflare remains authoritative for the parent zone and other application records.

Validate from the RHBK namespace:

```bash
oc run -n rhbk dns-test-ldap389 --rm -i --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi-minimal --command -- \
  sh -c 'getent hosts ldap01.arencloud.com; timeout 5 sh -c "</dev/tcp/ldap01.arencloud.com/636" && echo ldaps-open'
```

Expected result:

```text
10.10.30.12 ldap01.arencloud.com
ldaps-open
```

## Keycloak Runtime Mount

The Keycloak CR uses one projected volume mounted at:

```text
/mnt/keycloak-vault
```

The projected volume combines:

```text
rhbk-ad-bind-vault
rhbk-ldap389-bind-vault
```

This keeps both LDAP bind credentials available to the Keycloak file vault without storing them in Git.

## LDAP Provider

The second provider is:

| Setting | Value |
| --- | --- |
| Provider name | `arencloud-389ds` |
| Provider ID | `ldap` |
| Priority | `10` |
| Edit mode | `READ_ONLY` |
| Vendor | `other` |
| Connection URL | `ldaps://ldap01.arencloud.com:636` |
| Users DN | `ou=people,dc=ldap,dc=arencloud,dc=com` |
| Bind DN | `uid=svc_rhbk_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com` |
| Username LDAP attribute | `uid` |
| RDN LDAP attribute | `uid` |
| UUID LDAP attribute | `entryUUID` |
| User object classes | `inetOrgPerson, organizationalPerson` |
| Truststore SPI | `ldapsOnly` |

AD provider priority is `0`; 389 DS provider priority is `10`.

## Attribute Mappers

| RHBK user field | 389 DS LDAP attribute |
| --- | --- |
| Username | `uid` |
| First name | `givenName` |
| Last name | `sn` |
| Email | `mail` |

All mappers are read-only. User lifecycle is managed by midPoint, not RHBK.

## Group Mapper

The 389 DS group mapper is:

| Setting | Value |
| --- | --- |
| Mapper name | `ldap389-groups` |
| Mapper type | `group-ldap-mapper` |
| Mode | `READ_ONLY` |
| Groups DN | `ou=groups,dc=ldap,dc=arencloud,dc=com` |
| Group object class | `groupOfNames` |
| Group name LDAP attribute | `cn` |
| Membership LDAP attribute | `member` |
| Membership attribute type | `DN` |
| Membership user LDAP attribute | `dn` |
| Retrieve strategy | `LOAD_GROUPS_BY_MEMBER_ATTRIBUTE` |

The 389 DS groups use the same names as AD:

| LDAP group | RHBK realm role |
| --- | --- |
| `balance-users` | `balance_user` |
| `balance-approvers` | `balance_approver` |
| `balance-auditors` | `balance_auditor` |
| `balance-admins` | `balance_admin` |

The realm already defines RHBK groups with those names and maps them to the Balance realm roles.

## Important Realm Import Behavior

`KeycloakRealmImport` is an initial import mechanism. It does not overwrite or continuously reconcile an existing realm. Red Hat documents that if a realm with the same name already exists, it is not overwritten, and Realm Import does not update or delete realms.

Operational consequence:

- Fresh RHBK deployments or rebuilt passive cl02 can receive this provider directly from GitOps realm import.
- Existing cl03 realm `arencloud` will not automatically gain the new LDAP provider just because `realm-import.yaml` changed.
- For an existing realm, apply the same provider configuration through a controlled admin API or partial-import operation, or recreate the realm only in a planned maintenance window.

## Validation

After GitOps sync, check External Secrets:

```bash
oc get externalsecret -n rhbk rhbk-ldap389-bind-vault
oc get secret -n rhbk rhbk-ldap389-bind-vault
```

Check the Keycloak pod has both file vault keys:

```bash
oc exec -n rhbk statefulset/rhbk -- ls -l /mnt/keycloak-vault
```

Expected files:

```text
arencloud_ldapBc
arencloud_ldap389Bc
```

Validate network and TLS from the RHBK namespace:

```bash
oc run -n rhbk ldap389-test --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi -- bash
```

Inside the temporary pod:

```bash
getent hosts ldap01.arencloud.com
timeout 5 bash -c '</dev/tcp/ldap01.arencloud.com/636' && echo ldaps-open
```

In the RHBK Admin Console:

1. Open realm `arencloud`.
2. Go to User federation.
3. Confirm provider `arencloud-389ds` exists and is enabled.
4. Run Test connection.
5. Run Test authentication.
6. Synchronize changed users, or full sync during a test window.
7. Check a midPoint-provisioned 389 DS user resolves with expected group membership.

## Duplicate Username Rule

The same user may exist in AD and 389 DS. RHBK provider priority decides which provider is queried first. AD has priority `0`, and 389 DS has priority `10`.

For normal operation, keep usernames consistent across both directories and avoid manually creating users directly in either directory. midPoint should create and maintain both projections.

For DR or failover testing, document whether RHBK should authenticate users primarily from AD, from 389 DS, or from only one enabled provider at a time. Two active LDAP providers with the same usernames are useful for validation, but they are not a replacement for a deliberate failover procedure.
