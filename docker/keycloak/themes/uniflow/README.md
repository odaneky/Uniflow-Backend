# uniflow — Keycloak login theme

Keycloak **26.0** login theme for UniFlow. Extends `keycloak` so every flow we do not override
(WebAuthn, TOTP setup, IdP linking, terms, expired login, …) still works.

```
themes/uniflow/login/
  theme.properties          parent + class mappings (no PatternFly)
  template.ftl              page chrome: wordmark, card, alerts
  footer.ftl                optional card footer
  login.ftl                 username / password
  login-reset-password.ftl
  login-otp.ftl
  register.ftl              inherited user-profile form, our layout
  error.ftl
  messages/messages_en.properties
  resources/css/login.css   brand tokens at :root
  resources/img/logo.svg
```

## Install

Mount the theme **next to** the built-in themes, never over `/opt/keycloak/themes`:

```yaml
volumes:
  - ./docker/keycloak/themes/uniflow:/opt/keycloak/themes/uniflow:ro
```

That line is already in `UniPro-Backend/docker-compose.yml`. Recreate Keycloak:

```bash
docker compose up -d --force-recreate keycloak
```

## Enable

Admin console (`http://localhost:8081`, `admin` / `admin`):

**Realm settings → Themes → Login theme → uniflow → Save**

Then open a login: `http://localhost:8081/realms/university-lms/account` or the SPA welcome
page at `/` (Students or Staff & faculty).

## Whitelabel / deploy branding

The SPA reads **effective** branding from `GET /api/v1/branding` (public). Deploy defaults come
from `lms.branding` / `LMS_*` env vars; System admin overrides them in **System settings → Branding**.

This Keycloak theme is **deploy-time only** (option 2A). Align it with the same env values when you
ship a campus:

| Theme | Align with |
|---|---|
| `template.ftl` wordmark | `LMS_WORDMARK` |
| `messages_en.properties` `loginTitleHtml` | `LMS_PRODUCT_NAME` |
| `login.css` `--uf-primary` / `--uf-accent` | `LMS_PRIMARY_COLOR` / `LMS_ACCENT_COLOR` |
| `resources/img/logo.svg` | `LMS_LOGO_URL` (replace the file, or point favicon at the same asset) |

Admin saves in UniFlow update the portal immediately; they do **not** rewrite these theme files.
Redeploy / recreate Keycloak after changing the theme volume.

## Restyle

Edit CSS variables in `resources/css/login.css`:

| Token | Default | Role |
|---|---|---|
| `--uf-primary` | `#171717` | Buttons (UniFlow near-black) |
| `--uf-accent` | `#3b82f6` | Optional accent |
| `--uf-bg` | `#fafafa` | Page background |
| `--uf-font` | Inter | Typeface |

Drop a PNG/SVG over `resources/img/logo.svg`. Dark mode follows `prefers-color-scheme`.
