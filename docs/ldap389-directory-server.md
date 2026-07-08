# 389 Directory Server Configuration

## Purpose

389 Directory Server is the second directory target for the Arencloud identity lab. It is managed by midPoint alongside Microsoft AD and gives RHBK a second LDAP-compatible identity source for the same Balance authorization model.

The intended flow is:

```text
midPoint assignment
  -> 389 DS user and group membership
  -> RHBK LDAP federation over LDAPS
  -> Balance realm roles
  -> application authorization
```

## Baseline

| Item | Value |
| --- | --- |
| Host | `ldap01.arencloud.com` |
| IP address | `10.10.30.12` |
| OS | AlmaLinux 9 |
| Directory instance | `arencloud` |
| Systemd unit | `dirsrv@arencloud` |
| LDAP endpoint | `ldap://ldap01.arencloud.com:389` |
| LDAPS endpoint | `ldaps://ldap01.arencloud.com:636` |
| Suffix | `dc=ldap,dc=arencloud,dc=com` |
| Directory Manager DN | `cn=Directory Manager` |
| TLS CA | Vault PKI root and intermediate chain |
| Certificate source | Vault `pki_webapps_common` mount, `web-server` role |

Fix DNS before using this service from OpenShift or midPoint. `ldap01.arencloud.com` should resolve to `10.10.30.12` from the midPoint VM and from cluster workloads. Validate the IPv4 record explicitly:

```bash
getent ahostsv4 ldap01.arencloud.com
```

Expected result: `10.10.30.12`.

## Package Installation

Install the server and LDAP client utilities:

```bash
sudo dnf install -y 389-ds-base openldap-clients policycoreutils-python-utils
```

Create the instance with `dscreate` using an installer file. The installer file must be deleted after the instance is created because it contains the Directory Manager password.

Important instance settings:

```ini
[general]
full_machine_name = ldap01.arencloud.com
start = True
strict_host_checking = False

[slapd]
instance_name = arencloud
root_dn = cn=Directory Manager
port = 389
secure_port = 636
suffix = dc=ldap,dc=arencloud,dc=com
self_sign_cert = False
```

Enable the service:

```bash
sudo systemctl enable --now dirsrv@arencloud
```

## Firewall

LDAP and LDAPS are opened on the host firewall:

```bash
sudo firewall-cmd --permanent --add-service=ldap
sudo firewall-cmd --permanent --add-service=ldaps
sudo firewall-cmd --reload
```

Expected result:

```bash
sudo firewall-cmd --list-services
```

The output should include `ldap` and `ldaps`.

## Vault Secrets

The credentials are stored in Vault under shared paths so both cl02 and cl03 can consume them through External Secrets when needed.

| Credential | Vault logical path | Required keys |
| --- | --- | --- |
| Directory Manager | `arencloud/shared/ldap389/directory-manager` | `bindDn`, `password` |
| midPoint provisioning bind | `arencloud/shared/ldap389/midpoint-bind` | `bindDn`, `uid`, `password` |
| RHBK read-only bind | `arencloud/shared/ldap389/rhbk-bind` | `bindDn`, `uid`, `password`, `bindCredential` |

Do not store these values in Git or shell history. Retrieve them only when configuring midPoint, RHBK, or break-glass administration.

The shared External Secrets Vault policy is `shared-ldap389-read`. It grants read access to:

```text
arencloud/data/shared/ldap389/*
arencloud/metadata/shared/ldap389/*
```

The policy is attached to both Vault Kubernetes auth roles:

```text
eso-cl02-role
eso-cl03-role
```

`ClusterSecretStore/vault` remains unchanged on the OpenShift clusters.

## Directory Layout

The managed suffix contains three organizational units:

```text
dc=ldap,dc=arencloud,dc=com
ou=people,dc=ldap,dc=arencloud,dc=com
ou=groups,dc=ldap,dc=arencloud,dc=com
ou=service-accounts,dc=ldap,dc=arencloud,dc=com
```

`ou=people` and `ou=groups` are intended to be managed by midPoint. Direct manual changes should be limited to bootstrap, troubleshooting, or break-glass operations.

## Service Accounts

| Account | DN | Purpose |
| --- | --- | --- |
| midPoint bind | `uid=svc_midpoint_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com` | Provision users and group membership |
| RHBK bind | `uid=svc_rhbk_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com` | Read users and groups for LDAP federation |
| Placeholder | `uid=group-placeholder,ou=service-accounts,dc=ldap,dc=arencloud,dc=com` | Locked placeholder member for empty `groupOfNames` groups |

The placeholder account is locked with `nsAccountLock: true`.

## Balance Groups

389 DS uses hyphenated LDAP group names, matching the AD group model. RHBK maps those groups to underscore-based realm roles.

| 389 DS group | RHBK role | Purpose |
| --- | --- | --- |
| `balance-users` | `balance_user` | Standard Balance access |
| `balance-approvers` | `balance_approver` | Approve or reject Balance requests |
| `balance-auditors` | `balance_auditor` | Read-only audit/reporting access |
| `balance-admins` | `balance_admin` | Balance administration |

