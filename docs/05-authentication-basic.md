# Buổi 5 – Thêm Authentication cơ bản & Mã hoá mật khẩu
**Ngày:** 2025-08-16 (UTC+7)

## 🎯 Mục tiêu
- Thêm dependency `spring-security-crypto` để mã hoá mật khẩu.
- Mã hoá mật khẩu khi tạo user bằng `BCryptPasswordEncoder`.
- Tạo **API đăng nhập cơ bản** (username + password).
- Xây dựng AuthenticationService và AuthenticationController.
- Chuẩn hoá `ApiResponse` để giữ `code = 1000` mặc định.

---

## 🛠 Công cụ & môi trường
- Java 21
- Spring Boot 3.5.4
- Maven
- Lombok
- MapStruct
- Spring Security Crypto (chỉ dùng BCryptPasswordEncoder)

---

## 🚀 Các bước thực hiện

### 1) Thêm dependency `spring-security-crypto`
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

---

### 2) Encode mật khẩu khi tạo User
Trong `UserService`:
```java
public UserResponse createUser(UserCreationRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new AppException(ErrorCode.USER_EXISTED);
    }
    User user = userMapper.toUser(request);

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    user.setPassword(passwordEncoder.encode(request.getPassword()));

    return userMapper.toUserResponse(userRepository.save(user));
}
```

---

### 3) Tạo Authentication DTO
**`AuthenticationRequest.java`**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationRequest {
    String username;
    String password;
}
```

**`AuthenticationResponse.java`**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    boolean authenticated;
}
```

---

### 4) Cập nhật UserRepository
```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
}
```

---

### 5) Tạo AuthenticationService
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class AuthenticationService {
    UserRepository userRepository;

    public boolean isAuthenticated(AuthenticationRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        return passwordEncoder.matches(request.getPassword(), user.getPassword());
    }
}
```

---

### 6) Tạo AuthenticationController
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    AuthenticationService authenticationService;

    @PostMapping("/login")
    ApiResponse<AuthenticationResponse> login(@RequestBody AuthenticationRequest request) {
        return ApiResponse.<AuthenticationResponse>builder()
                .result(AuthenticationResponse.builder()
                        .authenticated(authenticationService.isAuthenticated(request))
                        .build())
                .build();
    }
}
```

---

### 7) Chuẩn hoá ApiResponse với @Builder.Default
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @Builder.Default
    int code = 1000;
    String message;
    T result;
}
```

---

## 🧠 Lưu ý
- `BCryptPasswordEncoder(10)` dùng **strength = 10** (mức phổ biến, cân bằng bảo mật và hiệu năng).
- Lúc đăng nhập, `matches()` sẽ so sánh password nhập vào với password đã mã hoá trong DB.
- Nếu quên `@Builder.Default`, giá trị `code` trong `ApiResponse` sẽ bị set = 0 thay vì 1000.
- Hiện tại API login chỉ trả về `true/false`, chưa phát sinh token (JWT sẽ làm ở bước sau).

---

## 📌 Điều học được
- Cách mã hoá password an toàn với BCrypt.
- Cách viết login API cơ bản chỉ xác thực username + password.
- Cách mở rộng repository (`findByUsername`).
- Cách giữ giá trị mặc định khi dùng Lombok builder.
