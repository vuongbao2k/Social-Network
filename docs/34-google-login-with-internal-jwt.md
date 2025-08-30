# Buổi 34 — Đăng nhập Google nhưng dùng **JWT nội bộ** của hệ thống

**Ngày:** 2025-08-24 (UTC+7)

---

## 🎯 Mục tiêu
- Sau khi user chọn **“Continue with Google”**, BE vẫn **phát hành JWT nội bộ** (không dùng token Google để gọi API hệ thống).
- FE lưu **JWT nội bộ** rồi gọi API `/users/my-info`.
- Giữ nguyên luồng **Authorization Code** (buổi 32) & **onboarding Google user** (buổi 33).

---

## 🛠 Công cụ & môi trường
- Frontend: React 18 (Router v6, MUI).
- Backend: Spring Boot 3.5.x, Spring Security (Resource Server), JWT nội bộ.
- OpenFeign cho Google `/token` & `/userinfo`.

---

## ⚙️ 1) Frontend — dùng **JWT nội bộ** để gọi API

### 1.1 `Login.jsx`  
(giữ nguyên ý tưởng từ buổi 32, chỉ nhắc lại phần quan trọng)

```jsx
const handleContinueWithGoogle = () => {
  const callbackUrl = OAuthConfig.redirectUri;    // http://localhost:3000/authenticate
  const authUrl = OAuthConfig.authUri;            // https://accounts.google.com/o/oauth2/auth
  const googleClientId = OAuthConfig.clientId;

  const targetUrl = `${authUrl}?redirect_uri=${encodeURIComponent(
    callbackUrl
  )}&response_type=code&client_id=${googleClientId}&scope=openid%20email%20profile`;

  window.location.href = targetUrl;
};
```

### 1.2 `Authenticate.jsx`  
- Nhận **code** từ Google.
- Gọi BE `/auth/outbound/authentication?code=...` (BE sẽ **exchange code → access_token Google**, onboard user, rồi **phát hành JWT nội bộ**).
- **Lưu JWT nội bộ** vào localStorage.

```jsx
useEffect(() => {
  const authCodeRegex = /code=([^&]+)/;
  const isMatch = window.location.href.match(authCodeRegex);

  if (isMatch) {
    const authCode = isMatch[1];

    fetch(`http://localhost:8080/identity/auth/outbound/authentication?code=${authCode}`, {
      method: "POST",
    })
      .then(res => res.json())
      .then(data => {
        // ✅ Lưu JWT nội bộ từ BE (không phải token Google)
        setToken(data.result?.token);
        setIsLoggedin(true);
      });
  }
}, []);
```

### 1.3 `Home.jsx`  
- Lấy token nội bộ từ localStorage và gọi **/users/my-info**.

```jsx
const getUserDetails = async (accessToken) => {
  const response = await fetch("http://localhost:8080/identity/users/my-info", {
    method: "GET",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const data = await response.json();
  setUserDetails(data.result);
};
```

> ✅ Từ đây, FE **không còn** gọi Google userinfo nữa. Mọi API hệ thống đều dùng **JWT nội bộ**.

---

## ⚙️ 2) Backend — phát hành **JWT nội bộ** sau khi exchange code

> Phần **exchange code** & **onboard user** giữ nguyên từ buổi 32–33.  
> Chỉ **thay đổi trả về**: thay vì trả **token Google**, ta **generate token nội bộ** từ `User`.

**`AuthenticationService.outboundAuthentication`** (phần cập nhật)
```java
public AuthenticationResponse outboundAuthentication(String code) {
    // 1) Exchange code -> access_token (giữ nguyên)
    var response = outboundIdentityClient.exchangeToken(ExchangeTokenRequest.builder()
            .code(code)
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .redirectUri(REDIRECT_URI)
            .grantType(GRANT_TYPE)
            .build());

    // 2) Lấy user info Google (giữ nguyên buổi 33) & onboard user nếu chưa có
    var userInfo = outboundUserClient.getUserInfo("json", response.getAccessToken());

    var roles = new HashSet<Role>();
    roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);

    var user = userRepository.findByUsername(userInfo.getEmail()).orElseGet(() -> {
        var newUser = User.builder()
                .username(userInfo.getEmail())
                .firstName(userInfo.getGivenName())
                .lastName(userInfo.getFamilyName())
                .roles(roles)
                .build();
        return userRepository.save(newUser);
    });

    // 3) ✅ Phát hành JWT NỘI BỘ từ user (scope/role/permission như buổi 18–21)
    var internalJwt = generateToken(user);

    return AuthenticationResponse.builder()
            .token(internalJwt)       // ✅ Trả JWT nội bộ thay vì token Google
            .authenticated(true)
            .build();
}
```

> Ghi chú:
> - `generateToken(User user)` đã có từ buổi 18–21 (thêm `jti`, `scope`, thời hạn, …).
> - `buildScope(user)` hiện đang sinh `"ROLE_..."` và các permission, mapping sang Spring Authorities đã cấu hình ở `JwtAuthenticationConverter`.

---

## 🧪 3) Test nhanh end-to-end
1. FE `Login` → **Continue with Google**.  
2. Google redirect về `/authenticate?code=...`.  
3. FE gọi `POST /auth/outbound/authentication?code=...` → **BE trả JWT nội bộ**.  
4. FE lưu token → vào `Home` → gọi `/users/my-info` **OK** (không phụ thuộc Google token).  
5. Log ra `roles`/`permissions` trong `/users/my-info` để kiểm chứng scope.

---

## 🔐 Tại sao nên dùng JWT nội bộ thay vì token Google?
- **Tách biệt domain**: Quyền trong hệ thống (ROLE/permission) không phụ thuộc Google.  
- **Kiểm soát bảo mật & thời hạn**: Chủ động cấu hình `exp`, `refresh`, `blacklist`, `introspect`.  
- **Không lộ client_secret** hoặc ràng buộc chính sách Google ở frontend.

---

## 🧠 Điều học được
- Cách chuyển đổi từ **SSO token** (Google) sang **JWT nội bộ** cho API trong hệ thống.  
- FE chỉ cần biết **token nội bộ**, đơn giản hoá gọi API và kiểm quyền.  
- Tận dụng sẵn **scope/role** đã thiết kế cho Spring Security.

---

## 🗺️ Hướng phát triển tiếp theo
- Liên kết tài khoản: nếu user đã có tài khoản mật khẩu nội bộ, cho phép **link** với Google account.  
- Đổi sang **Authorization Code + PKCE** cho SPA bảo mật hơn.  
- Hỗ trợ **refresh token** nội bộ khi đăng nhập bằng Google.  
- Thêm **logout all sessions** (revoke/blacklist theo `sub` hoặc `userId`).

