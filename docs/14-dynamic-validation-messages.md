# Buổi 14 — Validation Message động với tham số `{min}`

**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- Cập nhật **ErrorCode** để cho phép message động, có tham số `{min}`.  
- Viết lại **GlobalExceptionHandler** để map giá trị constraint (`min`) vào message.  
- Trả về thông báo lỗi **cụ thể và rõ ràng** khi validation thất bại.  

---

## 🛠 Công cụ & môi trường
- Spring Boot Validation (`jakarta.validation`)  
- Spring MVC Exception Handling  
- Lombok  

---

## ⚙️ 1) Cập nhật ErrorCode
```java
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized exception", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_FOUND(1001, "User not found", HttpStatus.NOT_FOUND),
    USER_EXISTED(1002, "User already exists", HttpStatus.BAD_REQUEST),

    USERNAME_INVALID(1003, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(1004, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    DOB_INVALID(1007, "Your age must be at least {min}", HttpStatus.BAD_REQUEST),

    UNAUTHENTICATED(1005, "User is not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1006, "User is not authorized", HttpStatus.FORBIDDEN),
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
```

---

## ⚙️ 2) Bắt lỗi MethodArgumentNotValidException  
Thay đổi để đọc attribute `min` từ constraint và thay thế vào message:
```java
@ExceptionHandler(value = MethodArgumentNotValidException.class)
ResponseEntity<ApiResponse> handleValidationException(MethodArgumentNotValidException e) {
    String enumKey = e.getFieldError().getDefaultMessage();
    ErrorCode errorCode = ErrorCode.valueOf(enumKey);

    var violation = e.getBindingResult().getAllErrors()
            .getFirst()
            .unwrap(ConstraintViolation.class);

    var attributes = violation.getConstraintDescriptor().getAttributes();

    ApiResponse response = new ApiResponse();
    response.setCode(errorCode.getCode());
    response.setMessage(mapAttribute(errorCode.getMessage(), attributes));

    return ResponseEntity.badRequest().body(response);
}

private static final String MIN_ATTRIBUTE = "min";

private String mapAttribute(String message, Map<String, Object> attributes) {
    if (attributes.containsKey(MIN_ATTRIBUTE)) {
        return message.replace("{" + MIN_ATTRIBUTE + "}", attributes.get(MIN_ATTRIBUTE).toString());
    }
    return message;
}
```

---

## 📌 Ví dụ hoạt động
- Nếu `username = "abc"` (3 ký tự) và rule là `@Size(min = 5)`  
  → Response:  
  ```json
  {
    "code": 1003,
    "message": "Username must be at least 5 characters"
  }
  ```

- Nếu `dateOfBirth = 2015-01-01` và rule là `@DobConstraint(min = 18)`  
  → Response:  
  ```json
  {
    "code": 1007,
    "message": "Your age must be at least 18"
  }
  ```

---

## 📌 Điều học được
- Có thể tận dụng **ConstraintDescriptor attributes** để lấy thông tin cấu hình validator.  
- Kỹ thuật **message template động** giúp API trả về thông báo lỗi **cụ thể** và thân thiện hơn cho client.  
- Kết hợp giữa **enum ErrorCode** + **attributes mapping** giúp vừa chuẩn hóa mã lỗi vừa chi tiết hóa thông báo.  

---

## 🗺️ Hướng phát triển tiếp theo
- Hỗ trợ thêm nhiều placeholder (`{max}`, `{pattern}`) cho các validation khác.  
- Chuẩn hóa thông báo lỗi ra nhiều ngôn ngữ (i18n).  
