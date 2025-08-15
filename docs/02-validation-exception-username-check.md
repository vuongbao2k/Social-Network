# Buổi 2 – Thêm Validation, Exception Handler & Kiểm tra Username tồn tại
**Ngày:** 2025-08-15 (UTC+7)

## 🎯 Mục tiêu
- Thêm **validation** cho request DTO.
- Xử lý lỗi tập trung với `@ControllerAdvice`.
- Kiểm tra `username` trùng trước khi tạo user.
- Trả thông báo lỗi rõ ràng khi vi phạm điều kiện.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.4
- Java 21
- Maven
- MySQL
- Postman

---

## 🚀 Các bước thực hiện

### 1) Thêm dependency validation vào `pom.xml`
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

### 2) Tạo Global Exception Handler
**`GlobalExceptionHandler.java`**
```java
package com.jb.identity_service.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Bắt lỗi RuntimeException (business logic)
    @ExceptionHandler(value = RuntimeException.class)
    ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    // Bắt lỗi validation (@Valid)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<String> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(e.getFieldError().getDefaultMessage());
    }
}
```
- **`RuntimeException`**: lỗi logic như username đã tồn tại.  
- **`MethodArgumentNotValidException`**: lỗi validation (`@Size`, `@NotNull`...).

---

### 3) Thêm method kiểm tra tồn tại username trong Repository
**`UserRepository.java`**
```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByUsername(String username);
}
```

---

### 4) Chặn username trùng khi tạo user
**`UserService.java`**
```java
public User createUser(UserCreationRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new RuntimeException("Username already exists: " + request.getUsername());
    }

    User user = new User();
    user.setUsername(request.getUsername());
    user.setPassword(request.getPassword());
    user.setFirstName(request.getFirstName());
    user.setLastName(request.getLastName());
    user.setDateOfBirth(request.getDateOfBirth());

    return userRepository.save(user);
}
```

---

### 5) Thêm validation vào DTO
**`UserCreationRequest.java`**
```java
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class UserCreationRequest {

    @Size(min = 5, message = "Username must be at least 5 characters long.")
    private String username;

    @Size(min = 5, message = "Password must be at least 5 characters long.")
    private String password;

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;

    // getters & setters
}
```

---

### 6) Kích hoạt validation ở Controller
**`UserController.java`**
```java
@PostMapping
public User createUser(@RequestBody @Valid UserCreationRequest request) {
    return userService.createUser(request);
}
```

---

## 🧪 Lệnh test API

### 1) Chạy ứng dụng
```bash
mvn spring-boot:run
```

### 2) Tạo user thành công
```bash
curl -X POST http://localhost:8080/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john123",
    "password": "secretpass",
    "firstName": "John",
    "lastName": "Doe",
    "dateOfBirth": "2000-01-12"
  }'
```

### 3) Lỗi do username trùng
```bash
curl -X POST http://localhost:8080/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john123",
    "password": "secretpass",
    "firstName": "Jane",
    "lastName": "Smith",
    "dateOfBirth": "1999-05-10"
  }'
# Kết quả:
# HTTP 400: "Username already exists: john123"
```

### 4) Lỗi do username hoặc password < 5 ký tự
```bash
curl -X POST http://localhost:8080/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "abc",
    "password": "123",
    "firstName": "Short",
    "lastName": "Name",
    "dateOfBirth": "2000-01-12"
  }'
# Kết quả:
# HTTP 400: "Username must be at least 5 characters long."
```

---

## 🧠 Lưu ý
- `@Valid` + `spring-boot-starter-validation` giúp Spring tự động kiểm tra dữ liệu request.
- `handleValidationException` sẽ lấy **thông báo lỗi đầu tiên** từ `getFieldError().getDefaultMessage()`.
- Nếu muốn trả về **tất cả lỗi cùng lúc**, có thể duyệt `e.getBindingResult().getFieldErrors()` và ghép thành danh sách.

---

## 📌 Điều học được
- Bắt lỗi validation và business logic riêng biệt.
- `@ControllerAdvice` giúp gom tất cả xử lý lỗi về một nơi.
- Spring Boot tích hợp sẵn Hibernate Validator để dùng với `@Valid`.