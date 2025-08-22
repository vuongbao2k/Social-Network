# Buổi 19 — Cấu hình Test với H2 Database & Bổ sung Mock Repository

**Ngày:** 2025-08-20 (UTC+7)

---

## 🎯 Mục tiêu
- Thêm **H2 Database** cho môi trường test.  
- Cấu hình **test.properties** để test không ảnh hưởng MySQL thật.  
- Bổ sung **mock `RoleRepository`** trong Unit Test.  
- Thêm **@ConditionalOnProperty** cho ApplicationInitConfig để khi chạy test không tạo dữ liệu mặc định.

---

## 🛠️ 1. Thêm dependency H2 Database (chỉ cho test)
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.3.232</version>
    <scope>test</scope>
</dependency>
```

---

## ⚙️ 2. Tạo `test.properties`
📂 `src/test/resources/test.properties`
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MYSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=sa
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=none
```

> `MODE=MYSQL` giúp H2 hiểu cú pháp SQL của MySQL.  
> `ddl-auto=none` để tránh Hibernate tự tạo schema khi test.

---

## ⚙️ 3. Chỉ định dùng `test.properties` trong Test
```java
@SpringBootTest
@TestPropertySource("/test.properties")
public class UserServiceTest {
    // ...
}
```

```java
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
public class UserControllerTest {
    // ...
}
```

---

## 🧪 4. Bổ sung mock `RoleRepository` trong Service Test
```java
@MockBean
private RoleRepository roleRepository;

@Test
void createUser_validRequest_success() {
    // GIVEN
    Mockito.when(userRepository.existsByUsername(request.getUsername()))
            .thenReturn(false);
    Mockito.when(userRepository.save(Mockito.any(User.class)))
            .thenReturn(user);
    Mockito.when(roleRepository.findById("USER_ROLE"))
            .thenReturn(Optional.empty()); // Giả sử chưa có role USER_ROLE trong test

    // WHEN
    UserResponse response = userService.createUser(request);

    // THEN
    Assertions.assertThat(response.getId()).isEqualTo("12345");
    Assertions.assertThat(response.getUsername()).isEqualTo("testuser");
}
```

---

## ⚙️ 5. Bổ sung `driver-class-name` trong `application.yaml`
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
```

---

## ⚙️ 6. Ngăn ApplicationInitConfig chạy trong môi trường test
```java
@Bean
@ConditionalOnProperty(
    prefix = "spring.datasource",
    value = "driver-class-name",
    havingValue = "com.mysql.cj.jdbc.Driver"
)
ApplicationRunner applicationRunner() {
    return args -> {
        // code init mặc định (admin, role, user)
    };
}
```

> Khi chạy test với `H2`, property là `org.h2.Driver` nên `ApplicationRunner` sẽ không được tạo → tránh insert dữ liệu vào DB test.

---

## 📌 Điều học được
- Dùng **H2 In-Memory DB** cho test để nhanh và an toàn.  
- Cấu hình **@TestPropertySource** để tách biệt config test/production.  
- Bổ sung **mock repository** khi service phụ thuộc đến nhiều tầng.  
- Sử dụng **@ConditionalOnProperty** để kiểm soát bean chạy tùy môi trường.
