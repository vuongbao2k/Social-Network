# Buổi 12 — Hoàn thiện Role/Permission trong User (Update, Scope Token, Seeder)
**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- **Hiển thị Role trong UserResponse** bằng `Set<Role>` thay vì `Set<String>`.
- **Cập nhật User** (update request) có thể thay đổi **password** và **roles**.
- Hoàn thiện **JWT scope**: phân biệt **ROLE_*** và **permission*** rõ ràng.
- Cập nhật **JwtAuthenticationConverter** bỏ prefix mặc định để dễ phân biệt.
- Thêm **PredefinedRole constant class** để quản lý role mặc định.
- Cập nhật **ApplicationInitConfig** để seed `ADMIN` & `USER` role + admin user.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.4, Java 21  
- Spring Security (OAuth2 Resource Server + JWT)  
- JPA/Hibernate  
- Lombok, MapStruct  

---

## ⚙️ 1) UserResponse hiển thị Role
```java
public class UserResponse {
    String id;
    String username;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
    Set<Role> roles;   // đổi từ Set<String> → Set<Role>
}
```

---

## ⚙️ 2) UserUpdateRequest thêm field roles
```java
public class UserUpdateRequest {
    String password;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
    List<String> roles; // danh sách roleId
}
```

---

## ⚙️ 3) Update user service  
Cập nhật password (nếu có) + roles.
```java
public UserResponse updateUser(String id, UserUpdateRequest userUpdateRequest) {
    User user = userRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    userMapper.updateUser(user, userUpdateRequest);

    if (userUpdateRequest.getPassword() != null) {
        user.setPassword(passwordEncoder.encode(userUpdateRequest.getPassword()));
    }

    var roles = roleRepository.findAllById(userUpdateRequest.getRoles());
    user.setRoles(new HashSet<>(roles));

    return userMapper.toUserResponse(userRepository.save(user));
}
```

### Mapper cho update user
```java
@Mapping(target = "roles", ignore = true) // xử lý roles thủ công
void updateUser(@MappingTarget User user, UserUpdateRequest request);
```

---

## ⚙️ 4) Cập nhật buildScope cho JWT
Thêm prefix `ROLE_` cho role, còn permission giữ nguyên.
```java
private String buildScope(User user) {
    StringJoiner joiner = new StringJoiner(" ");
    if (user.getRoles() != null) {
        user.getRoles().forEach(role -> {
            joiner.add("ROLE_" + role.getName());
            if (role.getPermissions() != null) {
                role.getPermissions().forEach(permission -> {
                    joiner.add(permission.getName());
                });
            }
        });
    }
    return joiner.toString();
}
```

---

## ⚙️ 5) Cập nhật JwtAuthenticationConverter  
Bỏ prefix mặc định `"SCOPE_"` để ta dễ phân biệt.
```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    jwtGrantedAuthoritiesConverter.setAuthorityPrefix(""); // không còn "SCOPE_"

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
    return converter;
}
```

> Giải thích:  
> - Khi check role → `hasRole("ADMIN")` (Spring sẽ tự thêm `"ROLE_"`).  
> - Khi check permission → `hasAuthority("USER_READ")` (giữ nguyên string permission).

---

## ⚙️ 6) PredefinedRole constant
```java
package com.jb.identity_service.constant;

public class PredefinedRole {
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String USER_ROLE = "USER";

    private PredefinedRole() {} // tránh new
}
```

---

## ⚙️ 7) Cập nhật ApplicationInitConfig (seed role + admin user)
```java
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {
    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;

    @NonFinal
    static final String ADMIN_USER_NAME = "admin";

    @NonFinal
    static final String ADMIN_PASSWORD = "admin";

    @Bean
    ApplicationRunner applicationRunner() {
        return args -> {
            if (!userRepository.existsByUsername(ADMIN_USER_NAME)) {
                // Seed USER role
                roleRepository.save(Role.builder()
                        .name(PredefinedRole.USER_ROLE)
                        .description("Default user role with basic access")
                        .build());

                // Seed ADMIN role
                var adminRole = roleRepository.save(Role.builder()
                        .name(PredefinedRole.ADMIN_ROLE)
                        .description("Administrator role with full access")
                        .build());

                var roles = new HashSet<Role>();
                roles.add(adminRole);

                User user = User.builder()
                        .username(ADMIN_USER_NAME)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
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

## 📌 Điều học được
- **UserResponse** nên trả về `Role` entity (hoặc DTO tuỳ nhu cầu), không chỉ String.
- **UpdateUser** cần xử lý riêng roles và password để đảm bảo đúng logic bảo mật.
- **JWT scope** khi chuẩn hoá prefix giúp phân biệt rõ **Role** (`ROLE_*`) và **Permission**.
- **JwtAuthenticationConverter**: bỏ prefix mặc định giúp mapping trực tiếp.
- **Seeder**: nên tạo role trước, sau đó gán vào user để tránh lỗi missing role.

---

## 🗺️ Hướng phát triển tiếp theo
- Bổ sung hiển thị `Permission` trong `UserResponse`.
- Tích hợp **Method Security** để check theo cả `role` và `permission`.
- Seed mặc định **permissions** cho từng role trong `ApplicationInitConfig`.

