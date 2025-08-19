# Buổi 7 — Cấu hình Spring Security với OAuth2 Resource Server
**Ngày:** 2025-08-17 (UTC+7)

## 🎯 Mục tiêu
- Tích hợp **Spring Security – OAuth2 Resource Server** để **xác thực JWT** tự động ở filter chain.
- **Loại bỏ verify thủ công** bằng thư viện riêng; dùng `JwtDecoder` tích hợp sẵn.
- Bảo vệ API: chỉ cho phép truy cập nếu **Bearer token hợp lệ**.
- Duy trì thuật toán **HS512** và lấy `signerKey` từ cấu hình (`application.yaml`).

---

## 🛠 Công cụ & môi trường
- Java 21, Spring Boot 3.5.4  
- spring-boot-starter-oauth2-resource-server  
- spring-security-crypto (đã dùng để BCrypt ở Buổi 5)  
- Lombok, MapStruct  
- MySQL  
- Context path hiện tại: **`/identity`**

---

## ⚙️ 1) Thêm dependency / Gỡ dependency cũ
**`pom.xml`** – thêm:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```
> Gỡ bỏ `com.nimbusds:nimbus-jose-jwt` đã thêm ở Buổi 6 (starter trên đã kéo transitively qua `spring-security-oauth2-jose`).

---

## 🔐 2) Cấu hình SecurityFilterChain & JwtDecoder
**`com.jb.identity_service.config.SecurityConfig`**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.signerKey}")
    private String SIGNER_KEY;

    private final String[] PUBLIC_ENDPOINTS = {
            "/users",
            "/auth/token",
            "/auth/introspect"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                // Cho phép POST /users (đăng ký), POST /auth/token, POST /auth/introspect không cần token
                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
            );

        http.oauth2ResourceServer(oauth2 ->
            oauth2.jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder()))
        );

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(SIGNER_KEY.getBytes(), "HS512");
        return NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }
}
```

---

## 🧾 3) Cập nhật cấu hình `application.yaml` (nhắc lại)
```yaml
jwt:
  signerKey: XRHPndz7zeYl/OwDx3dXRmWJ6Xj199B//7vb9TtiIDJ2fGl4tkXbgbUnNrtZp2XG
```
> Key cần đủ dài cho HS512. Khi deploy, **không hardcode** – đưa vào biến môi trường/secret manager.

---

## 🧩 4) Giải thích logic ngắn gọn
- **OAuth2 Resource Server**: bật cơ chế xác thực JWT ở **Security Filter Chain**. Spring tự:
  - Parse token từ header `Authorization: Bearer <JWT>`,
  - Verify chữ ký bằng `JwtDecoder` (HS512 + `SIGNER_KEY`),
  - Check `exp`/`nbf`… trước khi request vào Controller.
- **Public endpoints**:
  - `.requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()`  
    → Cho phép **POST** tới `/users` (đăng ký), `/auth/token` (login) và `/auth/introspect` mà không cần token.
  - Các method khác (ví dụ **GET /users**) cần token hợp lệ.
- **JwtDecoder HS512**: phải **khớp** với thuật toán sinh token ở Buổi 6 (HS512). Sai thuật toán/khác key → 401.

---

## 🧪 5) Test nhanh với curl

### (1) Lấy token (đã làm ở Buổi 6)
```bash
curl -X POST http://localhost:8080/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","password":"secretpass"}'
# => result.token = <JWT>
```

### (2) Gọi API công khai – **POST /users** (permitAll)
```bash
curl -X POST http://localhost:8080/identity/users \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"secret","firstName":"New","lastName":"User","dateOfBirth":"2000-01-12"}'
# 200 OK (không cần token vì permitAll cho POST /users)
```

### (3) Gọi API bảo vệ – **GET /users** (cần token)
```bash
# Không có token -> 401
curl http://localhost:8080/identity/users

# Có token -> 200
curl http://localhost:8080/identity/users \
  -H "Authorization: Bearer <JWT>"
```

### (4) Introspect (tùy chọn cho dev/debug)
```bash
curl -X POST http://localhost:8080/identity/auth/introspect \
  -H "Content-Type: application/json" \
  -d '{"token":"<JWT>"}'
```

---

## ⚠️ Ghi chú
- Nếu trả về **401**: thường do token sai/mất, sai thuật toán, hoặc token hết hạn (`exp`).
- Nếu muốn mở thêm endpoint public (ví dụ **GET** `/users/{id}`), thêm rule `.requestMatchers(HttpMethod.GET, "/users/**").permitAll()`.
- Về lâu dài, có thể bỏ `/auth/introspect` vì Resource Server đã tự verify ở filter.

---

## 📌 Điều học được
- Tích hợp Spring Security Resource Server để xác thực JWT chuẩn, không cần verify thủ công.
- Cách định nghĩa public vs. protected endpoints.
- Sử dụng `NimbusJwtDecoder` với **HS512** và key từ cấu hình.
- Luồng xác thực: **Bearer token** → filter chain → Controller.