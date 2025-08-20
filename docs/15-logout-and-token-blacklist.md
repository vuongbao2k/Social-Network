# Buổi 15 — Thêm Logout & Token Blacklist (Invalidated Tokens)

**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- Bổ sung `jti` (JWT ID) vào token để phân biệt từng token.  
- Triển khai **logout** bằng cách lưu token bị thu hồi vào bảng `InvalidatedToken`.  
- Dùng **custom JwtDecoder** để kiểm tra token qua introspect (bao gồm check blacklist).  
- Hoàn thiện luồng **logout → token invalid → introspect → security filter**.  

---

## 🛠 Công cụ & môi trường
- Spring Boot Security + OAuth2 Resource Server  
- JWT (Nimbus JOSE + JWT)  
- Spring Data JPA  

---

## ⚙️ 1) Thêm JWT ID khi phát token
```java
private String generateToken(User user) {
    JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);
    JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
            .subject(user.getUsername())
            .issuer("jb.com")
            .issueTime(new Date())
            .expirationTime(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()))
            .jwtID(UUID.randomUUID().toString()) // 🔑 thêm jti
            .claim("scope", buildScope(user))
            .build();
    ...
}
```

---

## ⚙️ 2) Entity & Repository cho InvalidatedToken
```java
@Entity
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvalidatedToken {
    @Id
    String id;       // chính là jti
    Date expiryTime;
}

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {
}
```

---

## ⚙️ 3) Logout Request DTO
```java
@Data @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogoutRequest {
    String token;
}
```

---

## ⚙️ 4) Verify Token trong AuthenticationService
```java
public SignedJWT verifyToken(String token) throws JOSEException, ParseException {
    JWSVerifier jwsVerifier = new MACVerifier(SIGNER_KEY.getBytes());
    SignedJWT signedJWT = SignedJWT.parse(token);
    String tokenId = signedJWT.getJWTClaimsSet().getJWTID();
    Date expirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();

    boolean valid = signedJWT.verify(jwsVerifier) && expirationTime.after(new Date());
    if (!valid) throw new AppException(ErrorCode.UNAUTHENTICATED);

    // 🔥 Check blacklist
    if (invalidatedTokenRepository.existsById(tokenId)) {
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    return signedJWT;
}
```

---

## ⚙️ 5) Cập nhật Introspect (dùng verifyToken mới)
```java
public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
    boolean isValid = true;
    try {
        verifyToken(request.getToken());
    } catch (AppException e) {
        isValid = false;
    }

    return IntrospectResponse.builder()
            .valid(isValid)
            .build();
}
```

---

## ⚙️ 6) Thêm logout logic
```java
public void logout(LogoutRequest request) throws ParseException, JOSEException {
    var signedToken = verifyToken(request.getToken());
    String tokenId = signedToken.getJWTClaimsSet().getJWTID();
    Date expirationTime = signedToken.getJWTClaimsSet().getExpirationTime();

    InvalidatedToken invalidatedToken = InvalidatedToken.builder()
            .id(tokenId)
            .expiryTime(expirationTime)
            .build();

    invalidatedTokenRepository.save(invalidatedToken);
}
```

---

## ⚙️ 7) Endpoint Logout
```java
@PostMapping("/logout")
public ApiResponse<Void> logout(@RequestBody LogoutRequest request) throws ParseException, JOSEException {
    authenticationService.logout(request);
    return ApiResponse.<Void>builder().build();
}
```

Và thêm `/auth/logout` vào `PUBLIC_ENDPOINTS` trong `SecurityConfig`.

---

## ⚙️ 8) Custom JwtDecoder (dùng introspect của mình)
```java
@Component
public class CustomJwtDecoder implements JwtDecoder {
    @Value("${jwt.signerKey}")
    private String SIGNER_KEY;

    @Autowired
    private AuthenticationService authenticationService;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            var response = authenticationService.introspect(IntrospectRequest.builder()
                    .token(token)
                    .build());
            if (!response.isValid()) {
                throw new JwtException("Invalid JWT token");
            }
        } catch (Exception e) {
            throw new JwtException("Invalid JWT token", e);
        }

        SecretKeySpec secretKeySpec = new SecretKeySpec(SIGNER_KEY.getBytes(), "HS512");
        var nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
        return nimbusJwtDecoder.decode(token);
    }
}
```

---

## ⚙️ 9) Áp dụng CustomJwtDecoder trong SecurityConfig
```java
@Autowired
private CustomJwtDecoder customJwtDecoder;

@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> 
            jwt.decoder(customJwtDecoder)
               .jwtAuthenticationConverter(jwtAuthenticationConverter())
        ).authenticationEntryPoint(new JwtAuthenticationEntryPoint()));

    return http.build();
}

// ❌ Xoá bean jwtDecoder cũ vì đã custom
```

---

## 📌 Điều học được
- **JWT ID (`jti`)** giúp phân biệt token, cần thiết cho cơ chế blacklist.  
- **InvalidatedToken** lưu token đã bị thu hồi, đảm bảo bảo mật khi logout.  
- **Custom JwtDecoder** cho phép lồng thêm business logic (introspect, blacklist) trước khi decode JWT.  
- Tích hợp **logout → introspect → filter** tạo nên luồng xác thực chặt chẽ và mở rộng dễ dàng.  

---

## 🗺️ Hướng phát triển tiếp theo
- Dọn dẹp token trong bảng `InvalidatedToken` sau khi hết hạn.  
- Thêm refresh token flow để cấp lại token mới sau khi access token hết hạn.  