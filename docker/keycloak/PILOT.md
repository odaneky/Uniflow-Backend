# Community College Pilot — Production Keycloak

For pilot deployments, run Keycloak in production mode rather than `start-dev`:

```bash
cd UniPro-Backend/docker/keycloak
export KC_HOSTNAME=pilot.example.edu
export KC_HTTPS_CERTIFICATE_FILE=/path/to/tls.crt
export KC_HTTPS_CERTIFICATE_KEY_FILE=/path/to/tls.key
./kc.sh start --hostname-strict=true --http-enabled=false
```

## Pilot checklist

- [ ] TLS termination on Keycloak and API
- [ ] Remove or disable `university-lms-dev` password-grant client
- [ ] Enable MFA (TOTP) via Keycloak authentication flow for staff roles
- [ ] Set `sslRequired: all` on the realm
- [ ] Store admin credentials in a secret manager
- [ ] Set `VITE_PILOT_MODE=true` on the frontend build to hide mock-only screens

Staff should authenticate with MFA before accessing student records. FERPA record-access events are logged at `GET /api/v1/record-access/students/{id}`.
