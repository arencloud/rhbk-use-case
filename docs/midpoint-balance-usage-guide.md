# midPoint Balance Usage Guide

## Current State

midPoint is available at:

```text
https://midpoint.arencloud.com/midpoint/
```

Configured objects:

| Object type | Objects |
| --- | --- |
| AD resource | `Arencloud AD` |
| Organizations | `Arencloud`, `Balance Application`, `Bank Employees` |
| Balance roles | `Balance User`, `Balance Approver`, `Balance Auditor`, `Balance Admin` |
| Test users | `balance.employee`, `balance.approver`, `balance.auditor`, `balance.admin` |

## Balance Access Model

| midPoint role | Role identifier | AD group | RHBK role |
| --- | --- | --- | --- |
| `Balance User` | `balance_user` | `balance-users` | `balance_user` |
| `Balance Approver` | `balance_approver` | `balance-approvers` | `balance_approver` |
| `Balance Auditor` | `balance_auditor` | `balance-auditors` | `balance_auditor` |
| `Balance Admin` | `balance_admin` | `balance-admins` | `balance_admin` |

Workflow intent:

```text
midPoint user -> assigned Balance role -> AD group membership -> RHBK LDAP federation -> balance app token claims
```

## How To Use midPoint

Use named admin users for operations. Do not use the built-in `administrator` account for normal access management. See [midPoint Admin Operating Model](midpoint-admin-operating-model.md).

### 1. Create a Bank Employee

In the midPoint UI:

1. Go to `Users`.
2. Create a new user.
3. Set at least:
   - `name`, for example `jane.employee`
   - `givenName`
   - `familyName`
   - `emailAddress`
4. Save the user.

Recommended naming:

```text
firstname.lastname
```

### 2. Assign Balance Access

On the user:

1. Open `Assignments`.
2. Add role:
   - `Balance User` for normal Balance access
   - `Balance Approver` for approval workflow rights
   - `Balance Auditor` for read-only/reporting access
   - `Balance Admin` only for application administrators
3. Save the assignment.
4. Recompute the user if provisioning does not run immediately.

### 3. Verify RHBK Login Path

After RHBK is deployed and LDAP federation is active:

1. Confirm the user exists in AD under:

```text
OU=Users,OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

2. Confirm group membership under:

```text
OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

3. Log in through:

```text
https://sso.arencloud.com/realms/arencloud
```

4. Confirm the `balance` client token contains the expected role.

## Configured Test Personas

| User | Intended access |
| --- | --- |
| `balance.employee` | Standard Balance user |
| `balance.approver` | Standard user plus approval rights |
| `balance.auditor` | Read-only/reporting |
| `balance.admin` | Standard user plus administrative rights |

These are lab personas for validating the workflow. They are not production identities.

## Implemented Provisioning

midPoint now manages the AD account and AD group membership for Balance users in the managed subtree:

```text
OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

Implemented mappings:

- AD user accounts are created under `OU=Users,OU=Arencloud,DC=ad,DC=arencloud,DC=com`.
- `Balance User` adds the user to `balance-users`.
- `Balance Approver` adds the user to `balance-approvers`.
- `Balance Auditor` adds the user to `balance-auditors`.
- `Balance Admin` adds the user to `balance-admins`.

Validated AD state:

| AD user | AD groups |
| --- | --- |
| `balance.employee` | `balance-users` |
| `balance.approver` | `balance-users`, `balance-approvers` |
| `balance.auditor` | `balance-auditors` |
| `balance.admin` | `balance-users`, `balance-admins` |

Operational rule: do not manually grant application access in AD for normal users. Assign or remove the matching role in midPoint, then let midPoint update AD.
