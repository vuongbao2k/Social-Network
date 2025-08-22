# Buổi 20 — Code Coverage với JaCoCo & Cách tăng Coverage

**Ngày:** 2025-08-20 (UTC+7)

---

## 🎯 Mục tiêu
- Tích hợp **JaCoCo** để đo **code coverage** khi chạy test.
- Xem báo cáo coverage qua **HTML report**.
- Viết thêm test (ví dụ: `getMyInfo`) để **tăng coverage** phần Service.
- Tối ưu coverage bằng cách **exclude** các package **không cần tính coverage** (DTO/Entity/Mapper/Config).

---

## 🛠 Công cụ & môi trường
- Maven, JUnit 5, Mockito, Spring Boot Test
- JaCoCo Maven Plugin
- H2 (test), Spring Security Test (mock user)

---

## ⚙️ 1) Thêm plugin JaCoCo (pom.xml)
```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <!-- Gắn Java agent để đo coverage khi chạy test -->
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <!-- Sinh báo cáo HTML -->
    <execution>
      <id>report</id>
      <phase>prepare-package</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <excludes>
      <!-- Exclude các phần không cần đo coverage -->
      <!-- LƯU Ý: path phải đúng package "com/jb" -->
      <exclude>com/jb/identity_service/dto/**</exclude>
      <exclude>com/jb/identity_service/entity/**</exclude>
      <exclude>com/jb/identity_service/mapper/**</exclude>
      <exclude>com/jb/identity_service/config/**</exclude>
    </excludes>
  </configuration>
</plugin>
```
> *Giải thích:*  
> - `prepare-agent`: JaCoCo gắn agent Java khi chạy test.  
> - `report`: sinh báo cáo sau khi test xong (ở phase `prepare-package`).  
> - `excludes`: loại trừ các lớp **không có logic** (DTO/Entity/Mapper/Config) để coverage phản ánh đúng effort test business.

---

## ⚙️ 2) Cài thêm dependency cho Security test (mock user)
```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

---

## 🧪 3) Viết thêm test để tăng coverage — `getMyInfo` (Service)
> Mục tiêu: cover cả **happy path** và **exception path**.

```java
@Test
@WithMockUser(username = "testuser")
void getMyInfo_userExists_returnsUserResponse() {
    // GIVEN
    Mockito.when(userRepository.findByUsername(ArgumentMatchers.anyString()))
            .thenReturn(Optional.of(user));
    // WHEN
    UserResponse response = userService.getMyInfo();
    // THEN
    Assertions.assertThat(response.getId()).isEqualTo("12345");
    Assertions.assertThat(response.getUsername()).isEqualTo("testuser");
    Assertions.assertThat(response.getFirstName()).isEqualTo("Test");
    Assertions.assertThat(response.getLastName()).isEqualTo("User");
    Assertions.assertThat(response.getDateOfBirth()).isEqualTo(dateOfBirth);
}

@Test
@WithMockUser(username = "testuser")
void getMyInfo_userNotFound_throwsException() {
    // GIVEN
    Mockito.when(userRepository.findByUsername(ArgumentMatchers.anyString()))
            .thenReturn(Optional.empty());
    // WHEN & THEN
    var exception = assertThrows(AppException.class, () -> userService.getMyInfo());
    Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1001); // USER_NOT_FOUND
}
```

> *Giải thích logic:*  
> - `@WithMockUser` mock `SecurityContext` để service lấy `authentication.name`.  
> - Case 1: có user → trả `UserResponse`.  
> - Case 2: không có user → ném `AppException(USER_NOT_FOUND)`.

---

## ▶️ 4) Chạy test & mở báo cáo
Chạy bằng Terminal (chọn 1 trong các cách):
```bash
mvn clean test jacoco:report
# hoặc
./mvnw clean test jacoco:report
# hoặc chạy test từ IntelliJ, sau đó:
mvn jacoco:report
```

Mở báo cáo HTML:
- Đường dẫn: `target/site/jacoco/index.html`  
- Mở bằng Chrome/Browser → xem cột **Cov.**, **Missed**, **Covered** theo **Lines/Instructions/Branches/Methods**.

---

## 📈 5) Mẹo tăng coverage (thực tế, nhanh gọn)
- **Excludes hợp lý**: DTO/Entity/Mapper/Config thường không có logic → loại khỏi coverage để số liệu phản ánh phần cần test.  
- **Test cả nhánh lỗi**: với service có `if/throw`, luôn viết ít nhất **02 test** (OK + lỗi).  
- **Validation**: viết test cho `@Size`, `@DobConstraint` để cover `GlobalExceptionHandler` mapping `{min}`.  
- **Controller**: dùng **MockMvc** test status + payload (đã có mẫu từ buổi 18).  
- **Security paths**: test 401/403 nhanh bằng call API **không token** và **token không đủ quyền**.  
- **Boundary**: test giá trị **sát min** (ví dụ username 4/5 ký tự; DOB 17/18 tuổi).

---

## 🔍 6) (Tuỳ chọn) Kiểm soát ngưỡng coverage khi CI
Thêm `jacoco:check` để fail build nếu coverage thấp (có thể làm ở buổi sau):
```xml
<execution>
  <id>check</id>
  <goals>
    <goal>check</goal>
  </goals>
  <configuration>
    <rules>
      <rule>
        <element>PACKAGE</element>
        <limits>
          <limit>
            <counter>INSTRUCTION</counter>
            <value>COVEREDRATIO</value>
            <minimum>0.70</minimum>
          </limit>
        </limits>
      </rule>
    </rules>
  </configuration>
</execution>
```

---

## 🧠 Điều học được
- Cách tích hợp **JaCoCo** và **đọc báo cáo coverage**.
- Phân biệt phần **cần đo coverage** và **nên exclude**.
- Chiến lược tăng coverage bền vững: **thêm test cho nhánh lỗi**, **boundary test**, **security test**.

---

## 🗺️ Hướng phát triển tiếp theo
- Thêm **jacoco:check** với ngưỡng tối thiểu (CI).  
- Viết **integration test** cho `/auth/refresh`, `/auth/logout`.  
- Cover **GlobalExceptionHandler** đầy đủ (401/403/404/500 + mapping `{min}`).

