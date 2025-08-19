# Buổi 10 – Chuẩn hoá ErrorCode & Xử lý Exception (401, 403, 500)
**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- Chuẩn hoá **ErrorCode** để gắn kèm `HttpStatusCode` → giúp mapping chính xác sang HTTP status.  
- Cập nhật **GlobalExceptionHandler** để trả response đúng chuẩn:  
  - `401 Unauthorized` → chưa xác thực.  
  - `403 Forbidden` → không đủ quyền.  
  - `404 Not Found` → dữ liệu không tồn tại.  
- Tạo **AuthenticationEntryPoint** custom để can thiệp lỗi 401 từ Spring Security.  
- Đồng bộ response API bằng `ApiResponse` → dễ debug & frontend dễ consume.

---

## 🛠 Công cụ & môi trường
- Java 21, Spring Boot 3.5.4  
- Spring Security (OAuth2 Resource Server)  
- Lombok  
- Jackson (ObjectMapper để trả JSON)

---

## ⚙️ 1) Cập nhật `ErrorCode`  
Trước đây chỉ có `code` và `message`. Bây giờ bổ sung thêm `HttpStatusCode` để response có thể trả đúng status.  

**`ErrorCode.java`**
```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized exception", HttpStatus.INTERNAL_SERVER_ERROR),
    USER_NOT_FOUND(1001, "User not found", HttpStatus.NOT_FOUND),
    USER_EXISTED(1002, "User already exists", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Username is invalid", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(1004, "Password is invalid", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1005, "User is not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1006, "User is not authorized", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
```

💡 Nhờ vậy khi bắn exception, ta không cần hardcode `400 Bad Request` mặc định nữa.  

---

## ⚠️ 2) GlobalExceptionHandler
Trước đây `AppException` luôn trả 400. Bây giờ update để lấy status từ `ErrorCode`. Đồng thời thêm handler cho `AccessDeniedException` (403).  

**`GlobalExceptionHandler.java`** (chỉ highlight phần mới/được sửa)
```java
// ❌ lỗi 403 – không có quyền
@ExceptionHandler(AccessDeniedException.class)
ResponseEntity<ApiResponse> handleAccessDeniedException(AccessDeniedException e) {
    ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
    ApiResponse response = ApiResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .build();
    return ResponseEntity.status(errorCode.getStatusCode()).body(response);
}

// ❌ lỗi custom từ AppException
@ExceptionHandler(AppException.class)
ResponseEntity<ApiResponse> handleAppException(AppException e) {
    ErrorCode errorCode = e.getErrorCode();
    ApiResponse response = ApiResponse.builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .build();
    return ResponseEntity.status(errorCode.getStatusCode()).body(response);
}
```

---

## 🔐 3) Custom AuthenticationEntryPoint
Spring Security khi lỗi 401 mặc định trả HTML. Ta override để trả JSON theo format của hệ thống.  

**`JwtAuthenticationEntryPoint.java`**
```java
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(errorCode.getStatusCode().value());

        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        new ObjectMapper().writeValue(response.getWriter(), apiResponse);
        response.flushBuffer();
    }
}
```

---

## 🛡️ 4) SecurityConfig
Cấu hình `authenticationEntryPoint` để Spring gọi class custom vừa tạo khi xảy ra lỗi 401.  

**`SecurityConfig.java`** (chỉ phần thay đổi)
```java
http.oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt
                .decoder(jwtDecoder())
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
        )
        .authenticationEntryPoint(new JwtAuthenticationEntryPoint()) // ✅ thêm dòng này
);
```

---

## 🧪 5) Test nhanh

### 1) Chưa login / token sai → 401 Unauthorized
```json
{
  "code": 1005,
  "message": "User is not authenticated"
}
```

### 2) User thường gọi API của admin → 403 Forbidden
```json
{
  "code": 1006,
  "message": "User is not authorized"
}
```

### 3) User không tồn tại → 404 Not Found
```json
{
  "code": 1001,
  "message": "User not found"
}
```

### 4) Exception chưa bắt → 500 Internal Server Error
```json
{
  "code": 9999,
  "message": "Uncategorized exception"
}
```

---

## 📌 Điều học được
- Sự khác biệt **401 vs 403**:  
  - `401 Unauthorized`: chưa login/token invalid.  
  - `403 Forbidden`: đã login nhưng không có quyền.  
- Dùng `HttpStatusCode` trong `ErrorCode` để đảm bảo **status code chính xác**.  
- Custom `AuthenticationEntryPoint` để override response mặc định của Spring Security.  
- Response API được chuẩn hoá theo format `ApiResponse`.
