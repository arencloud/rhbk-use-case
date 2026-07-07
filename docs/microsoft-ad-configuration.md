# Microsoft AD Configuration

## Purpose

This document describes the Microsoft Active Directory baseline used by the Arencloud identity lab. AD is the credential store and group source for RHBK, and it is the provisioning target managed by midPoint.

## Baseline

| Item | Value |
| --- | --- |
| Domain | `ad.arencloud.com` |
| Domain controller | `AD01.ad.arencloud.com` |
| Domain controller IP | `10.10.30.11` |
| LDAPS endpoint | `ldaps://ad01.ad.arencloud.com:636` |
| Managed subtree | `OU=Arencloud,DC=ad,DC=arencloud,DC=com` |

## OU Layout

Create this OU layout in AD:

```text
OU=Arencloud,DC=ad,DC=arencloud,DC=com
OU=Users,OU=Arencloud,DC=ad,DC=arencloud,DC=com
OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

`OU=Users` and `OU=Groups` are managed by midPoint. Direct manual changes should be limited to break-glass or infrastructure tasks.

## Balance Groups

Create the Balance authorization groups:

| AD group | RHBK role | Purpose |
| --- | --- | --- |
| `balance-users` | `balance_user` | Standard Balance access |
| `balance-approvers` | `balance_approver` | Approve/reject Balance requests |
| `balance-auditors` | `balance_auditor` | Read-only audit/reporting access |
| `balance-admins` | `balance_admin` | Balance administration |

Expected distinguished names:

```text
CN=balance-users,OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
CN=balance-approvers,OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
CN=balance-auditors,OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
CN=balance-admins,OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com
```

## Service Accounts

Create two separate AD service accounts:

| Account | sAMAccountName | Purpose | Permission model |
| --- | --- | --- | --- |
| `CN=midPoint AD Service,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com` | `svc_midpoint_ad` | midPoint provisioning | Delegated control over the lab-managed subtree |
| `CN=RHBK LDAP Bind,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com` | `rhbk-ldap-bind` | RHBK LDAP federation | Read-only LDAP bind |

Both service account passwords are stored in Vault, not Git.

The RHBK bind password is projected into OpenShift by External Secrets and mounted into RHBK as a Keycloak file vault. The midPoint bind password is used by the midPoint AD resource.

## LDAPS

Configure LDAPS on AD01 before connecting midPoint or RHBK:

1. Issue or import a certificate for `ad01.ad.arencloud.com` with Server Authentication EKU.
2. Import the issuing CA chain into the Windows local machine trust store.
3. Import the LDAPS certificate and private key into the Windows local machine personal certificate store.
4. Reboot AD01 or restart AD DS in a maintenance window so the service picks up the certificate.
5. Verify `ldaps://ad01.ad.arencloud.com:636` from the midPoint VM and from OpenShift workloads.

Use the FQDN `ad01.ad.arencloud.com` for LDAPS. Do not connect with the short name `AD01`, because the short name is not in the certificate SAN.

The full certificate issuance, import, renewal task, and validation runbook is in [AD01 LDAPS configuration](ad01-ldaps-configuration.md).

## Vault Paths

| Credential | Vault logical path | Required key |
| --- | --- | --- |
| RHBK LDAP bind for cl03 | `arencloud/cl03/rhbk/ad-bind` | `bindCredential` |
| RHBK LDAP bind for cl02 | `arencloud/cl02/rhbk/ad-bind` | `bindCredential` |
| midPoint AD provisioning bind | `arencloud/cl03/midpoint/ad-bind` | `bindCredential` |

## Validation

From the midPoint VM:

```bash
LDAPTLS_CACERT=/etc/pki/ca-trust/source/anchors/vault-root-ca.pem \
ldapsearch -LLL \
  -H ldaps://ad01.ad.arencloud.com:636 \
  -D "CN=midPoint AD Service,OU=Service Accounts,OU=Arencloud,DC=ad,DC=arencloud,DC=com" \
  -W \
  -b "OU=Groups,OU=Arencloud,DC=ad,DC=arencloud,DC=com" \
  "(cn=balance-*)" cn
```

Expected result: the four Balance groups are returned.

From OpenShift, validate DNS and LDAPS reachability from an RHBK-capable namespace:

```bash
oc run -n rhbk ldap-test --rm -it --restart=Never \
  --image=registry.access.redhat.com/ubi9/ubi -- bash
```

Inside the temporary pod:

```bash
getent hosts ad01.ad.arencloud.com
timeout 5 bash -c '</dev/tcp/ad01.ad.arencloud.com/636' && echo ldaps-open
```

## Operating Rules

- Use LDAPS on port `636` for midPoint and RHBK.
- Keep LDAP port `389` only for troubleshooting or bootstrap.
- Let midPoint manage normal user lifecycle and application group membership.
- Keep RHBK LDAP bind read-only.
- Store bind credentials in Vault only.
- Rotate service account credentials through a planned process that updates Vault and verifies External Secrets/midPoint afterwards.

