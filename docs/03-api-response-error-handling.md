# Buổi 3 – Chuẩn hóa API Response & Xử lý lỗi theo ErrorCode
**Ngày:** 2025-08-15 (UTC+7)

## 🎯 Mục tiêu
- Tạo wrapper `ApiResponse<T>` chuẩn cho toàn bộ API.
- Sử dụng `ErrorCode` để quản lý mã lỗi và thông điệp thống nhất.
- Xây dựng `AppException` để ném lỗi với mã định danh.
- Nâng cấp `GlobalExceptionHandler` để trả response thống nhất.
- Sửa DTO, Service để dùng error code khi ném exception.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.4
- Java 21
- Maven
- Jackson (tự động tích hợp với Spring Boot)
- MySQL

---

## 🚀 Các bước thực hiện

### 1) Tạo `ApiResponse<T>`
**`ApiResponse.java`**
```java
package com.jb.identity_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code = 1000; // default success code
    private String message;
    private T result;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getResult() { return result; }
    public void setResult(T result) { this.result = result; }
}
```
- `@JsonInclude(JsonInclude.Include.NON_NULL)` → bỏ qua field null trong JSON.
- `code` mặc định `1000` (success).

---

### 2) Sửa Controller trả `ApiResponse`
```java
@PostMapping
public ApiResponse<User> createUser(@RequestBody @Valid UserCreationRequest request) {
    ApiResponse<User> response = new ApiResponse<>();
    response.setResult(userService.createUser(request));
    return response;
}
```

---

### 3) Tạo `ErrorCode` enum
**`ErrorCode.java`**
```java
package com.jb.identity_service.exception;

public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized exception"),
    USER_EXISTED(1002, "User already exists"),
    USERNAME_INVALID(1003, "Username is invalid"),
    PASSWORD_INVALID(1004, "Password is invalid");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
```
- Mã lỗi (`code`) là số nguyên, message là thông báo tiếng Anh.

---

### 4) Tạo `AppException`
**`AppException.java`**
```java
package com.jb.identity_service.exception;

public class AppException extends RuntimeException {
    private ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public void setErrorCode(ErrorCode errorCode) { this.errorCode = errorCode; }
}
```

---

### 5) Nâng cấp `GlobalExceptionHandler`
**`GlobalExceptionHandler.java`**
```java
package com.jb.identity_service.exception;

import com.jb.identity_service.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi chưa xác định
    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse> handleRuntimeException(RuntimeException e) {
        ApiResponse response = new ApiResponse();
        response.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        response.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    // Lỗi AppException (business logic)
    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse> handleAppException(AppException e) {
        ErrorCode errorCode = e.getErrorCode();
        ApiResponse response = new ApiResponse();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    // Lỗi validation (@Valid)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException e) {
        String enumKey = e.getFieldError().getDefaultMessage();
        ErrorCode errorCode = ErrorCode.valueOf(enumKey);
        ApiResponse response = new ApiResponse();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
```

---

### 6) Chỉnh DTO để liên kết message validation với `ErrorCode`
**`UserCreationRequest.java`**
```java
@Size(min = 5, message = "USERNAME_INVALID")
private String username;

@Size(min = 5, message = "PASSWORD_INVALID")
private String password;
```
- Khi validation fail, message sẽ là tên của enum `ErrorCode` → handler sẽ map ra code & message chuẩn.

---

### 7) Chỉnh Service ném `AppException` thay vì `RuntimeException`
```java
public User createUser(UserCreationRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new AppException(ErrorCode.USER_EXISTED);
    }
    ...
}
```

---

## 🧪 Lệnh test API

### 1) User tạo thành công
```bash
curl -X POST http://localhost:8080/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "secretpass",
    "firstName": "John",
    "lastName": "Doe",
    "dateOfBirth": "2000-01-12"
  }'
# Response:
# {
#   "code": 1000,
#   "result": { ...user data... }
# }
```

### 2) Username trùng
```bash
# Gọi API với username đã tồn tại
# Response:
# {
#   "code": 1002,
#   "message": "User already exists"
# }
```

### 3) Username quá ngắn (<5 ký tự)
```bash
# Response:
# {
#   "code": 1003,
#   "message": "Username is invalid"
# }
```

---

## 🧠 Lưu ý
- Giờ toàn bộ API trả về format thống nhất: `{code, message, result}`.
- Lợi ích:
  - Dễ bắt lỗi ở frontend.
  - Mã lỗi (`code`) tách biệt với message → dễ i18n (đa ngôn ngữ).
- Với validation, cần đảm bảo `message` của annotation trùng tên `ErrorCode`.

---

## 📌 Điều học được
- Sử dụng generic `ApiResponse<T>` để chuẩn hóa response API.
- Tách `ErrorCode` giúp dễ quản lý và đồng bộ mã lỗi.
- `AppException` giúp ném lỗi có ngữ nghĩa rõ ràng.
- Exception handler gom toàn bộ xử lý lỗi về một chỗ.
