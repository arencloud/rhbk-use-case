# midPoint Admin Operating Model

## Rule

Do not use the built-in `administrator` account for normal operations.

Use `administrator` only for:

- initial platform setup
- connector/resource repair
- emergency recovery
- upgrades
- break-glass access

Normal AD user lifecycle, group membership, governance, access assignment, and review should be performed with named midPoint users.

## Configured Admin Roles

| Role | Identifier | Purpose |
| --- | --- | --- |
| `Arencloud Platform Administrator` | `arencloud_platform_administrator` | Named full midPoint administrator for platform configuration and emergency operations |
| `Identity Administrator` | `identity_administrator` | User lifecycle, organizations, assignments, and AD projection execution |
| `Balance Access Manager` | `balance_access_manager` | Assign and remove Balance application access roles |
| `Security Auditor` | `security_auditor` | Read-only review of users, roles, resources, shadows, and tasks |

## Configured Admin Users

| User | Assigned role |
| --- | --- |
| `egevorky.admin` | `Arencloud Platform Administrator` |
| `identity.admin` | `Identity Administrator` |
| `balance.owner` | `Balance Access Manager` |
| `security.auditor` | `Security Auditor` |

Temporary initial passwords are stored in Vault:

```text
arencloud/cl03/midpoint/admin-users
```

Rotate these passwords after first login.

## Operational Use

Use `egevorky.admin` for platform-level administration instead of the shared built-in `administrator` account.

Use `identity.admin` for day-to-day user lifecycle work:

- create users
- update user attributes
- assign or remove governed roles
- recompute users when needed
- trigger AD provisioning changes through midPoint

Use `balance.owner` for application access management:

- assign `Balance User`
- assign `Balance Approver`
- assign `Balance Auditor`
- assign `Balance Admin`
- remove Balance access when it is no longer required

Use `security.auditor` for review:

- inspect users
- inspect role assignments
- inspect AD projections/shadows
- review resources and tasks

## Source Of Truth

midPoint is the source of truth for managed access.

For normal users, do not manually add or remove members in these AD groups:

- `balance-users`
- `balance-approvers`
- `balance-auditors`
- `balance-admins`

Instead, assign or remove the matching midPoint role and let midPoint update AD.

## Production Hardening

Current lab configuration is intentionally pragmatic. Before production:

- replace broad AD delegation with least-privilege permissions
- add approval workflows for privileged Balance roles
- enable mandatory password rotation for admin personas
- connect admin users to enterprise authentication through RHBK or AD
- create reconciliation tasks for users, groups, and shadows
- enable regular access review campaigns
