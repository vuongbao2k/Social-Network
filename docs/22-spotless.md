# Buổi 22 — Chuẩn hóa format code với Spotless

**Ngày:** 2025-08-22 (UTC+7)

---

## 🎯 Mục tiêu
- Tự động **chuẩn hóa format code Java** để codebase đồng nhất.
- Loại bỏ import thừa, format indentation, newline, whitespace.
- Kiểm tra format trong quá trình build (`mvn compile`).
- Có thể **tự động fix** format code bằng 1 lệnh (`spotless:apply`).

---

## 🛠 Công cụ & plugin
- **Spotless Maven Plugin** (by Diffplug)  
- **Palantir Java Format** (Google-style format nhưng ít chỉnh tay hơn)  

---

## ⚙️ 1) Thêm plugin Spotless vào `pom.xml`

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.diffplug.spotless</groupId>
      <artifactId>spotless-maven-plugin</artifactId>
      <version>${spotless.version}</version>
      <configuration>
        <java>
          <removeUnusedImports />          <!-- xóa import thừa -->
          <toggleOffOn/>                   <!-- cho phép bật/tắt format thủ công -->
          <trimTrailingWhitespace/>        <!-- xóa whitespace cuối dòng -->
          <endWithNewline/>                <!-- luôn kết thúc file bằng newline -->
          <indent>
            <tabs>true</tabs>
            <spacesPerTab>4</spacesPerTab>
          </indent>
          <palantirJavaFormat/>            <!-- sử dụng style của Palantir -->
          <importOrder>                    <!-- quy tắc sắp xếp import -->
            <order>java,jakarta,org,com,com.diffplug,</order>
          </importOrder>
        </java>
      </configuration>
      <executions>
        <execution>
          <phase>compile</phase>
          <goals>
            <goal>check</goal>             <!-- kiểm tra format khi compile -->
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

📌 Trong `properties` có thể khai báo version cho gọn:
```xml
<properties>
  <spotless.version>2.43.0</spotless.version>
</properties>
```

---

## 🧪 2) Các lệnh Spotless
- **Áp dụng format cho toàn bộ code** (fix tự động):
  ```bash
  mvn spotless:apply
  ```
- **Chỉ kiểm tra (không tự fix)** — thường chạy trong CI/CD:
  ```bash
  mvn spotless:check
  ```
- **Chạy kèm build**:
  ```bash
  mvn clean compile
  ```
  Nếu file nào chưa đúng format → build fail.

---

## 💡 3) Disable Spotless cho đoạn code đặc biệt
Có thể **bỏ qua một đoạn code** bằng comment:

```java
//spotless:off
public class WeirdFormatting {
        public   void doSomething( ) {System.out.println( "Keep my spacing" );}
}
//spotless:on
```

👉 Spotless sẽ **bỏ qua đoạn giữa `//spotless:off` và `//spotless:on`**.

---

## ✅ Lợi ích
- Giữ code đồng nhất → dễ review, dễ merge.
- Tự động loại bỏ import thừa, format lại indent, newline.
- Áp dụng **CI/CD check** → tránh commit code “bẩn”.

---

## 📌 Hướng mở rộng
- Tích hợp Spotless vào **Git pre-commit hook** (chạy `mvn spotless:apply` trước khi commit).
- Dùng thêm rule cho **JSON, YAML, Markdown** (Spotless hỗ trợ nhiều format ngoài Java).
- Cấu hình format giống với team convention (Google/Palantir/AOSP…).

---

## 🗂️ Tài liệu tham khảo
- [Spotless Maven Plugin Docs](https://github.com/diffplug/spotless/tree/main/plugin-maven)  
- [Palantir Java Format](https://github.com/palantir/palantir-java-format)  
