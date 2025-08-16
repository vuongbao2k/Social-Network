# Buổi 6 – Cấp JWT Access Token & Introspect Token
**Ngày:** 2025-08-16 (UTC+7)

## 🎯 Mục tiêu
- Bổ sung dependency **nimbus-jose-jwt** để ký/kiểm tra JWT.
- Cấp **access token** khi đăng nhập thành công.
- Tạo endpoint **/auth/introspect** để kiểm tra token hợp lệ.
- Ẩn **signerKey** trong `application.yaml`.

---

## 🛠 Công cụ & môi trường
- Java 21, Spring Boot 3.5.4
- Spring Security Crypto (BCrypt) – *đã thêm ở buổi 5*
- Nimbus JOSE + JWT
- Lombok, MapStruct
- MySQL

---

## ⚙️ 1) Thêm dependency JWT
**`pom.xml`**
```xml
<dependency>
    <groupId>com.nimbusds</groupId>
    <artifactId>nimbus-jose-jwt</artifactId>
    <version>9.30.1</version>
</dependency>
```

---

## 🔐 2) Cấu hình signerKey
**`application.yaml`**
```yaml
jwt:
  signerKey: XRHPndz7zeYl/OwDx3dXRmWJ6Xj199B//7vb9TtiIDJ2fGl4tkXbgbUnNrtZp2XG
```
> Lưu ý: Key phải đủ độ dài (HS512 cần key dài). **Không commit key thật** trong production; dùng biến môi trường/secrets.

---

## 🧾 3) Cập nhật DTO Authentication
**`AuthenticationResponse.java`**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    String token;
    boolean authenticated;
}
```

**`AuthenticationRequest.java`** (giữ nguyên như buổi 5)

**Introspect DTO**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IntrospectRequest {
    String token;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IntrospectResponse {
    boolean valid;
}
```

---

## 🧩 4) Bổ sung `ErrorCode` (nếu chưa có)
**`ErrorCode.java`** – thêm:
```java
USER_NOT_FOUND(1005, "User not found"),
UNAUTHENTICATED(1006, "Unauthenticated"),
```
> Vì service dùng `USER_NOT_FOUND` và `UNAUTHENTICATED`, cần hiện diện trong enum.

---

## 🔑 5) AuthenticationService – generate & verify JWT
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class AuthenticationService {
    UserRepository userRepository;

    @NonFinal
    @Value("${jwt.signerKey}")
    private String SIGNER_KEY;

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();
        JWSVerifier jwsVerifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);
        Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        boolean valid = signedJWT.verify(jwsVerifier);

        return IntrospectResponse.builder()
                .valid(valid && expirationTime.after(new Date()))
                .build();
    }

    public AuthenticationResponse isAuthenticated(AuthenticationRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String token = generateToken(user.getUsername());
        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    private String generateToken(String username) {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);
        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(username)
                .issuer("jb.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()))
                .claim("roles", "USER")
                .build();

        JWSObject jwsObject = new JWSObject(jwsHeader, new Payload(jwtClaimsSet.toJSONObject()));
        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
```

---

## 🌐 6) AuthenticationController – `/token` & `/introspect`
> Lưu ý: bạn đang dùng **context-path = `/identity`** ⇒ Base URL là `http://localhost:8080/identity`

```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    AuthenticationService authenticationService;

    @PostMapping("/token")
    ApiResponse<AuthenticationResponse> login(@RequestBody AuthenticationRequest request) {
        return ApiResponse.<AuthenticationResponse>builder()
                .result(authenticationService.isAuthenticated(request))
                .build();
    }

    @PostMapping("/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request)
            throws ParseException, JOSEException {
        return ApiResponse.<IntrospectResponse>builder()
                .result(authenticationService.introspect(request))
                .build();
    }
}
```

---

## 🧪 7) Test nhanh với curl

### (1) Lấy token
```bash
curl -X POST http://localhost:8080/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "secretpass"
  }'
# Response:
# {
#   "code": 1000,
#   "result": {
#     "token": "<JWT>",
#     "authenticated": true
#   }
# }
```

### (2) Introspect token
```bash
curl -X POST http://localhost:8080/identity/auth/introspect \
  -H "Content-Type: application/json" \
  -d '{
    "token": "<JWT>"
  }'
# Response:
# {
#   "code": 1000,
#   "result": { "valid": true }
# }
```

### (3) Sai mật khẩu / user không tồn tại
- Sai pass → `code = 1006 (UNAUTHENTICATED)`
- Không tìm thấy user → `code = 1005 (USER_NOT_FOUND)`

---

## 🧠 Lưu ý bảo mật
- **SignerKey**: coi như mật khẩu hệ thống; nếu bị lộ có thể **giả mạo token**. Đưa vào **env/secret manager** khi deploy.
- **Thời hạn token** (`exp`) đang set **1 giờ** – tuỳ nhu cầu có thể rút ngắn hoặc thêm **refresh token**.
- `roles` đang hardcode `"USER"` → sau này lấy từ DB/authority thật.

---

## 📌 Điều học được
- Cách ký/verify JWT với Nimbus JOSE JWT (HS512).
- Quy trình login → cấp token → introspect để kiểm tra hợp lệ.
- Ẩn signerKey trong cấu hình runtime.
- Bổ sung `ErrorCode` để phản hồi xác thực nhất quán.