Expected group DNs:

```text
cn=balance-users,ou=groups,dc=ldap,dc=arencloud,dc=com
cn=balance-approvers,ou=groups,dc=ldap,dc=arencloud,dc=com
cn=balance-auditors,ou=groups,dc=ldap,dc=arencloud,dc=com
cn=balance-admins,ou=groups,dc=ldap,dc=arencloud,dc=com
```

The groups are `groupOfNames` objects. Each group initially contains the locked placeholder member because `groupOfNames` requires at least one `member`.

## Access Control

The server requires secure simple binds:

```bash
sudo dsconf arencloud config replace nsslapd-require-secure-binds=on
```

ACI model:

| Subtree | midPoint bind | RHBK bind |
| --- | --- | --- |
| `ou=people,dc=ldap,dc=arencloud,dc=com` | `all` | `read`, `search`, `compare` |
| `ou=groups,dc=ldap,dc=arencloud,dc=com` | `all` | `read`, `search`, `compare` |
| `ou=service-accounts,dc=ldap,dc=arencloud,dc=com` | No broad delegated access | No broad delegated access |

This keeps midPoint as the write owner and keeps RHBK read-only.

## Directory Plugins

The following plugins are enabled:

| Plugin | Reason |
| --- | --- |
| Referential Integrity | Removes deleted user DNs from group `member` attributes |
| MemberOf | Maintains user-side `memberOf` values for group membership lookup |

Commands:

```bash
sudo dsconf arencloud plugin referential-integrity enable
sudo dsconf arencloud plugin memberof enable
sudo systemctl restart dirsrv@arencloud
```

Validation:

```bash
sudo dsconf arencloud plugin referential-integrity show | grep -i nsslapd-pluginEnabled
sudo dsconf arencloud plugin memberof show | grep -i nsslapd-pluginEnabled
```

Expected result: both plugins are `on`.

## TLS Configuration

The VM trusts the Vault root CA through the AlmaLinux system trust store:

```bash
sudo install -m 0644 vault-root-ca.pem /etc/pki/ca-trust/source/anchors/vault-root-ca.pem
sudo update-ca-trust
```

The LDAPS certificate is issued from Vault:

| Item | Value |
| --- | --- |
| Vault PKI mount | `pki_webapps_common` |
| Vault PKI role | `web-server` |
| Common name | `ldap01.arencloud.com` |
| SAN | `DNS:ldap01.arencloud.com` |
| 389 DS NSS nickname | `Server-Cert` |

TLS is enabled in 389 DS:

```bash
sudo dsconf arencloud config replace nsslapd-security=on
sudo dsconf arencloud security set --tls-protocol-min TLS1.2 --tls-protocol-max TLS1.3
sudo dsconf arencloud security rsa set --tls-allow-rsa-certificates on
sudo dsconf arencloud security rsa set --nssslpersonalityssl Server-Cert
sudo dsconf arencloud security rsa set --nssslactivation on
sudo systemctl restart dirsrv@arencloud
```

## Certificate Renewal

Certificate renewal is automated on the VM using Vault AppRole and systemd.

Vault policy:

```hcl
path "pki_webapps_common/sign/web-server" {
  capabilities = ["update"]
}

path "pki_webapps_common/ca_chain" {
  capabilities = ["read"]
}

path "pki_root/ca/pem" {
  capabilities = ["read"]
}
```

Vault AppRole:

```text
ldap389-ldaps-renewal
```

VM files:

| File | Purpose | Permissions |
| --- | --- | --- |
| `/etc/ldap389-renewal/vault-approle.json` | AppRole RoleID and SecretID | `0600`, `root:root` |
| `/usr/local/sbin/ldap389-renew-ldaps` | Renewal script | `0700`, `root:root` |
| `/etc/systemd/system/ldap389-renew-ldaps.service` | One-shot renewal unit | `0644`, `root:root` |
| `/etc/systemd/system/ldap389-renew-ldaps.timer` | Daily renewal timer | `0644`, `root:root` |

The script renews only when the active LDAPS certificate has less than 15 days of validity remaining. It keeps the NSS certificate nickname stable as `Server-Cert`, imports the renewed certificate and CA chain, validates the LDAPS listener, and restarts `dirsrv@arencloud`.

Timer commands:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ldap389-renew-ldaps.timer
sudo systemctl status ldap389-renew-ldaps.timer
```

Manual check without forcing renewal:

```bash
sudo /usr/local/sbin/ldap389-renew-ldaps
```

Expected result when the certificate is still healthy:

```text
Current LDAPS certificate is valid for N days; renewal not needed.
```

## Validation

Service status:

```bash
sudo systemctl status dirsrv@arencloud
sudo ss -ltnp | grep -E ':389|:636'
```

TLS certificate:

```bash
echo Q | openssl s_client \
  -connect ldap01.arencloud.com:636 \
  -servername ldap01.arencloud.com \
  -CAfile /etc/pki/ca-trust/source/anchors/vault-root-ca.pem 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates -ext subjectAltName
