# Buổi 35 — Tạo password cho user onboard từ Google

**Ngày:** 2025-08-30 (UTC+7)

---

## 🎯 Mục tiêu
- Cho phép user đăng nhập bằng Google trước đó **tạo mật khẩu nội bộ** để có thể login bằng tài khoản hệ thống.
- Hiển thị trạng thái **`noPassword`** trong `/users/my-info` để FE biết cần hiển thị form tạo mật khẩu.
- Thêm endpoint: `POST /users/create-password`.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.x (Security, Validation)
- React 18 + MUI
- Context path backend: `/identity`

---

## 🔧 1) Backend — DTO & Response

### 1.1 PasswordCreationRequest (validation tối thiểu 5 ký tự)
**`dto/request/PasswordCreationRequest.java`**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PasswordCreationRequest {
    @Size(min = 5, message = "PASSWORD_INVALID")
    String password;
}
```

### 1.2 UserResponse — thêm cờ `noPassword`
> Các field khác **giữ nguyên**, chỉ bổ sung:
```java
public class UserResponse {
    String id;
    String username;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
    boolean noPassword;   // NEW
    Set<Role> roles;
}
```

> ✅ Nếu `User` hiện lưu password dạng BCrypt thì `noPassword = true` khi `password` rỗng/null.

---

## 🧠 2) Backend — Service logic

### 2.1 Tạo mật khẩu cho user hiện tại
**`UserService`** (chỉ phần mới/đổi, các phần khác **giữ nguyên**)
```java
public void createPassword(String password) {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    if (user.getPassword() != null) {
        throw new AppException(ErrorCode.PASSWORD_ALREADY_CREATED); // NEW error code
    }

    user.setPassword(passwordEncoder.encode(password));
    userRepository.save(user);
}
```

### 2.2 Cập nhật `getMyInfo` để set `noPassword`
```java
public UserResponse getMyInfo() {
    String username = SecurityContextHolder.getContext().getAuthentication().getName();
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    var userResponse = userMapper.toUserResponse(user);
    userResponse.setNoPassword(!StringUtils.hasText(user.getPassword())); // NEW
    return userResponse;
}
```

### 2.3 Bổ sung ErrorCode (nếu chưa có)
**`ErrorCode`** — thêm hằng số:
```java
PASSWORD_ALREADY_CREATED(1010, "Password already created", HttpStatus.BAD_REQUEST),
```

> Lý do: User onboard từ Google chưa có password → tạo một lần; nếu đã có, chặn thao tác tạo lại.

---

## 🌐 3) Backend — Controller

**`UserController`** — thêm endpoint tạo mật khẩu:
```java
@PostMapping("/create-password")
public ApiResponse<Void> createPassword(@RequestBody @Valid PasswordCreationRequest request) {
    userService.createPassword(request.getPassword());
    return ApiResponse.<Void>builder()
            .message("Password created successfully")
            .build();
}
```

> Endpoint này **yêu cầu JWT nội bộ hợp lệ** (đăng nhập Google → BE phát hành JWT nội bộ ở buổi 34).

---

## 🎨 4) Frontend — Login & Home

### 4.1 `Login.jsx`
- Thêm Snackbar hiển thị lỗi/thành công.
- Không đổi flow Google; login thường vẫn POST `/auth/token`.


### 4.2 `Home.jsx`
- Gọi `/users/my-info` bằng **JWT nội bộ**.
- Nếu `noPassword === true` thì hiển thị form tạo mật khẩu và POST `/users/create-password`.

**Điểm chính trong `Home.jsx`**:
```jsx
{userDetails.noPassword && (
  <Box component="form" onSubmit={addPassword}>
    {/* input password & submit */}
  </Box>
)}
```

**Gọi API tạo password**:
```jsx
fetch("http://localhost:8080/identity/users/create-password", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    Authorization: `Bearer ${getToken()}`,
  },
  body: JSON.stringify({ password }),
})
  .then(res => res.json())
  .then(data => {
    if (data.code != 1000) throw new Error(data.message);
    getUserDetails(getToken());
    showSuccess(data.message);
  })
  .catch(err => showError(err.message));
```

---

## 🔁 Luồng tổng quan
1. User login Google (Authorization Code) → BE **onboard** user (email → username) → BE **phát hành JWT nội bộ** (buổi 34).  
2. FE gọi `/users/my-info` → thấy `noPassword = true` → hiển thị form tạo password.  
3. FE POST `/users/create-password` → backend mã hoá password và lưu.  
4. Từ sau, user có thể login bằng **username/password** nội bộ hoặc tiếp tục login Google.

---

## ✅ Kiểm thử nhanh (Postman/cURL)

### Lấy my-info
```bash
curl -H "Authorization: Bearer <INTERNAL_JWT>" \
http://localhost:8080/identity/users/my-info
```
→ Kiểm tra `noPassword`.

### Tạo mật khẩu
```bash
curl -X POST http://localhost:8080/identity/users/create-password \
 -H "Content-Type: application/json" \
 -H "Authorization: Bearer <INTERNAL_JWT>" \
 -d '{"password":"secret123"}'
```
- Nếu tạo lại lần 2 → nhận `PASSWORD_ALREADY_CREATED`.

---

## 🧩 Lưu ý & best practices
- **Validation**: đã dùng `@Size(min = 5)` cho password; có thể thêm kiểm tra **độ mạnh** nâng cao sau.  
- **Bảo mật**: endpoint tạo password nên xem xét rate-limiting, audit log.  
- **UX**: thông báo rõ khi tạo xong; cập nhật `my-info` để `noPassword = false` ngay.

---

## 📌 Điều học được
- Hoàn thiện vòng đời SSO → tài khoản nội bộ (liên kết Google ↔ user DB).  
- Thiết kế **cờ trạng thái** (`noPassword`) giúp FE quyết định hiển thị UI đúng thời điểm.  
- Áp dụng quy ước lỗi chuẩn (`ErrorCode`) để FE xử lý thông báo nhất quán.

