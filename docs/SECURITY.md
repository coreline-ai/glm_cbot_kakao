# Security Policy

## 🛡️ Security Overview

This project maintains a minimal and clean architecture to minimize the attack surface. We prioritize the security of AI API keys and data privacy.

## 🔒 Security Practices

### 1. API Key Protection
- **Environment Variables**: All sensitive keys (e.g., `GLM_API_KEY`) must be stored in a `.env` file.
- **Git Safety**: The `.env` file is included in `.gitignore` to prevent accidental public exposure.
- **Sample Files**: Only `.env.example` should be committed to the repository.

### 2. Data Privacy
- **Stateless Design**: The server is designed to be stateless by default (minimal version).
- **No Persistence**: Chat history is not stored on the server side in the current version, ensuring no personal data leakage through database breaches.

### 3. Network Security
- **CORS Configuration**: CORS is configured to allow specific origins or be strictly managed in production.
- **Local IP Binding**: For Android development, specific IP bindings (10.0.2.2) are documented to ensure safe internal communication.

## 🚀 Supported Versions

Security updates are currently provided for the following versions:

| Version | Supported |
| ------- | --------- |
| 1.0.x   | ✅ Yes     |
| < 1.0.0 | ❌ No      |

## 🐛 Reporting a Vulnerability

If you discover a security vulnerability, please do NOT open a public issue. Instead:

1. **Email us**: Send a detailed report to `security@coreline.ai`.
2. **Details to include**:
   - Description of the vulnerability.
   - Steps to reproduce.
   - Potential impact.
3. **Response Time**: We aim to respond to all security reports within 48 hours.

## 📜 Legal Notice

This software is provided "as is" under the MIT License. While we strive for security excellence, users are responsible for their individual deployment and environment security configurations.
