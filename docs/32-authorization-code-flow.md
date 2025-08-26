# Buổi 32 — Implement luồng Authentication với **Authorization Code**

**Ngày:** 2025-08-24 (UTC+7)

---

## 🎯 Mục tiêu
- Chuyển từ **Implicit Flow** → **Authorization Code Flow** (an toàn hơn).
- FE nhận **authorization code**, BE gọi Google **/token** để **exchange code → access_token**.
- Tích hợp **Spring Cloud OpenFeign** để gọi Google OAuth endpoint.
- Bảo mật & cấu hình bằng **application.yaml**.

---

## 🛠 Công cụ & môi trường
- React 18 (Router v6), MUI.
- Spring Boot 3.5.4, Spring Security (giữ nguyên logic JWT nội bộ).
- Spring Cloud OpenFeign (compatible với Boot 3.5.x).
- Google Cloud Console (OAuth 2.0 Client).

---

## ⚙️ 1) FE — Chuyển `response_type` sang **code**
**`Login.jsx`** — (chỉ thay đổi URL building; phần còn lại **giữ nguyên**)
```jsx
const handleClick = () => {
  const callbackUrl = OAuthConfig.redirectUri;   // http://localhost:3000/authenticate
  const authUrl = OAuthConfig.authUri;           // https://accounts.google.com/o/oauth2/auth
  const googleClientId = OAuthConfig.clientId;

  const targetUrl = `${authUrl}?redirect_uri=${encodeURIComponent(callbackUrl)}&response_type=code&client_id=${googleClientId}&scope=openid%20email%20profile`;
  window.location.href = targetUrl;
};
```

---

## ⚙️ 2) BE — Thêm dependency OpenFeign
**`pom.xml`**
```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<properties>
  <spring-cloud.version>2025.0.0</spring-cloud.version>
</properties>
```

> ✔️ **Bật Feign**: trong lớp Application chính, thêm `@EnableFeignClients` (các phần khác **giữ nguyên**).

---

## ⚙️ 3) DTO cho exchange token (snake_case)
> Chỉ hiển thị field mới; **không lặp lại import**

**`ExchangeTokenRequest`**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExchangeTokenRequest {
    String code;
    String clientId;
    String clientSecret;
    String grantType;
    String redirectUri;
}
```

**`ExchangeTokenResponse`**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExchangeTokenResponse {
    String accessToken;
    Long   expiresIn;
    String refreshToken;
    String scope;
    String tokenType;
}
```

---

## ⚙️ 4) Feign client gọi Google OAuth
**`OutboundIdentityClient`**
```java
@FeignClient(name = "outbound-identity-client", url = "https://oauth2.googleapis.com")
public interface OutboundIdentityClient {

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ExchangeTokenResponse exchangeToken(@QueryMap ExchangeTokenRequest request);
}
```

> ℹ️ Gửi theo `application/x-www-form-urlencoded`. Ở Spring Cloud OpenFeign, `@QueryMap` sẽ serialize field thành form params cho request body (tuỳ encoder). Nếu provider yêu cầu chặt chẽ, có thể chuyển sang `MultiValueMap<String,String>` với `@RequestBody` + header `Content-Type`.

---

## ⚙️ 5) Cấu hình `application.yaml`
```yaml
outbound:
  identity:
    client-id: 850035654893-lft23uc6jkrs8u7l8t2svf8dfnbtpa4q.apps.googleusercontent.com
    client-secret: ${GOOGLE_CLIENT_SECRET:your_google_client_secret}
    redirect-uri: http://localhost:3000/authenticate
```

> ⚠️ **Bảo mật**: đưa `client-secret` vào **biến môi trường/secret manager** trong môi trường thật.

---

## ⚙️ 6) Service — Exchange code → access_token
> **Giữ nguyên** các method cũ; **thêm** outbound auth

**`AuthenticationService`** (chỉ phần mới)
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class AuthenticationService {
    OutboundIdentityClient outboundIdentityClient;

    @NonFinal @Value("${outbound.identity.client-id}")     String CLIENT_ID;
    @NonFinal @Value("${outbound.identity.client-secret}") String CLIENT_SECRET;
    @NonFinal @Value("${outbound.identity.redirect-uri}")  String REDIRECT_URI;

    @NonFinal String GRANT_TYPE = "authorization_code";

    public AuthenticationResponse outboundAuthentication(String code) {
        var tokenRes = outboundIdentityClient.exchangeToken(
            ExchangeTokenRequest.builder()
                .code(code)
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .redirectUri(REDIRECT_URI)
                .grantType(GRANT_TYPE)
                .build()
        );

        return AuthenticationResponse.builder()
                .token(tokenRes.getAccessToken())
                .authenticated(true)
                .build();
    }
}
```

---

## ⚙️ 7) Controller — Endpoint mới
> **Các endpoint cũ giữ nguyên**, bổ sung thêm:

**`AuthenticationController`**
```java
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
public class AuthenticationController {
    AuthenticationService authenticationService;

    @PostMapping("outbound/authentication")
    ApiResponse<AuthenticationResponse> outboundAuthentication(@RequestParam String code) {
        return ApiResponse.<AuthenticationResponse>builder()
                .result(authenticationService.outboundAuthentication(code))
                .build();
    }
}
```

---

## ⚙️ 8) Security — Public endpoint
> Thêm path mới vào danh sách public (các cấu hình khác **giữ nguyên**):

```java
private static final String[] PUBLIC_ENDPOINTS = {
    "/users", "/auth/token", "/auth/introspect", "/auth/logout", "/auth/refresh",
    "/auth/outbound/authentication"
};
```

---

## ⚙️ 9) FE — Nhận **code** và gọi BE exchange
**`Authenticate.jsx`**
```jsx
useEffect(() => {
  const authCodeRegex = /code=([^&]+)/;
  const match = window.location.href.match(authCodeRegex);

  if (match) {
    const authCode = match[1];
    fetch(`http://localhost:8080/identity/auth/outbound/authentication?code=${authCode}`, {
      method: "POST",
    })
      .then(res => res.json())
      .then(data => {
        setToken(data.result?.token);
        setIsLoggedin(true);
      });
  }
}, []);
```

> 🔧 **React Strict Mode** (dev) có thể chạy effect 2 lần → cân nhắc **tắt StrictMode** khi test hoặc kiểm soát bằng flag.

---

## 🧪 10) Test luồng end-to-end
1. FE → `Login` → **Continue with Google**.  
2. Google login + consent → redirect về `http://localhost:3000/authenticate?code=...`.  
3. FE gọi **POST** `/identity/auth/outbound/authentication?code=...`.  
4. BE (Feign) gọi Google **/token** → nhận `access_token`.  
5. BE trả `AuthenticationResponse` (token) → FE lưu token.  
6. FE điều hướng **Home** → (nếu muốn) dùng token gọi Google **userinfo**.

---

## 📌 Điều học được
- Sự khác nhau giữa **Implicit** và **Authorization Code**.  
- Cách **exchange code → token** an toàn hơn (qua backend).  
- Tích hợp **OpenFeign** để đơn giản hoá outbound HTTP call.  
- Quản lý **client_id/secret/redirect_uri** qua cấu hình.

---

## 🧭 Gợi ý nâng cấp
- Dùng **Authorization Code + PKCE** (chuẩn cho SPA).  
- BE **xác thực token** và **map** Google user ↔ user nội bộ (SSO).  
- Lưu **refresh_token** an toàn (server-side), triển khai **token refresh**.  
- Ẩn `client-secret` bằng **env vars** / **Secret Manager**.

