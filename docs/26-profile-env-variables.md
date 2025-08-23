# Buổi 26 — Profile & Biến môi trường trong Spring Boot

**Ngày:** 2025-08-23 (UTC+7)

---

## 🎯 Mục tiêu
- Sử dụng **Spring Profiles** để tách cấu hình theo môi trường (`dev`, `prod`).  
- Áp dụng **biến môi trường (Environment Variables)** để cấu hình động (DB URL, username, password).  
- Kích hoạt profile `prod` để chạy với config riêng biệt.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.x  
- MySQL  
- IntelliJ IDEA (hoặc bất kỳ IDE nào)  

---

## ⚙️ 1) Tạo file `application-prod.yaml`
📂 `src/main/resources/application-prod.yaml`

```yaml
jwt:
  signerKey: QiaHFNbjkigFCC7wlRqWZBAnqgpgcq8WNXmeRa7x1dS1yEkFUOATnAqDlSnfSDSb
  valid-duration: 7200 # seconds (2h)
  refresh-valid-duration: 720000 # seconds (~200h)
```

> 🔑 Khi profile = `prod` → app sẽ đọc config trong file này.  

---

## ⚙️ 2) Chỉnh `application.yaml` dùng biến môi trường
📂 `src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: identity-service

  datasource:
    url: ${DBMS_CONNECTION:jdbc:mysql://localhost:3306/identity_service}
    username: ${DBMS_USERNAME:root}
    password: ${DBMS_PASSWORD:1234}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

> 🔑 Giải thích cú pháp:  
> - `${VAR_NAME:default}` → nếu **biến môi trường `VAR_NAME` có giá trị** → lấy giá trị đó.  
> - Nếu **không có** → fallback về **default**.  

Ví dụ:  
- Nếu set `DBMS_CONNECTION=jdbc:mysql://prod-db:3306/identity_service` → app sẽ kết nối prod DB.  
- Nếu không set → mặc định `localhost:3306`.

---

## ⚙️ 3) Kích hoạt profile `prod`
Cách 1 — qua biến môi trường (phù hợp khi deploy):  
```bash
SPRING_PROFILES_ACTIVE=prod
```

Cách 2 — trong IntelliJ:  
- Vào **Run/Debug Configurations → Edit Configurations**.  
- Thêm **Environment Variables**:  
  ```
  SPRING_PROFILES_ACTIVE=prod
  ```

> Nếu không thấy chỗ add env var → bấm **More Actions → Modify Options → Environment Variables**.

---

## 🧪 4) Test nhanh
1. Run app với profile mặc định → log sẽ dùng DB `localhost:3306`.  
2. Run app với `SPRING_PROFILES_ACTIVE=prod` → log sẽ đọc thêm file `application-prod.yaml`.  
   - `jwt.signerKey` & token duration sẽ thay đổi.  
3. Dùng `echo $SPRING_PROFILES_ACTIVE` (Linux/Mac) hoặc `echo %SPRING_PROFILES_ACTIVE%` (Windows) để check.

---

## 📌 Điều học được
- **Spring Profiles** cho phép tách config theo môi trường (dev, test, prod).  
- **Biến môi trường** giúp app chạy linh hoạt, không hard-code username/password.  
- Cách kết hợp `${VAR:default}` giúp dễ dàng fallback khi biến không tồn tại.  
- Triển khai thực tế:  
  - `application.yaml` (default, local dev).  
  - `application-prod.yaml` (production).  
  - CI/CD pipeline sẽ set env `SPRING_PROFILES_ACTIVE=prod`.