```

Expected certificate properties:

```text
subject=CN=ldap01.arencloud.com
issuer=CN=intermediate_midpoint_ca
DNS:ldap01.arencloud.com
```

Secure bind enforcement:

```bash
ldapwhoami \
  -H ldap://ldap01.arencloud.com:389 \
  -D "uid=svc_rhbk_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com" \
  -W
```

Expected result: simple bind over plain LDAP is rejected because secure binds are required.

RHBK read-only bind over LDAPS:

```bash
LDAPTLS_CACERT=/etc/pki/ca-trust/source/anchors/vault-root-ca.pem \
ldapsearch -LLL \
  -H ldaps://ldap01.arencloud.com:636 \
  -D "uid=svc_rhbk_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com" \
  -W \
  -b "ou=groups,dc=ldap,dc=arencloud,dc=com" \
  "(cn=balance-*)" cn member
```

Expected result: the four Balance groups are returned.

midPoint provisioning bind validation:

```bash
LDAPTLS_CACERT=/etc/pki/ca-trust/source/anchors/vault-root-ca.pem \
ldapadd \
  -H ldaps://ldap01.arencloud.com:636 \
  -D "uid=svc_midpoint_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com" \
  -W \
  -f test-user.ldif
```

Expected result: midPoint bind can create and delete users under `ou=people` and update membership under `ou=groups`.

## midPoint Integration

The midPoint resource is:

| Item | Value |
| --- | --- |
| Resource name | `389ds-arencloud` |
| Resource OID | `08ac3417-4374-4a33-aad2-edba50342122` |
| Connector | `com.evolveum.polygon.connector.ldap.LdapConnector` |
| Connection | `ldap01.arencloud.com:636`, SSL |
| Base context | `dc=ldap,dc=arencloud,dc=com` |
| Account object class | `inetOrgPerson` |
| Group object class | `groupOfNames` |
| Account DN mapping | `uid=<midPoint user name>,ou=people,dc=ldap,dc=arencloud,dc=com` |
| Group association | object-to-subject through group `member` |

The midPoint account object type includes `ri:nsMemberOf` as an auxiliary object class. This is necessary because the 389 DS MemberOf plugin maintains `objectClass: nsMemberOf` and the computed `memberOf` attribute. Those values are server-managed and should be tolerated by midPoint, not removed.

The Balance roles in midPoint now induce both AD and 389 DS group memberships:

| midPoint role | 389 DS group |
| --- | --- |
| `Balance User` | `balance-users` |
| `Balance Approver` | `balance-approvers` |
| `Balance Auditor` | `balance-auditors` |
| `Balance Admin` | `balance-admins` |

Connector-side password hashing is intentionally not configured. 389 DS receives normal password values from midPoint and applies server-side password storage.

Validated behavior:

```text
midPoint direct 389 DS construction
  -> created uid=<test-user>,ou=people,dc=ldap,dc=arencloud,dc=com
  -> added user DN to cn=balance-users,ou=groups,dc=ldap,dc=arencloud,dc=com
  -> populated user memberOf
  -> deleted user
  -> Referential Integrity removed the group member value
```

## OpenShift Consumption

External Secrets should use the shared Vault paths when RHBK is configured to federate 389 DS:

```text
arencloud/shared/ldap389/rhbk-bind
```

Recommended RHBK LDAP federation values:

| Setting | Value |
| --- | --- |
| Vendor | `other` |
| Connection URL | `ldaps://ldap01.arencloud.com:636` |
| Users DN | `ou=people,dc=ldap,dc=arencloud,dc=com` |
| Bind DN | `uid=svc_rhbk_ldap,ou=service-accounts,dc=ldap,dc=arencloud,dc=com` |
| Bind credential | Vault key `bindCredential` |
| Username LDAP attribute | `uid` |
| RDN LDAP attribute | `uid` |
| UUID LDAP attribute | `entryUUID` |
| User object classes | `inetOrgPerson, organizationalPerson` |
| Groups DN | `ou=groups,dc=ldap,dc=arencloud,dc=com` |
| Group object class | `groupOfNames` |
| Membership attribute | `member` |
| Membership LDAP attribute | `memberOf` |

The `memberOf` plugin is enabled, so RHBK may use user-side `memberOf` lookup or group search by `member`. Group search by `member` remains the most explicit mapping for this lab because the managed groups are few and well-known.

## Next Steps

1. Configure RHBK LDAP federation for 389 DS after deciding whether it should be active alongside AD or used as a DR/secondary identity source.
2. Validate that one real Balance user can be provisioned to both AD and 389 DS from the same midPoint assignment model.
3. Add scheduled reconciliation/import tasks for the 389 DS resource in midPoint.
