# Buổi 10 — Chuẩn hoá ErrorCode & Xử lý Exception chi tiết (401, 403, 500)
**Ngày:** 2025-08-19 (UTC+7)

## 🎯 Mục tiêu
- Chuẩn hoá **ErrorCode**: thêm `HttpStatusCode` để mỗi lỗi trả về HTTP status đúng chuẩn.
- Cập nhật **ExceptionHandler** để phản hồi status tương ứng thay vì luôn `400 Bad Request`.
- Bổ sung **AccessDeniedException handler** → `403 Forbidden`.
- Bổ sung **AuthenticationEntryPoint** để xử lý `401 Unauthorized` khi JWT invalid/expired.
- Đảm bảo API trả về format thống nhất:  
  ```json
  {
    "code": 1005,
    "message": "User is not authenticated"
  }
  ```

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.4, Java 21  
- Spring Security (OAuth2 Resource Server)  
- Jackson (ObjectMapper) để serialize response  
- Lombok

---

## ⚙️ 1) Cập nhật `ErrorCode`
Thêm field `HttpStatusCode statusCode` để mapping HTTP status.

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
    UNAUTHORIZED(1006, "User is not authorized", HttpStatus.FORBIDDEN),
    ;

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
```

---

## ⚠️ 2) GlobalExceptionHandler
Cập nhật để trả về status code từ `ErrorCode`.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<ApiResponse> handleAccessDeniedException(AccessDeniedException e) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ApiResponse response = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse> handleAppException(AppException e) {
        ErrorCode errorCode = e.getErrorCode();
        ApiResponse response = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse> handleUncategorized(Exception e) {
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        ApiResponse response = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }
}
```

---

## 🔐 3) AuthenticationEntryPoint cho lỗi 401
Khi JWT invalid/expired, Spring Security ném `AuthenticationException`.  
Chúng ta override để trả JSON format thống nhất.

**`JwtAuthenticationEntryPoint.java`**
```java
package com.jb.identity_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jb.identity_service.dto.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(errorCode.getStatusCode().value());
        ApiResponse<?> apiResponse = ApiResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(apiResponse));
        response.flushBuffer();
    }
}
```

---

## 🛡️ 4) Cập nhật SecurityConfig
Đăng ký `JwtAuthenticationEntryPoint`.

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                    .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .decoder(jwtDecoder())
                            .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                    .authenticationEntryPoint(new JwtAuthenticationEntryPoint()) // ✅ thêm
            );

    return http.build();
}
```

---

## 🧪 5) Test thực tế với curl

### (A) Lỗi 401 — chưa login / token invalid
```bash
curl http://localhost:8080/identity/users
# Response:
# {
#   "code": 1005,
#   "message": "User is not authenticated"
# }
```

### (B) Lỗi 403 — user không có quyền
```bash
curl http://localhost:8080/identity/users \
  -H "Authorization: Bearer <JWT_USER>"
# Response:
# {
#   "code": 1006,
#   "message": "User is not authorized"
# }
```

### (C) Lỗi 404 — user không tồn tại
```bash
curl http://localhost:8080/identity/users/invalid-id \
  -H "Authorization: Bearer <JWT_ADMIN>"
# Response:
# {
#   "code": 1001,
#   "message": "User not found"
# }
```

### (D) Lỗi 500 — exception không bắt được
```json
{
  "code": 9999,
  "message": "Uncategorized exception"
}
```

---

## 📌 Điều học được
- Tách biệt rõ **401 Unauthorized** (chưa xác thực) và **403 Forbidden** (không đủ quyền).
- Dùng `HttpStatusCode` trong `ErrorCode` giúp API trả status đúng chuẩn.
- Cách custom **AuthenticationEntryPoint** để Spring Security trả JSON thay vì HTML mặc định.
- Cách centralize xử lý lỗi với **GlobalExceptionHandler**.
