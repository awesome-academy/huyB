---
doc_type: system-forward-draft
promotes_to: docs/system/architecture.md
status: draft
---

# API Documentation & OAuth2 Providers

## OpenAPI / Swagger (T1.3)

- **Library:** `springdoc-openapi-starter-webmvc-ui:2.6.0`
- **URL:** `http://localhost:8080/swagger-ui.html` (dev only — disabled in prod)
- **Security scheme:** API Key in Cookie (`JWT_TOKEN`) — documentational, không enforce trên Swagger UI
- **CSP:** thêm `'unsafe-eval'` vào `script-src` cho Swagger UI JS renderer

## OAuth2 Provider Matrix (T1.4)

| Provider | Flow | UserService | Email |
|---|---|---|---|
| Google | OIDC (openid scope) | `CustomOAuth2UserService` (OidcUserService) | Từ OIDC claims |
| Facebook | Standard OAuth2 | `CustomStandardOAuth2UserService` | Từ Graph API (có thể null) |
| Twitter | OAuth 2.0 PKCE | `CustomStandardOAuth2UserService` | Không có — generated fallback |

## Credential Management

Tất cả OAuth2 credentials lưu trong `.env` (gitignored), được import qua:
```
spring.config.import=optional:file:.env[.properties]
```

| Variable | Provider |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google |
| `FACEBOOK_CLIENT_ID` / `FACEBOOK_CLIENT_SECRET` | Facebook |
| `TWITTER_CLIENT_ID` / `TWITTER_CLIENT_SECRET` | Twitter |
