# Buổi 24 — Cấu hình CORS & Kết nối Frontend React với Spring Boot

**Ngày:** 2025-08-22 (UTC+7)

---

## 🎯 Mục tiêu
- Bật **CORS** đúng cách trong Spring Security 6/Spring Boot 3.
- Sử dụng **`CorsConfigurationSource`** thay vì `CorsFilter` để cấu hình CORS tinh gọn hơn.
- Tích hợp **frontend React** (login, my-info) gọi API thành công qua JWT.
- Giải quyết vấn đề **preflight request (OPTIONS)** và **Authorization header**.

---

## 🛠 Công cụ & môi trường
- Backend: Spring Boot 3.5.x, Spring Security (resource server, JWT).
- Frontend: React 18 (CRA), MUI.
- Trình duyệt: Chrome (DevTools để check CORS).

---

## ⚙️ 1) Chuẩn bị Frontend
- FE chạy tại **http://localhost:3000**.
- BE chạy tại **http://localhost:8080/identity**.
- Cấu hình `.env` trong FE (tùy chọn):
  ```env
  REACT_APP_API_BASE=http://localhost:8080/identity
  ```

---
### 1.1 `package.json` (đã cung cấp)
```json
{
  "name": "web-app",
  "version": "0.1.0",
  "private": true,
  "dependencies": {
    "@emotion/react": "^11.11.4",
    "@emotion/styled": "^11.11.5",
    "@mui/icons-material": "^5.15.18",
    "@mui/material": "^5.15.18",
    "@testing-library/jest-dom": "^5.17.0",
    "@testing-library/react": "^13.4.0",
    "@testing-library/user-event": "^13.5.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.23.1",
    "react-scripts": "5.0.1"
  },
  "scripts": {
    "start": "react-scripts start"
  }
}
```

### 1.2 Cài & chạy
```bash
# trong thư mục web-app
npm i
npm start
```

> Mẹo: tạo `.env` cho FE để dễ đổi base:
> ```
> REACT_APP_API_BASE=http://localhost:8080/identity
> ```
> Rồi dùng `process.env.REACT_APP_API_BASE` khi fetch.

---

## ⚙️ 2) Cấu hình CORS trong Spring Security

### 2.1 Bật `.cors()` trong SecurityFilterChain
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // ✅ Bật CORS
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            // ✅ Cho phép preflight
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // ✅ Endpoint public
            .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(customJwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
            .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
        );

    return http.build();
}
```

### 2.2 Đăng ký `CorsConfigurationSource`
```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000")); // FE domain
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("*")); // Cho phép Authorization, Content-Type, v.v.
    config.setAllowCredentials(true); // Cho phép cookie/authorization header

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

> 🔑 **Điểm khác với CorsFilter**:  
> - Đây là cách **Spring Security khuyến nghị** từ Boot 3.x.  
> - Khi bật `.cors()` → Security tự động lấy `CorsConfigurationSource`.  
> - Ưu điểm: gọn hơn, tích hợp trực tiếp với filter chain.  

---

## 🌐 3) React gọi API

### 3.1 Login (POST `/auth/token`)
```js
fetch(`${process.env.REACT_APP_API_BASE}/auth/token`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username, password }),
})
  .then(res => res.json())
  .then(data => {
    if (data.code !== 1000) throw new Error(data.message);
    setToken(data.result?.token);
    navigate("/");
  })
  .catch(err => setSnackBarMessage(err.message));
```

### 3.2 Lấy user info (GET `/users/my-info`)
```js
const token = getToken();
const res = await fetch(`${process.env.REACT_APP_API_BASE}/users/my-info`, {
  headers: { Authorization: `Bearer ${token}` },
});
const data = await res.json();
setUserDetails(data.result);
```

---

## 🧪 4) Test nhanh

1. FE (`npm start`) chạy tại `http://localhost:3000`.  
2. Đăng nhập với `admin/admin` → FE gọi `POST /auth/token` → nhận token.  
3. FE gọi `GET /users/my-info` với header `Authorization: Bearer <token>`.  
4. Nếu config CORS chuẩn → không lỗi **CORS blocked**.  
5. Nếu 401/403 → là lỗi auth chứ không phải CORS.

---

## 📌 Điều học được
- Cấu hình CORS chuẩn với **`CorsConfigurationSource`** giúp Spring Security quản lý toàn bộ.  
- Preflight request (OPTIONS) phải được permitAll.  
- FE và BE khác origin → bắt buộc cấu hình CORS rõ ràng (origin, method, header).  
- Trong production: không dùng `"*"` mà chỉ định domain cụ thể, tách role cho FE/BE rõ ràng.  

---

## 🗺️ Hướng phát triển tiếp theo
- Gom API vào **API Gateway** để loại bỏ khác origin → không cần CORS.  
- Nếu nhiều domain FE → cấu hình nhiều origin trong `CorsConfiguration`.  
- Kiểm soát chặt **AllowCredentials** khi thật sự cần.

