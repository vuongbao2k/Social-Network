# Buổi 27 — Đóng gói & Triển khai ứng dụng

**Ngày:** 2025-08-23 (UTC+7)

---

## 🎯 Mục tiêu
- Hiểu quy trình **build** ứng dụng Spring Boot bằng Maven.  
- Tạo file `.jar` có thể chạy độc lập.  
- Thực hành chạy ứng dụng từ file jar (không cần IDE).  

---

## 🛠 Công cụ & môi trường
- Java 21  
- Spring Boot 3.5.x  
- Maven (hoặc `./mvnw`)  

---

## ⚙️ 1) Build ứng dụng với Maven
Chạy lệnh:

```bash
mvn package -DskipTests
```

📌 Giải thích:  
- `package` → Maven compile code và đóng gói app thành `.jar`.  
- `-DskipTests` → bỏ qua việc chạy test (tiết kiệm thời gian build).  

---

## ⚠️ 2) Vấn đề với Spotless
Trong `pom.xml`, plugin Spotless đang cấu hình ở chế độ **check**:

```xml
<execution>
  <phase>compile</phase>
  <goals>
    <goal>check</goal>
  </goals>
</execution>
```

→ Nghĩa là khi build, Maven sẽ kiểm tra format code. Nếu file nào **không đúng format** → build **fail**.

### Cách xử lý:
1. Trước khi build, chạy:
   ```bash
   mvn spotless:apply
   ```
   để tự động format lại code.  

2. Hoặc đổi `<goal>check</goal>` thành `<goal>apply</goal>` trong `pom.xml` để build tự apply format.

---

## 📦 3) Kết quả build
Sau khi build thành công, trong thư mục `target/` sẽ có file:

```
identity-service-0.0.1-SNAPSHOT.jar
```

---

## ▶️ 4) Chạy ứng dụng từ file JAR
Di chuyển đến thư mục chứa file `.jar`, chạy lệnh:

```bash
java -jar identity-service-0.0.1-SNAPSHOT.jar
```

👉 Ứng dụng sẽ start giống khi chạy từ IDE.  
Truy cập: [http://localhost:8080/identity](http://localhost:8080/identity)

---

## 📌 Điều học được
- Biết cách build project Spring Boot thành file `.jar`.  
- Biết cách xử lý lỗi build liên quan đến plugin Spotless.  
- Có thể chạy ứng dụng **độc lập ngoài IDE**, phục vụ cho việc deploy thực tế.
