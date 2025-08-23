# Buổi 25 — Unique field trong JPA & xử lý concurrent request

**Ngày:** 2025-08-23 (UTC+7)

---

## 🎯 Mục tiêu
- Hiểu vấn đề **concurrent request** khi nhiều client cùng tạo user với username trùng.  
- Sử dụng **JMeter** để test tải và tái hiện race condition.  
- Đảm bảo tính duy nhất cho username bằng cách:  
  - Thêm **unique constraint** ở DB (JPA annotation).  
  - Bắt **DataIntegrityViolationException** để trả lỗi business.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.x, JPA/Hibernate.  
- MySQL.  
- Apache JMeter (bản binary `.zip`).  
- Postman (test đơn lẻ).  

---

## ⚙️ 1) Cài đặt & chạy JMeter
1. Tải JMeter binary từ [https://jmeter.apache.org/download_jmeter.cgi](https://jmeter.apache.org/download_jmeter.cgi).  
2. Giải nén → vào thư mục `/bin` → chạy `jmeter.bat` (Windows) hoặc `./jmeter` (Linux/Mac).  
3. Trên giao diện:
   - Chuột phải **Test Plan → Add → Threads → Thread Group**, đặt tên: `Create User`.  
   - Chuột phải **Create User → Add → Sampler → HTTP Request**.  
     - Protocol: `http`  
     - Server Name: `localhost`  
     - Port: `8080`  
     - Method: `POST`  
     - Path: `/identity/users`  
     - Body Data: JSON user giống Postman.  
   - Đặt Name: `create user`.  
   - Chuột phải **create user → Add → Config Element → HTTP Header Manager**.  
     - Add → Name = `Content-Type`, Value = `application/json`.  
   - Chuột phải **Create User → Add → Listener → View Results Tree** để xem kết quả.  
4. Cấu hình Thread Group:  
   - Number of threads: `5`  
   - Ramp-up period: `0`  
   - Loop count: `1`  
   - Bấm **Start** để chạy test.  
   - Xem kết quả trong **View Results Tree**.  

👉 Kết quả: nhiều user được tạo trùng username vì xử lý logic chưa chặn concurrency kịp.

---

## ⚙️ 2) Đảm bảo tính duy nhất ở DB
Cập nhật entity `User` để enforce unique constraint trên cột `username`:

```java
@Column(
    name = "username",
    unique = true,
    columnDefinition = "VARCHAR(50) COLLATE utf8mb4_unicode_ci"
)
String username;
```

> 🔑 Giải thích:
> - `unique = true` → JPA tạo unique constraint ở DB.  
> - `columnDefinition` → đảm bảo so sánh case-insensitive nếu DB dùng collation `utf8mb4_unicode_ci`.  

⚠️ Vì thay đổi cấu trúc bảng, cần **drop database** và tạo lại (chỉ dùng cho local/dev).  
**Không nên drop trực tiếp trên production**.

---

## ⚙️ 3) Bắt lỗi khi username trùng
Cập nhật `UserService.createUser`:

```java
public UserResponse createUser(UserCreationRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
        throw new AppException(ErrorCode.USER_EXISTED);
    }

    User user = userMapper.toUser(request);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    try {
        user = userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
        // 🔑 Concurrent requests cùng insert username -> DB sẽ báo lỗi unique
        throw new AppException(ErrorCode.USER_EXISTED);
    }

    return userMapper.toUserResponse(user);
}
```

> 🔑 Tại sao cần `try-catch`?  
> - `existsByUsername` chỉ **check trước**, nhưng khi nhiều request chạy đồng thời, có thể **chưa kịp commit** mà nhiều thread cùng qua check.  
> - DB unique constraint là lớp phòng thủ cuối cùng → khi vi phạm, ném `DataIntegrityViolationException`.  
> - Do đó cần catch và map sang **business exception** (`USER_EXISTED`).  

---

## 🧪 4) Test lại với JMeter
- Chạy lại test với 5 threads tạo cùng username.  
- Kết quả:  
  - 1 request thành công.  
  - 4 request trả về lỗi `USER_EXISTED`.  
- Đảm bảo **tính toàn vẹn dữ liệu**.

---

## 📌 Điều học được
- Không chỉ check bằng code (`existsByUsername`), mà cần **unique constraint ở DB**.  
- Khi concurrent request xảy ra, DB constraint là lớp bảo vệ cuối cùng.  
- `DataIntegrityViolationException` cần được catch và chuyển thành `AppException` có `ErrorCode` rõ ràng.  
- Dùng **JMeter** để kiểm thử concurrency, giúp phát hiện bug race condition.  

---

## 🗺️ Hướng phát triển tiếp theo
- Tạo **index** và constraint chuẩn trên các field quan trọng (email, phone…).  
- Nếu hệ thống scale, có thể dùng **distributed lock** (Redis) cho một số trường hợp đặc biệt.  
- Áp dụng **optimistic locking** cho entity quan trọng để tránh overwrite.

