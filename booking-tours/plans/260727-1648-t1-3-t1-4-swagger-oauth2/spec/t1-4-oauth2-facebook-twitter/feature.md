---
feature_id: F004
title: OAuth2 Facebook + Twitter Verification
lang: vi
status: draft
created: 2026-07-27
---

# F004 — OAuth2 Facebook + Twitter

## Mục tiêu

Thêm đăng nhập OAuth2 qua Facebook và Twitter vào hệ thống. Cấu hình credentials qua `.env`,
xử lý user info qua `CustomStandardOAuth2UserService` (non-OIDC).

## Phạm vi

### Thêm mới
- `.env` — thêm `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET`, `TWITTER_CLIENT_ID`, `TWITTER_CLIENT_SECRET`
- `CustomStandardOAuth2UserService.java` — xử lý Facebook/Twitter (standard OAuth2, không có OIDC/openid scope)

### Thay đổi
- `application-dev.properties` — thêm Facebook + Twitter registration + Twitter provider config
- `application-test.properties` — thêm stub cho Facebook + Twitter (test context)
- `SecurityConfig.java` — thêm `.userService(customStandardOAuth2UserService)` bên cạnh `.oidcUserService()`
- `CustomAuthorizationRequestResolver.java` — giới hạn `prompt=select_account` chỉ cho Google

## Chi tiết kỹ thuật

### .env (thêm vào)
```properties
FACEBOOK_CLIENT_ID=your_facebook_app_id
FACEBOOK_CLIENT_SECRET=your_facebook_app_secret
TWITTER_CLIENT_ID=your_twitter_client_id
TWITTER_CLIENT_SECRET=your_twitter_client_secret
```

### application-dev.properties
```properties
# Facebook (Spring Security có built-in provider)
spring.security.oauth2.client.registration.facebook.client-id=${FACEBOOK_CLIENT_ID}
spring.security.oauth2.client.registration.facebook.client-secret=${FACEBOOK_CLIENT_SECRET}
spring.security.oauth2.client.registration.facebook.scope=public_profile,email
spring.security.oauth2.client.registration.facebook.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}

# Twitter OAuth 2.0 (PKCE — custom provider)
spring.security.oauth2.client.registration.twitter.client-id=${TWITTER_CLIENT_ID}
spring.security.oauth2.client.registration.twitter.client-secret=${TWITTER_CLIENT_SECRET}
spring.security.oauth2.client.registration.twitter.client-authentication-method=client_secret_basic
spring.security.oauth2.client.registration.twitter.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.twitter.scope=tweet.read,users.read,offline.access
spring.security.oauth2.client.registration.twitter.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}
spring.security.oauth2.client.provider.twitter.authorization-uri=https://twitter.com/i/oauth2/authorize
spring.security.oauth2.client.provider.twitter.token-uri=https://api.twitter.com/2/oauth2/token
spring.security.oauth2.client.provider.twitter.user-info-uri=https://api.twitter.com/2/users/me
spring.security.oauth2.client.provider.twitter.user-name-attribute=data
```

### CustomStandardOAuth2UserService.java
```
@Service
OAuth2UserService<OAuth2UserRequest, OAuth2User>
- Delegate tới DefaultOAuth2UserService
- Xác định provider từ registrationId (facebook/twitter)
- Facebook: attribute "id" → providerUserId, "email" (optional), "name"
- Twitter: attribute "data" → nested { "id", "name", "username" } (không có email)
- Gọi oAuthUserRegistrationService.findOrCreateUser(...)
- Return DefaultOAuth2User với authority từ DB
```

### SecurityConfig thay đổi
```java
.userInfoEndpoint(userInfo -> userInfo
    .oidcUserService(customOAuth2UserService)        // Google (OIDC)
    .userService(customStandardOAuth2UserService)    // Facebook, Twitter (standard OAuth2)
)
```

### CustomAuthorizationRequestResolver thay đổi
- Chỉ thêm `prompt=select_account` khi `registrationId == "google"`
- Facebook/Twitter: trả về request gốc không bổ sung param

## Điều kiện chấp nhận
- Truy cập `/oauth2/authorization/facebook` redirect đến Facebook OAuth dialog
- Truy cập `/oauth2/authorization/twitter` redirect đến Twitter OAuth dialog
- Sau callback: user được tạo/tìm trong DB, JWT cookie được set, redirect đến `/tours`
- Bảng `oauth_accounts` có bản ghi với provider=FACEBOOK/TWITTER

## Lưu ý quan trọng
- Twitter API v2 không trả về `email` trong user-info. `email` field trong User entity sẽ là `null` hoặc được sinh tự động (e.g., `twitter_{id}@noemail.local`) để đảm bảo NOT NULL constraint.
- Facebook cần app được verify và có quyền `email` — trong dev mode, chỉ admin của app có thể test.
