# midPoint Configuration

## Purpose

This document describes the midPoint configuration used by the Arencloud identity lab. midPoint is the source of truth for managed users and application access. It provisions AD and 389 Directory Server accounts and group membership, while RHBK reads those users and groups over LDAPS.

## Baseline

| Item | Value |
| --- | --- |
| URL | `https://midpoint.arencloud.com/midpoint/` |
| VM IP | `10.10.30.10` |
| AD resource name | `Arencloud AD` |
| AD resource OID | `8f1c6c56-35b7-4a48-9d96-f6b6cc9f3c03` |
| AD connector | `com.evolveum.polygon.connector.ldap.ad.AdLdapConnector` |
| AD connection | `ad01.ad.arencloud.com:636`, SSL |
| AD base context | `OU=Arencloud,DC=ad,DC=arencloud,DC=com` |
| 389 DS resource name | `389ds-arencloud` |
| 389 DS resource OID | `08ac3417-4374-4a33-aad2-edba50342122` |
| 389 DS connector | `com.evolveum.polygon.connector.ldap.LdapConnector` |
| 389 DS connection | `ldap01.arencloud.com:636`, SSL |
| 389 DS base context | `dc=ldap,dc=arencloud,dc=com` |

## AD Resource

Configure the midPoint AD resource with:

| Setting | Value |
| --- | --- |
| Host | `ad01.ad.arencloud.com` |
| Port | `636` |
| Connection security | `ssl` |
| Bind DN | `CN=midPoint AD Service,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com` |
| Users container | `OU=Users,OU=Arencloud,DC=ad,DC=arencloud,DC=com` |
| Groups container | `OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com` |

The midPoint AD service account is:

```text
CN=midPoint AD Service,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
sAMAccountName: svc_midpoint_ad
```

The account is delegated over:

```text
OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

The current lab delegation is intentionally broad to validate provisioning quickly. Before production, reduce it to least-privilege permissions for user lifecycle and group membership updates.

## 389 DS Resource

The 389 DS resource is named:

```text
389ds-arencloud
```

Configure the resource with:

| Setting | Value |
| --- | --- |
| Host | `ldap01.arencloud.com` |
| Port | `636` |
| Connection security | `ssl` |
| Bind DN | `uid=svc_midpoint_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com` |
| Base context | `dc=ldap,dc=arencloud,dc=com` |
| Users container | `ou=people,dc=ldap,dc=arencloud,dc=com` |
| Groups container | `ou=groups,dc=ldap,dc=arencloud,dc=com` |
| Account object class | `inetOrgPerson` |
| Group object class | `groupOfNames` |
| Group association | object-to-subject through group `member` |

The bind password is stored in Vault:

```text
arencloud/shared/ldap389/midpoint-bind
```

Do not configure connector-side password hashing for this resource. 389 DS should receive a normal password value and apply its own server-side password storage scheme.

The 389 DS resource was tested successfully from midPoint. A temporary direct-construction test user was provisioned under `ou=people`, added to `cn=balance-users,ou=groups,...`, received `memberOf`, and was deleted cleanly.

## Trust Store

The Vault root CA must be trusted by the midPoint JVM so midPoint can connect to AD01 and 389 DS over LDAPS.

In this lab the AD LDAPS certificate chain is imported into:

```text
/var/opt/midpoint/keystore.jceks
```

See [AD01 LDAPS configuration](ad01-ldaps-configuration.md) for the LDAPS certificate and CA details.

## Balance Roles

Create these midPoint roles and configure each role to induce membership in the matching AD and 389 DS groups:

| midPoint role | Identifier | Requestable | AD group inducement | 389 DS group inducement |
| --- | --- | --- | --- | --- |
| `Balance User` | `balance_user` | yes | `balance-users` | `balance-users` |
| `Balance Approver` | `balance_approver` | yes | `balance-approvers` | `balance-approvers` |
| `Balance Auditor` | `balance_auditor` | yes | `balance-auditors` | `balance-auditors` |
| `Balance Admin` | `balance_admin` | yes | `balance-admins` | `balance-admins` |

Operational flow:

```text
Create or update user in midPoint
  -> assign Balance role
  -> recompute if needed
  -> midPoint updates AD and 389 DS group membership
  -> RHBK reads directory groups
  -> Balance receives roles in token
```

Do not manually grant normal application access by editing AD or 389 DS group membership. Use midPoint role assignments so both directories remain projections of governed access.

## Admin Personas

Use named admin personas instead of the built-in `administrator` account for normal work:

| User | Role |
| --- | --- |
| `egevorky.admin` | `Arencloud Platform Administrator` |
| `identity.admin` | `Identity Administrator` |
| `balance.owner` | `Balance Access Manager` |
| `security.auditor` | `Security Auditor` |

Use the built-in `administrator` account only for initial platform setup, connector/resource repair, emergency recovery, upgrades, and break-glass access.

See [midPoint admin operating model](midpoint-admin-operating-model.md) for the role model and operating boundaries.

## Validation Checklist

Use this sequence to validate midPoint provisioning:

```text
1. Test the `Arencloud AD` resource connection.
2. Test the `389ds-arencloud` resource connection.
3. Create or import a test user in midPoint.
4. Set a compliant password before provisioning to AD.
5. Assign `Balance User`, `Balance Approver`, `Balance Auditor`, or `Balance Admin`.
6. Recompute the user if provisioning does not run immediately.
7. Verify the AD account exists under `OU=Users,OU=Arencloud,...`.
8. Verify the 389 DS account exists under `ou=people,dc=ldap,...`.
9. Verify AD and 389 DS group membership.
10. Log in through RHBK and confirm the expected Balance role appears in the token.
```

If creating a brand-new user with Balance role assignment and no password, AD can reject the provisioning operation because the generated initial password may not satisfy AD policy. Set the user's password in midPoint before reconcile/provisioning when the AD projection is included.

Validated lab examples:

| AD user | AD groups |
| --- | --- |
| `balance.employee` | `balance-users` |
| `balance.approver` | `balance-users`, `balance-approvers` |
| `balance.auditor` | `balance-auditors` |
| `balance.admin` | `balance-users`, `balance-admins` |

See [midPoint Balance usage guide](midpoint-balance-usage-guide.md) for operator steps in the midPoint UI.

## Production Hardening

Before production:

- Replace broad AD delegation with least-privilege permissions.
- Replace broad 389 DS ACIs with least-privilege permissions if this moves beyond lab use.
- Add scheduled reconciliation/import tasks for users, groups, and shadows.
- Add approval workflows for privileged Balance roles.
- Enable regular access review campaigns.
- Rotate initial admin persona passwords.
- Connect admin personas to enterprise authentication through RHBK or AD.
