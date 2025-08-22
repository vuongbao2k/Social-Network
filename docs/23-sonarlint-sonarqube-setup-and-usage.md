# Buổi 23 — Code “chuyên nghiệp” hơn với SonarLint & SonarQube

**Ngày:** 2025-08-22 (UTC+7)

---

## 🎯 Mục tiêu
- Dựng **SonarQube** cục bộ bằng Docker, phân tích chất lượng code (bugs, vulnerabilities, code smells, coverage).
- Tích hợp **SonarScanner for Maven** để đẩy kết quả từ project lên SonarQube.
- Kết nối **JaCoCo coverage** (từ buổi 20) vào SonarQube.
- Dùng **SonarLint** trong IntelliJ để sửa lỗi ngay trong IDE.
- Áp dụng một số **best practices** thường gặp mà Sonar nhắc (đổi field injection → constructor injection, chuẩn hoá constant, v.v.)

---

## 🛠 Công cụ & môi trường
- **Docker** (Desktop/Engine)
- **SonarQube LTS (Community)** trên cổng **9000**
- **Maven** + **SonarScanner for Maven** (goal `sonar:sonar`)
- **JaCoCo** (đã cấu hình ở buổi 20)
- IDE: **IntelliJ** + plugin **SonarLint**

---

## ⚙️ 1) Chạy SonarQube bằng Docker
1) Kéo image (từ Docker Hub: `sonarqube:lts-community`):
```bash
docker pull sonarqube:lts-community
```
2) Chạy container:
```bash
docker run --name sonar-qube -p 9000:9000 -d sonarqube:lts-community
```
> Lần sau chỉ cần **start container** từ UI Docker, không cần chạy lại lệnh.

3) Truy cập: **http://localhost:9000**  
   - Lần đầu đăng nhập: **admin / admin** → đổi mật khẩu.

---

## ⚙️ 2) Tạo Project & Token trên SonarQube
- Trên web SonarQube → **Create project** → **Manually** → đặt tên (ví dụ: `MS_identity-service`).
- **Setup** → **Locally** → tạo **Token** (chọn *No expiry* khi thử nghiệm).
- Chọn **Maven** → SonarQube sẽ hiển thị lệnh để copy.

Ví dụ lệnh (thay `projectKey` & `login` theo token của bạn):
```bash
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=MS_identity-service \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=sqp_xxx_your_token_xxx \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

> Gợi ý: đã có **JaCoCo** ở buổi 20 → thêm property `sonar.coverage.jacoco.xmlReportPaths` để Sonar lấy coverage.

---

## 🔗 3) (Tuỳ chọn) Khai báo plugin Sonar trong `pom.xml`
> Không bắt buộc vì `sonar:sonar` tải plugin từ Maven Central, nhưng có thể thêm để rõ ràng.

```xml
<build>
  <plugins>
    <!-- ... các plugin khác ... -->

    <!-- SonarScanner for Maven (optional explicit) -->
    <plugin>
      <groupId>org.sonarsource.scanner.maven</groupId>
      <artifactId>sonar-maven-plugin</artifactId>
      <version>3.10.0.2594</version>
    </plugin>
  </plugins>
</build>
```

---

## ▶️ 4) Chạy phân tích & xem kết quả
- Ở thư mục project:
```bash
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=MS_identity-service \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=sqp_xxx \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```
- Mở **http://localhost:9000** → vào project → xem **Bugs / Vulnerabilities / Code Smells / Coverage / Duplications**.

---

## 🧰 5) SonarLint trên IntelliJ (sửa lỗi trong IDE)
- Cài plugin **SonarLint** (Marketplace).
- Dùng **Standalone** (phân tích cục bộ) hoặc **Bind to SonarQube** (kết nối server để đồng bộ rules & issues).
- SonarLint gợi ý sửa trực tiếp. Ví dụ các cảnh báo thường gặp:

### (a) Constant naming / field constant
**Cảnh báo:** “Rename this field `PUBLIC_ENDPOINTS` to match regex `^[a-z][a-zA-Z0-9]*$`”  
**Cách xử lý:** Nếu đây là **constant thực sự**, khai báo **`static final`** để Sonar hiểu đây là hằng số UPPER_SNAKE_CASE hợp lệ.
```java
// Trước (có thể bị cảnh báo vì không static final)
private final String[] PUBLIC_ENDPOINTS = { "/users", "/auth/token", "/auth/introspect", "/auth/logout", "/auth/refresh" };

// Sau (constant chuẩn)
private static final String[] PUBLIC_ENDPOINTS = {
    "/users", "/auth/token", "/auth/introspect", "/auth/logout", "/auth/refresh"
};
```
> Nếu không phải constant, hãy đổi sang **lowerCamelCase** (ví dụ `publicEndpoints`) và **không** `static final`.

### (b) Field injection → Constructor injection
**Cảnh báo:** “Use constructor injection instead of field injection (@Autowired)”.

- Giải pháp: dùng **Lombok `@RequiredArgsConstructor`** + **`final`** cho dependency.
```java
// Trước
@Autowired
private CustomJwtDecoder customJwtDecoder;

// Sau (class)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Configuration
public class SecurityConfig {
    CustomJwtDecoder customJwtDecoder;
    // ...
}
```

### (c) Logging & Magic numbers
- Dùng `log.warn/info/error` thay vì `System.out.println`.
- Trích xuất **magic numbers** thành constant (ví dụ: `BCryptPasswordEncoder(10)` → đưa `SALT_ROUNDS = 10`).

### (d) Exception & Nullability
- Thêm **message hữu ích** khi `throw new AppException(...)`.
- Tránh `NullPointerException`: dùng `Objects.requireNonNullElse`, `Optional`, hoặc validate sớm.

---

## 🧪 6) Gắn Coverage vào Sonar (nhắc lại)
- Chạy trước **JaCoCo** để tạo report:
```bash
mvn clean test jacoco:report
```
- Rồi chạy Sonar (cùng lúc cũng được):
```bash
mvn clean verify sonar:sonar \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

---

## 📌 Điều học được
- Dựng **SonarQube** rất nhanh bằng Docker; quy trình phân tích với **SonarScanner for Maven** đơn giản.
- **SonarLint** giúp sửa vấn đề “tại chỗ”, tránh dồn lỗi chất lượng.
- Nhiều cảnh báo của Sonar thực chất là **best practices**: constructor injection, constant đúng nghĩa, không để code smells kéo dài.
- Coverage từ **JaCoCo** có thể đưa vào Sonar để theo dõi ở một nơi duy nhất.

---

## 🗺️ Hướng phát triển tiếp
- Thiết lập **Quality Gate** (ví dụ coverage ≥ 70%, không có bug blocker).
- Tích hợp **CI/CD** (GitHub Actions/GitLab CI) để chạy `sonar:sonar` mỗi PR.
- Bật **branch & PR analysis** (trên SonarQube Developer Edition trở lên) — Community chỉ hỗ trợ hạn chế.

