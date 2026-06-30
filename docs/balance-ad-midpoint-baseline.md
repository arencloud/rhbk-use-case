# Balance AD and midPoint Baseline

## Purpose

This records the identity baseline prepared for the `balance` application before RHBK deployment.

## Active Directory Baseline

Domain:

```text
ad.arencloud.com
```

Managed OU tree:

```text
OU=Arencloud,DC=ad,DC=arencloud,DC=com
OU=Users,OU=Arencloud,DC=ad,DC=arencloud,DC=com
OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

Balance authorization groups:

| AD group | RHBK role | Meaning |
| --- | --- | --- |
| `balance-users` | `balance_user` | Standard bank employee access to Balance |
| `balance-approvers` | `balance_approver` | Privileged Balance workflow approval |
| `balance-auditors` | `balance_auditor` | Read-only audit/reporting access |
| `balance-admins` | `balance_admin` | Balance application administration |

## Service Accounts

midPoint provisioning account:

```text
CN=midPoint AD Service,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
sAMAccountName: svc_midpoint_ad
```

This account is enabled, password-never-expires, and delegated over:

```text
OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

RHBK LDAP federation account:

```text
CN=RHBK LDAP Bind,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
sAMAccountName: rhbk-ldap-bind
```

This account is enabled, password-never-expires, and used only for read-only LDAP federation from RHBK.

## Vault

The RHBK LDAP bind credential is stored at:

```text
Vault logical path: arencloud/cl03/rhbk/ad-bind
Key: bindCredential
```

The midPoint AD provisioning bind credential is stored at:

```text
Vault logical path: arencloud/cl03/midpoint/ad-bind
Key: bindCredential
```

GitOps projects it into OpenShift as:

```text
ExternalSecret: rhbk/rhbk-ad-bind-vault
Secret: rhbk/rhbk-ad-bind-vault
Secret key: arencloud_ldapBc
Keycloak expression: ${vault.ldapBc}
```

## Validation

LDAPS bind was validated from:

- local workstation to `ldaps://10.10.30.11:636`
- midPoint VM to `ldaps://ad01.ad.arencloud.com:636`

Both validations returned the four Balance groups from AD.

## midPoint Status

midPoint is running on:

```text
https://midpoint.arencloud.com/midpoint/
```

The VM service state is healthy:

```text
midpoint.service: running
nginx.service: running
postgresql-16.service: running
```

midPoint REST administrator access was verified.

AD resource imported into midPoint:

```text
Name: Arencloud AD
OID: 8f1c6c56-35b7-4a48-9d96-f6b6cc9f3c03
Connector: com.evolveum.polygon.connector.ldap.ad.AdLdapConnector
Host: ad01.ad.arencloud.com
Port: 636
Connection security: ssl
Bind DN: CN=midPoint AD Service,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
Base context: OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

The midPoint AD resource connection test completed successfully.

The AD LDAPS certificate chain was imported into the midPoint keystore:

```text
/var/opt/midpoint/keystore.jceks
```

Balance roles created in midPoint:

| midPoint role | Identifier | Requestable | AD group inducement |
| --- | --- | --- | --- |
| `Balance User` | `balance_user` | yes | `balance-users` |
| `Balance Approver` | `balance_approver` | yes | `balance-approvers` |
| `Balance Auditor` | `balance_auditor` | yes | `balance-auditors` |
| `Balance Admin` | `balance_admin` | yes | `balance-admins` |

Balance test users created in midPoint:

| User | Intended access |
| --- | --- |
| `balance.employee` | Standard Balance user |
| `balance.approver` | Standard user plus approval rights |
| `balance.auditor` | Read-only/reporting |
| `balance.admin` | Standard user plus administrative rights |

## midPoint Provisioning Status

The current integration establishes AD connectivity, governance roles, and AD provisioning mappings:

- account object type for AD users under `OU=Users,OU=Arencloud,...`
- entitlement object type for AD groups under `OU=Groups,OU=Arencloud,...`
- role inducements that add users to the matching AD groups when Balance roles are assigned

The midPoint provisioning account was delegated full control on the lab-managed subtree:

```text
OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

Validated AD state after recompute:

| AD user | AD groups |
| --- | --- |
| `balance.employee` | `balance-users` |
| `balance.approver` | `balance-users`, `balance-approvers` |
| `balance.auditor` | `balance-auditors` |
| `balance.admin` | `balance-users`, `balance-admins` |

Remaining hardening before production:

- replace broad lab delegation with least-privilege AD delegation
- add scheduled reconciliation/import tasks for users and groups
- define approval workflows for privileged Balance roles
