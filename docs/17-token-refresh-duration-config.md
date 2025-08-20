# Buổi 17 — Thêm thời gian hiệu lực Token & Refresh Token từ `application.yaml`

**Ngày:** 2025-08-20 (UTC+7)

---

## 🎯 Mục tiêu
- **Tách biệt thời gian hiệu lực** giữa **Access Token** và **Refresh Token**.  
- Cấu hình thời gian trong **application.yaml** thay vì hardcode (dễ chỉnh).  
- Cập nhật logic:
  - `generateToken` → dùng `jwt.valid-duration` cho Access Token.  
  - `verifyToken(token, isRefresh)` → nếu `isRefresh = true` thì tính expiry theo `jwt.refresh-valid-duration`.  
  - Điều chỉnh những nơi gọi `verifyToken` (`introspect`, `refreshToken`, `logout`).  

---

## 🛠 Cấu hình `application.yaml`
```yaml
jwt:
  signerKey: XRHPndz7zeYl/OwDx3dXRmWJ6Xj199B//7vb9TtiIDJ2fGl4tkXbgbUnNrtZp2XG
  valid-duration: 3600         # 1 giờ (Access Token)
  refresh-valid-duration: 360000 # 100 giờ (Refresh Token)
```

---

## ⚙️ 1) Inject config vào Service
```java
@NonFinal
@Value("${jwt.valid-duration}")
private long VALID_DURATION;

@NonFinal
@Value("${jwt.refresh-valid-duration}")
private long REFRESH_VALID_DURATION;
```

---

## ⚙️ 2) Generate Token — theo `VALID_DURATION`
```java
private String generateToken(User user) {
    JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);
    JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
            .subject(user.getUsername())
            .issuer("jb.com")
            .issueTime(new Date())
            .expirationTime(new Date(Instant.now()
                .plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
            .jwtID(UUID.randomUUID().toString())
            .claim("scope", buildScope(user))
            .build();

    SignedJWT signedJWT = new SignedJWT(jwsHeader, jwtClaimsSet);
    // ... phần sign và serialize giữ nguyên
}
```

---

## ⚙️ 3) Verify Token — phân biệt Access vs Refresh
```java
public SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
    JWSVerifier jwsVerifier = new MACVerifier(SIGNER_KEY.getBytes());
    SignedJWT signedJWT = SignedJWT.parse(token);

    var tokenId = signedJWT.getJWTClaimsSet().getJWTID();

    // Nếu là refresh → expiry = issueTime + REFRESH_VALID_DURATION
    Date expirationTime = (isRefresh)
            ? new Date(signedJWT.getJWTClaimsSet().getIssueTime().toInstant()
                .plus(REFRESH_VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli())
            : signedJWT.getJWTClaimsSet().getExpirationTime();

    boolean valid = signedJWT.verify(jwsVerifier) && expirationTime.after(new Date());
    if (!valid) {
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
    if (invalidatedTokenRepository.existsById(tokenId)) {
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    return signedJWT;
}
```

---

## ⚙️ 4) Cập nhật các chỗ gọi `verifyToken`

### (A) **Introspect** (dùng Access Token) → `isRefresh = false`
```java
public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
    var token = request.getToken();
    boolean isValid = true;
    try {
        verifyToken(token, false); // Access Token
    } catch (AppException e) {
        isValid = false;
    }

    return IntrospectResponse.builder()
            .valid(isValid)
            .build();
}
```

---

### (B) **RefreshToken** → `isRefresh = true`
```java
public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
    SignedJWT signedToken = verifyToken(request.getToken(), true);

    // ... phần revoke cũ + generate token mới giữ nguyên
}
```

---

### (C) **Logout** → `isRefresh = true`  
> Vì logout có thể dùng Access/Refresh token, ta cho `true` và **try-catch** để luôn xử lý.
```java
public void logout(LogoutRequest request) throws ParseException, JOSEException {
    try {
        var signedToken = verifyToken(request.getToken(), true);
        String tokenId = signedToken.getJWTClaimsSet().getJWTID();
        Date expirationTime = signedToken.getJWTClaimsSet().getExpirationTime();
        InvalidatedToken invalidatedToken = InvalidatedToken.builder()
                .id(tokenId)
                .expiryTime(expirationTime)
                .build();
        invalidatedTokenRepository.save(invalidatedToken);
    } catch (AppException e) {
        // vẫn coi như logout thành công
    }
}
```

---

## 🧪 Test nhanh

### (A) Access Token hết hạn sau 3600s
```bash
curl -X POST http://localhost:8080/identity/auth/token ...
# -> token hết hạn trong 1h
```

### (B) Refresh Token còn hạn trong 100h
```bash
curl -X POST http://localhost:8080/identity/auth/refresh \
  -d '{"token":"<ACCESS_TOKEN>"}'

# -> chấp nhận trong 100h kể từ issueTime, kể cả khi access token exp = 1h
```

---

## 📌 Điều học được
- Tách biệt thời gian hiệu lực Access Token & Refresh Token ngay từ config.  
- Có thể **xoay vòng (rotate) token** an toàn, nhờ phân biệt khi verify.  
- Cơ chế Refresh giờ **không phụ thuộc vào access token exp** mà dùng `refresh-valid-duration`.

---

## 🗺️ Hướng phát triển tiếp theo
- Bổ sung **Refresh Token entity** riêng (thay vì dùng access token để refresh).  
- Thêm **scheduler dọn dẹp token expired** trong bảng `InvalidatedToken`.  
- Kết hợp **multi-device** (mỗi thiết bị 1 refresh token độc lập).

