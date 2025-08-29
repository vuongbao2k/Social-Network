# Buổi 33 — Onboarding Google User vào hệ thống nội bộ

**Ngày:** 2025-08-24 (UTC+7)

---

## 🎯 Mục tiêu
- Lấy **Google user info** từ access token sau bước **Authorization Code** (buổi 32).
- **Onboard** (upsert) user Google vào DB nội bộ:
  - `username = email`
  - gán **role mặc định** `USER`
- Chuẩn hóa DTO theo **snake_case** của Google.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.x
- Spring Cloud OpenFeign (đã bật `@EnableFeignClients`)
- Google OAuth 2.0 (`/oauth2/v1/userinfo`)

---

## ⚙️ 1) Feign client gọi Google UserInfo
> Tạo client để đọc thông tin người dùng Google bằng `access_token`.

**`repository/httpclient/OutboundUserClient.java`**
```java
@FeignClient(name = "outbound-user-client", url = "https://www.googleapis.com")
public interface OutboundUserClient {

    @GetMapping(value = "/oauth2/v1/userinfo")
    OutboundUserResponse getUserInfo(
        @RequestParam("alt") String alt,
        @RequestParam("access_token") String accessToken
    );
}
```

---

## 📦 2) DTO phản hồi từ Google (snake_case → camelCase)
> Dùng `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` để map tự động.

**`dto/response/OutboundUserResponse.java`**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonNaming(value = PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OutboundUserResponse {
    String id;
    String email;
    boolean verifiedEmail;
    String name;
    String givenName;
    String familyName;
    String picture;
}
```

---

## 🔗 3) Luồng service: exchange code → lấy user info → upsert user
> Phần code cũ **giữ nguyên** (exchange token ở buổi 32).  
> Bổ sung gọi `OutboundUserClient` và **upsert** user vào DB.

**`AuthenticationService`** (chỉ phần logic mới; các field/bean khác **giữ nguyên**)
```java
public AuthenticationResponse outboundAuthentication(String code) {
    var response = outboundIdentityClient.exchangeToken(ExchangeTokenRequest.builder()
            .code(code)
            .clientId(CLIENT_ID)
            .clientSecret(CLIENT_SECRET)
            .redirectUri(REDIRECT_URI)
            .grantType(GRANT_TYPE)
            .build());

    // 1) Gọi Google lấy user info
    var userInfo = outboundUserClient.getUserInfo("json", response.getAccessToken());

    // 2) Chuẩn bị role mặc định USER
    Set<Role> roles = new HashSet<>();
    roles.add(Role.builder().name(PredefinedRole.USER_ROLE).build()); // default

    // 3) Upsert user (username = email)
    var user = userRepository.findByUsername(userInfo.getEmail()).orElseGet(() -> {
        var newUser = User.builder()
                .username(userInfo.getEmail())
                .firstName(userInfo.getGivenName())
                .lastName(userInfo.getFamilyName())
                .roles(roles)
                .build();
        return userRepository.save(newUser);
    });

    // 4) Trả token Google để FE dùng (ví dụ gọi Google API userinfo)
    return AuthenticationResponse.builder()
            .token(response.getAccessToken())
            .authenticated(true)
            .build();
}
```

### 💡 Gợi ý cải thiện (khuyến nghị)
- Thay vì tạo `Role` bằng `builder()` (transient), nên lấy **entity đã tồn tại** để tránh lỗi “transient object” ở một số mapping ManyToMany:
  ```java
  var roles = new HashSet<Role>();
  roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);
  ```
  > Ta đã tạo sẵn `ADMIN`/`USER` ở `ApplicationInitConfig`, nên **nên** lấy từ DB.

- Nếu muốn **đồng bộ tên/ảnh** khi user đã tồn tại:
  ```java
  user.setFirstName(userInfo.getGivenName());
  user.setLastName(userInfo.getFamilyName());
  userRepository.save(user);
  ```

---

## 🧪 4) Kiểm thử nhanh
1. FE login bằng Authorization Code (buổi 32).  
2. BE exchange code lấy `access_token` → gọi `OutboundUserClient.getUserInfo()`.  
3. Lần đầu email xuất hiện → **tạo user mới** với role `USER`.  
4. Lần sau login với email đó → **dùng lại user cũ**.  
5. FE có thể dùng token Google để hiển thị `name/picture/email`.

---

## 🧩 Giải thích nhanh luồng
- **Authorization Code** bảo mật hơn: FE chỉ nhận `code`, **BE** mới cầm client secret để đổi token.  
- Dùng token Google → gọi **userinfo** để lấy profile.  
- Ánh xạ **email → username** để nhất quán giữa SSO và hệ thống nội bộ.  
- **Default role** `USER` đảm bảo user mới có thể truy cập những API cơ bản.

---

## 📌 Điều học được
- Cách **Onboarding** user SSO (Google) vào DB cục bộ.  
- Feign client với Google API (query params, snake_case mapping).  
- Lưu ý về **transient entity** trong ManyToMany (nên fetch role từ DB).

---

## 🗺️ Hướng phát triển tiếp theo
- Link user Google ↔ user nội bộ bằng **Google ID** để chống đổi email.  
- Đồng bộ **avatar** & **tên** định kỳ (nếu cần).  
- Phát hành **JWT nội bộ** sau khi onboard (không dùng token Google cho API nội bộ).  
- Thêm **ràng buộc unique** cho `username = email` (đã có ở buổi 25).

