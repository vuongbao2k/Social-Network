# Buổi 9 — Bật @EnableMethodSecurity, phân quyền bằng @PreAuthorize/@PostAuthorize
**Ngày:** 2025-08-18 (UTC+7)

## 🎯 Mục tiêu
- Bật **Method-level security** bằng `@EnableMethodSecurity`.
- **Xoá rule hasRole ở HttpSecurity** (request matcher) và **chuyển sang annotation** trên method dịch vụ.
- Dùng:
  - `@PreAuthorize("hasRole('ADMIN')")` cho **getAllUsers**.
  - `@PostAuthorize("returnObject.username == authentication.name or hasRole('ADMIN')")` cho **getUserById**.
- Thêm endpoint **/users/my-info** để lấy profile của chính người dùng đăng nhập.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.4, Java 21  
- Spring Security (OAuth2 Resource Server) + JWT (HS512, signerKey từ `application.yaml`)  
- Lombok, MapStruct, JPA/Hibernate  
- Context path: **`/identity`**

---

## ⚙️ 1) Cập nhật SecurityConfig: bật Method Security & bỏ rule hasRole ở matcher
- Giữ nguyên `permitAll` cho các endpoint public (đăng ký, login, introspect).
- Bỏ dòng `.requestMatchers(HttpMethod.GET, "/users").hasRole(...)`.
- **Thêm `@EnableMethodSecurity`** để kích hoạt `@PreAuthorize/@PostAuthorize`.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // ✅ Bật kiểm soát phân quyền ở tầng method
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
                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                // ❌ Bỏ rule hasRole/hasAuthority ở đây để chuyển sang @PreAuthorize/@PostAuthorize
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
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

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();
        delegate.setAuthorityPrefix("ROLE_"); // từ Buổi 8: map scope -> ROLE_*
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(delegate);
        return converter;
    }
}
```

---

## 🧩 2) Áp dụng @PreAuthorize/@PostAuthorize trong Service
> Chúng ta phân quyền **ngay tại Service** để bảo vệ logic nghiệp vụ (dù Controller thay đổi thì rule vẫn áp).  
> Yêu cầu: class Service phải là bean Spring (`@Service`), đã bật `@EnableMethodSecurity` như trên.

```java
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    UserMapper userMapper;

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toUserResponse)
                .toList();
    }

    @PostAuthorize("returnObject.username == authentication.name or hasRole('ADMIN')")
    public UserResponse getUserById(String id) {
        return userMapper.toUserResponse(
                userRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND))
        );
    }

    public UserResponse getMyInfo() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    // các hàm create/update/delete giữ nguyên như buổi trước
    public UserResponse updateUser(String id, UserUpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        userMapper.updateUser(user, req);
        return userMapper.toUserResponse(userRepository.save(user));
    }
}
```

### Giải thích ngắn:
- `@PreAuthorize("hasRole('ADMIN')")`: kiểm tra **trước khi** thực thi method. Chỉ **ADMIN** mới gọi được `getAllUsers`.
- `@PostAuthorize("returnObject.username == authentication.name or hasRole('ADMIN')")`: kiểm tra **sau khi** method chạy xong, dựa trên **giá trị trả về**:
  - Nếu **chính chủ** (username của user trả về bằng tên đăng nhập hiện tại) → OK.
  - Hoặc nếu là **ADMIN** → OK.
  - Ngược lại → **403 Forbidden**.

---

## 🌐 3) Controller: thêm `/users/my-info`
> Controller có thể giữ nguyên; ta chỉ **bổ sung** endpoint lấy thông tin người dùng hiện tại.

```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/users")
public class UserController {

    UserService userService;

    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.<List<UserResponse>>builder()
                .result(userService.getAllUsers())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable String id) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUserById(id))
                .build();
    }

    @GetMapping("/my-info")
    public ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfo())
                .build();
    }

    // create/update/delete như các buổi trước...
}
```

---

## 🧪 4) Test nhanh với curl

### (A) Lấy token
- **ADMIN**:
```bash
curl -X POST http://localhost:8080/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# => result.token = <JWT_ADMIN> (scope chứa ADMIN)
```
- **USER**:
```bash
curl -X POST http://localhost:8080/identity/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","password":"secretpass"}'
# => result.token = <JWT_USER> (scope chứa USER)
```

### (B) Gọi GET /users (get all) — yêu cầu ADMIN
```bash
# USER hoặc không có token -> 403/401
curl http://localhost:8080/identity/users \
  -H "Authorization: Bearer <JWT_USER>"

# ADMIN -> 200 OK
curl http://localhost:8080/identity/users \
  -H "Authorization: Bearer <JWT_ADMIN>"
```

### (C) Gọi GET /users/{id} — PostAuthorize
- **Chính chủ** (username của user trả về == authentication.name) → OK.  
- **ADMIN** → OK.  
- Người khác (không admin) → 403.

```bash
curl http://localhost:8080/identity/users/<USER_ID> \
  -H "Authorization: Bearer <JWT_USER_OR_ADMIN>"
```

### (D) Gọi GET /users/my-info — ai đã login cũng gọi được
```bash
curl http://localhost:8080/identity/users/my-info \
  -H "Authorization: Bearer <JWT_ANY_VALID>"
```

---

## 📌 Điều học được
- Bật **method security** với `@EnableMethodSecurity` và di chuyển phân quyền vào **tầng Service**.
- Phân quyền linh hoạt bằng **SpEL**:
  - `@PreAuthorize` (trước khi chạy method)
  - `@PostAuthorize` (dựa trên **kết quả trả về**).
- Cách viết endpoint **/users/my-info** dựa trên `SecurityContext`.

---

## ⚠️ Ghi chú
- Khi dùng `@PostAuthorize`, method phải **trả về object** (không phải `void`) để có `returnObject`.
- Đảm bảo **prefix ROLE_** đang bật trong `JwtGrantedAuthoritiesConverter` để dùng `hasRole('...')`.
- Nếu Service gọi lẫn nhau trong cùng class, annotation có thể **không kích hoạt** do proxy — cân nhắc tách method hoặc bật `proxyTargetClass`.