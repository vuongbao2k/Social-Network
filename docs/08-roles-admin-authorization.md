# Buổi 8 — Thêm Roles & Phân quyền ADMIN cho GET /users
**Ngày:** 2025-08-18 (UTC+7)

## 🎯 Mục tiêu
- Thêm field **roles** cho `User` và cho `UserResponse`; **loại bỏ `password` khỏi response**.
- Khi đăng ký, **gán mặc định ROLE_USER**; khi khởi động app, **seed admin mặc định** nếu chưa có.
- Đưa **roles vào JWT** (claim `scope`) và **map thành Spring Security authorities**.
- Chỉ cho phép **ADMIN** gọi `GET /users` (get all).

---

## 🛠 Công cụ & môi trường
- Java 21, Spring Boot 3.5.4  
- Spring Security (OAuth2 Resource Server), BCrypt PasswordEncoder  
- Lombok, MapStruct, JPA/Hibernate (`ddl-auto=update`)  
- MySQL  
- Context path: **`/identity`**

> 🔧 Sau khi thay đổi DTO/Entity, **Clean/Rebuild** để MapStruct/Lombok generate lại code.

---

## 1) Sửa Entity & Response (thêm roles, ẩn password)
**`User.java`** — thêm `roles` là `Set<String>`; dùng `@ElementCollection` để JPA lưu bảng phụ.
```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String username;
    String password;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;

    @ElementCollection(fetch = FetchType.EAGER) //nó tự thêm, chưa tìm hiểu
    Set<String> roles;
}
```

**`UserResponse.java`** — **không** chứa `password`.
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String username;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
    Set<String> roles;
}
```

---

## 2) Enum Role
**`com.jb.identity_service.enums.Role`**
```java
package com.jb.identity_service.enums;

public enum Role {
    ADMIN,
    USER
}
```

---

## 3) Đăng ký `PasswordEncoder` thành bean (tái sử dụng)
```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

---

## 4) Cấp ROLE_USER khi tạo user mới
**`UserService#createUser`** — encode password + gán mặc định `ROLE_USER`.
```java
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    public UserResponse createUser(UserCreationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        var roles = new HashSet<String>();
        roles.add(Role.USER.name());
        user.setRoles(roles);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    // ... các method khác giữ nguyên (getAllUsers, getUserById, updateUser, deleteUser)
}
```

---

## 5) Seed admin mặc định khi khởi động app
**`ApplicationInitConfig.java`**
```java
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner applicationRunner() {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                var roles = Set.of(Role.ADMIN.name());
                User user = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin"))
                        .firstName("Admin")
                        .dateOfBirth(LocalDate.of(1990, 1, 1))
                        .roles(roles)
                        .build();

                userRepository.save(user);
                log.info("Admin user created with username: {}", user.getUsername());
            }
        };
    }
}
```

---

## 6) JWT: đưa roles vào claim `scope`
Đổi `generateToken(String username)` ➜ `generateToken(User user)` để lấy roles; thêm `buildScope`.

```java
private String generateToken(User user) {
    JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);
    JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
            .subject(user.getUsername())
            .issuer("jb.com")
            .issueTime(new Date())
            .expirationTime(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()))
            .claim("scope", buildScope(user))  // Ví dụ: "ADMIN USER"
            .build();
    JWSObject jwsObject = new JWSObject(jwsHeader, new Payload(jwtClaimsSet.toJSONObject()));
    try {
        jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
        return jwsObject.serialize();
    } catch (JOSEException e) {
        throw new RuntimeException(e);
    }
}

private String buildScope(User user) {
    StringJoiner joiner = new StringJoiner(" ");
    if (user.getRoles() != null) {
        user.getRoles().forEach(joiner::add);
    }
    return joiner.toString();
}
```

Trong `isAuthenticated`, sau khi xác thực password, gọi:  
```java
String token = generateToken(user);
```

> Mặc định Spring Security map `scope` thành authorities `SCOPE_<ROLE>` (ví dụ: `SCOPE_ADMIN`, `SCOPE_USER`).

---

## 7) Phân quyền: chỉ ADMIN được `GET /users`
Mặc định authorities là `SCOPE_*`. Ta **đổi prefix** sang `ROLE_` để dùng `hasRole("ADMIN")`.

**`SecurityConfig` — bổ sung converter + rule:**
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authz -> authz
            .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
            .requestMatchers(HttpMethod.GET, "/users").hasRole(Role.ADMIN.name()) // chỉ ADMIN được GET /users
            .anyRequest().authenticated()
        );

    http.oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt
            .decoder(jwtDecoder())
            .jwtAuthenticationConverter(jwtAuthenticationConverter())
        )
    );

    return http.build();
}

@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();
    delegate.setAuthorityPrefix("ROLE_"); // đổi từ SCOPE_ sang ROLE_
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(delegate);
    return converter;
}
```

---

## 8) Log authorities để kiểm tra
**`UserController#getAllUsers`**
```java
@GetMapping
public ApiResponse<List<UserResponse>> getAllUsers() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    authentication.getAuthorities().forEach(authority ->
        log.info("User {} has authority: {}", authentication.getName(), authority.getAuthority())
    );
    return ApiResponse.<List<UserResponse>>builder()
            .result(userService.getAllUsers())
            .build();
}
```
> Trước khi đổi prefix sẽ log `SCOPE_ADMIN`; sau khi đổi prefix sẽ là `ROLE_ADMIN`.

---

## 🧪 9) Test nhanh với curl

### (1) Lấy token ADMIN
```bash
curl -X POST http://localhost:8080/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# => result.token = <JWT_ADMIN>
```

### (2) Lấy token USER thường
```bash
curl -X POST http://localhost:8080/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","password":"secretpass"}'
# => result.token = <JWT_USER> (scope: "USER")
```

### (3) Gọi GET /users
```bash
# Không kèm token -> 401
curl http://localhost:8080/identity/users

# Kèm token USER -> 403 (Forbidden)
curl http://localhost:8080/identity/users -H "Authorization: Bearer <JWT_USER>"

# Kèm token ADMIN -> 200 (OK)
curl http://localhost:8080/identity/users -H "Authorization: Bearer <JWT_ADMIN>"
```

---

## 🧩 Giải thích logic ngắn gọn
- **Service#createUser**: kiểm tra trùng username → encode password → gán `ROLE_USER` → lưu DB → trả `UserResponse` (không chứa password).
- **JWT**: claim `scope` chứa danh sách role phân tách bởi khoảng trắng; Spring tự map thành authorities.
- **Security**: đổi `authorityPrefix` thành `ROLE_` để dùng `hasRole("ADMIN")` trực tiếp cho `GET /users`.
- **Init**: seed tài khoản `admin/admin` có **ROLE_ADMIN** nếu chưa tồn tại.

---

## 📌 Điều học được
- Model roles đơn giản với `@ElementCollection Set<String>`.
- Đóng gói roles vào JWT và map sang authorities trong Spring Security.
- Tùy biến `JwtAuthenticationConverter` để dùng `hasRole`.
- Seed dữ liệu khởi tạo bằng `ApplicationRunner`.

---

## ⚠️ Ghi chú
- Với `@ElementCollection`, JPA tạo bảng phụ (ví dụ `user_roles`) — cần `ddl-auto=update` hoặc migration.
- Không bao giờ trả `password` về client; chỉ trả `UserResponse` không có password.
- Trong thực tế, cân nhắc **Role/Permission** là entity riêng (N-N) để quản trị linh hoạt.
