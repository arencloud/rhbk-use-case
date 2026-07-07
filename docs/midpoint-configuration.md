# midPoint Configuration

## Purpose

This document describes the midPoint configuration used by the Arencloud identity lab. midPoint is the source of truth for managed users and application access. It provisions AD accounts and AD group membership, while RHBK reads those users and groups from AD over LDAPS.

## Baseline

| Item | Value |
| --- | --- |
| URL | `https://midpoint.arencloud.com/midpoint/` |
| VM IP | `10.10.30.10` |
| AD resource name | `Arencloud AD` |
| AD resource OID | `8f1c6c56-35b7-4a48-9d96-f6b6cc9f3c03` |
| AD connector | `com.evolveum.polygon.connector.ldap.ad.AdLdapConnector` |
| AD connection | `ad01.ad.arencloud.com:636`, SSL |
| Base context | `OU=Arencloud,DC=ad,DC=arencloud,DC=com` |

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

## Trust Store

The Vault root CA must be trusted by the midPoint JVM so midPoint can connect to AD01 over LDAPS.

In this lab the AD LDAPS certificate chain is imported into:

```text
/var/opt/midpoint/keystore.jceks
```

See [AD01 LDAPS configuration](ad01-ldaps-configuration.md) for the LDAPS certificate and CA details.

## Balance Roles

Create these midPoint roles and configure each role to induce membership in the matching AD group:

| midPoint role | Identifier | Requestable | AD group inducement |
| --- | --- | --- | --- |
| `Balance User` | `balance_user` | yes | `balance-users` |
| `Balance Approver` | `balance_approver` | yes | `balance-approvers` |
| `Balance Auditor` | `balance_auditor` | yes | `balance-auditors` |
| `Balance Admin` | `balance_admin` | yes | `balance-admins` |

Operational flow:

```text
Create or update user in midPoint
  -> assign Balance role
  -> recompute if needed
  -> midPoint updates AD group membership
  -> RHBK reads AD groups
  -> Balance receives roles in token
```

Do not manually grant normal application access by editing AD group membership. Use midPoint role assignments so AD remains a projection of governed access.

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

Use this sequence to validate midPoint to AD provisioning:

```text
1. Test the `Arencloud AD` resource connection.
2. Create or import a test user in midPoint.
3. Assign `Balance User`, `Balance Approver`, `Balance Auditor`, or `Balance Admin`.
4. Recompute the user if provisioning does not run immediately.
5. Verify the AD account exists under `OU=Users,OU=Arencloud,...`.
6. Verify AD group membership under `OU=Groups,OU=Arencloud,...`.
7. Log in through RHBK and confirm the expected Balance role appears in the token.
```

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
- Add scheduled reconciliation/import tasks for users, groups, and shadows.
- Add approval workflows for privileged Balance roles.
- Enable regular access review campaigns.
- Rotate initial admin persona passwords.
- Connect admin personas to enterprise authentication through RHBK or AD.

