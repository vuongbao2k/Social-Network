# Buổi 13 — Custom Annotation Validator cho Date of Birth (DOB)

**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- Tạo **annotation tùy chỉnh** `@DobConstraint` để validate tuổi người dùng.  
- Viết **DobValidator** kiểm tra số tuổi tối thiểu.  
- Thêm **ErrorCode** mới cho DOB không hợp lệ.  
- Áp dụng validator vào **UserCreationRequest** và **UserUpdateRequest**.  
- Cập nhật lại logic **createUser**: gán role mặc định là `USER`.

---

## 🛠 Công cụ & môi trường
- Spring Boot Validation (`jakarta.validation`)  
- Lombok, JPA, Spring Security  
- MapStruct  

---

## ⚙️ 1) Tạo Annotation `DobConstraint`
```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {DobValidator.class})
public @interface DobConstraint {

    String message() default "{Invalid date of birth}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int min() default 0; // tuổi tối thiểu
}
```

---

## ⚙️ 2) Tạo Validator `DobValidator`
```java
public class DobValidator implements ConstraintValidator<DobConstraint, LocalDate> {

    private int min;

    @Override
    public void initialize(DobConstraint constraintAnnotation) {
        this.min = constraintAnnotation.min();
    }

    @Override
    public boolean isValid(LocalDate localDate, ConstraintValidatorContext context) {
        if (localDate == null) {
            return true; // null được coi là hợp lệ
        }
        return ChronoUnit.YEARS.between(localDate, LocalDate.now()) >= min;
    }
}
```

---

## ⚙️ 3) Thêm ErrorCode mới
```java
DOB_INVALID(1007, "Date of birth is invalid", HttpStatus.BAD_REQUEST),
```

---

## ⚙️ 4) Áp dụng Annotation trong DTO
### UserCreationRequest
```java
public class UserCreationRequest {
    @Size(min = 5, message = "USERNAME_INVALID")
    String username;

    @Size(min = 5, message = "PASSWORD_INVALID")
    String password;

    String firstName;
    String lastName;

    @DobConstraint(min = 18, message = "DOB_INVALID")
    LocalDate dateOfBirth;
}
```

### UserUpdateRequest
```java
public class UserUpdateRequest {
    @Size(min = 5, message = "PASSWORD_INVALID")
    String password;

    String firstName;
    String lastName;

    @DobConstraint(min = 18, message = "DOB_INVALID")
    LocalDate dateOfBirth;

    List<String> roles;
}
```

---

## ⚙️ 5) Cập nhật `createUser` để gán role mặc định
```java
public UserResponse createUser(UserCreationRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new AppException(ErrorCode.USER_EXISTED);
    }

    User user = userMapper.toUser(request);
    user.setPassword(passwordEncoder.encode(request.getPassword()));

    var roles = new HashSet<Role>();
    roleRepository.findById(PredefinedRole.USER_ROLE)
            .ifPresent(roles::add);

    user.setRoles(roles);

    return userMapper.toUserResponse(userRepository.save(user));
}
```

---

## 📌 Điều học được
- Có thể **tạo annotation custom** trong Spring Validation tương tự annotation built-in (`@Size`, `@NotNull`).  
- `ConstraintValidator` cho phép ta **tùy chỉnh logic** kiểm tra (ở đây là tuổi tối thiểu).  
- ErrorCode nên được chuẩn hoá để dễ mapping với exception handler.  
- Khi khởi tạo user, việc **gán role mặc định USER** giúp đảm bảo mọi user đều có ít nhất một quyền.  

---

## 🗺️ Hướng phát triển tiếp theo
- Viết thêm validator cho **username/email format**.  
- Thêm logic **unique constraint** validator cho username/email.  
- Gán **default permission** cho role `USER` trong ApplicationInit.  