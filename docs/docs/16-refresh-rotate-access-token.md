# Buổi 16 — Refresh (Rotate) Access Token bằng jti + Blacklist

**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- Bổ sung API **/auth/refresh** để **đổi (rotate) access token**:  
  - Xác thực token hiện tại còn hợp lệ (chưa bị thu hồi, chưa hết hạn).  
  - **Thu hồi** token hiện tại (đưa `jti` vào bảng `InvalidatedToken`).  
  - **Phát hành token mới** cho cùng người dùng (mang `jti` mới).  
- Mở endpoint **/auth/refresh** là **public** (không yêu cầu token header; token sẽ nằm trong body).  
- Giữ format trả về **ApiResponse<AuthenticationResponse>** đồng nhất.

> Lưu ý: Cách làm trong buổi này là **rotate access token bằng chính access token còn hiệu lực** (không có refresh token riêng). Buổi sau sẽ nâng cấp lên **refresh token chuẩn** (dài hạn, lưu trữ an toàn, có revocation/rotation riêng).

---

## 🛠 Công cụ & môi trường
- Spring Security (OAuth2 Resource Server)  
- Nimbus JOSE + JWT (`verifyToken` đã có từ Buổi 15)  
- JPA/Hibernate (bảng `InvalidatedToken` đã có)  
- Lombok

---

## ⚙️ 1) DTO — RefreshRequest
> Token cũ (đang còn hạn và chưa bị thu hồi) sẽ được gửi ở body.
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshRequest {
    String token;
}
```

---

## ⚙️ 2) Service — `refreshToken(request)`
> Quy trình:  
> 1) `verifyToken(token)` → đảm bảo token **đúng chữ ký, chưa hết hạn, không trong blacklist**.  
> 2) Thu hồi token cũ (thêm vào **InvalidatedToken** bằng `jti`).  
> 3) Tìm user theo `sub` → **generateToken(user)** → trả token mới.

```java
public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
    SignedJWT signedToken = verifyToken(request.getToken());

    // 1) Thu hồi token cũ (đưa jti vào blacklist)
    String tokenId = signedToken.getJWTClaimsSet().getJWTID();
    Date expirationTime = signedToken.getJWTClaimsSet().getExpirationTime();
    InvalidatedToken invalidatedToken = InvalidatedToken.builder()
            .id(tokenId)
            .expiryTime(expirationTime)
            .build();
    invalidatedTokenRepository.save(invalidatedToken);

    // 2) Phát hành token mới cho cùng user
    String username = signedToken.getJWTClaimsSet().getSubject();
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    String token = generateToken(user);

    return AuthenticationResponse.builder()
            .token(token)
            .authenticated(true)
            .build();
}
```

> **Logic an toàn**: token cũ sau khi refresh sẽ **không thể dùng lại** (vì đã bị blacklist bằng `jti`).

---

## ⚙️ 3) Controller — Endpoint `/auth/refresh`
```java
@PostMapping("/refresh")
public ApiResponse<AuthenticationResponse> login(@RequestBody RefreshRequest request)
        throws ParseException, JOSEException {
    return ApiResponse.<AuthenticationResponse>builder()
            .result(authenticationService.refreshToken(request))
            .build();
}
```

---

## ⚙️ 4) Security — mở public endpoint
> Chỉ thêm path mới vào danh sách public; các cấu hình còn lại **giữ nguyên** như buổi trước (đang dùng `CustomJwtDecoder`).

```java
private final String[] PUBLIC_ENDPOINTS = {
        "/users",
        "/auth/token",
        "/auth/introspect",
        "/auth/logout",
        "/auth/refresh" // ✅ thêm
};
```

---

## 🧪 5) Test nhanh với curl

### (A) Refresh token hợp lệ
```bash
# Giả sử ACCESS_TOKEN còn hạn và chưa logout
curl -X POST http://localhost:8080/identity/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"token":"<ACCESS_TOKEN>"}'

# => 200 OK
# {
#   "code": 1000,
#   "result": {
#     "token": "<NEW_ACCESS_TOKEN>",
#     "authenticated": true
#   }
# }
```

### (B) Dùng lại token cũ sau khi refresh → bị blacklist → 401
```bash
# Gọi API bảo vệ với token đã refresh (bị đưa vào blacklist)
curl http://localhost:8080/identity/users \
  -H "Authorization: Bearer <ACCESS_TOKEN_OLD>"

# => 401 UNAUTHENTICATED (do CustomJwtDecoder -> introspect -> verifyToken -> blacklist)
```

### (C) Refresh với token đã logout → 401
```bash
curl -X POST http://localhost:8080/identity/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"token":"<ACCESS_TOKEN_OLD_LOGGED_OUT>"}'
# => 401 UNAUTHENTICATED
```

---

## 🧩 Giải thích logic ngắn gọn
- **`verifyToken`**: đảm bảo token hợp lệ về **chữ ký**, **hạn dùng**, và **không có trong blacklist** (`InvalidatedToken`).  
- **Rotate token**: sau khi refresh, token cũ **bị vô hiệu hóa ngay** (thêm jti → bản chất là *one-time use*).  
- **CustomJwtDecoder**: mọi request vào hệ thống đều kiểm tra token qua **introspect** và **blacklist**, nên việc refresh/logout hiệu lực tức thì.

---

## 📌 Điều học được
- Cách **rotate access token** an toàn với **jti + bảng blacklist**.  
- Tránh **reuse** token cũ nhờ cơ chế **InvalidatedToken**.  
- Nối mạch logic: **/auth/logout**, **/auth/introspect**, **CustomJwtDecoder**, **/auth/refresh** hoạt động thống nhất.

---

## 🗺️ Hướng phát triển tiếp theo
- Thiết kế **Refresh Token** riêng (dài hạn, mã hoá/ghi DB, rotation, revoke độc lập).  
- Dọn dẹp bảng `InvalidatedToken` (job xóa record đã hết hạn).  
- Thêm **rate limit** cho `/auth/refresh` để hạn chế abuse.

