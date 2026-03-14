# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

- **Preferred**: Open a [GitHub Security Advisory](https://github.com/JoursBleu/ssh-server/security/advisories/new)
- **Do NOT** open a public Issue for security vulnerabilities

We will acknowledge receipt within 48 hours and provide a timeline for a fix.

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅        |

## Security Design

- **Host key**: RSA 3072-bit, generated once and stored in app-private storage
- **Authentication**: Public key (RSA/Ed25519) and optional password
- **Key storage**: authorized_keys stored in Android internal storage (not accessible without root)
- **Transport**: All communication is encrypted via SSH protocol
- **Dependencies**: Apache MINA SSHD 2.17.1, BouncyCastle 1.80
