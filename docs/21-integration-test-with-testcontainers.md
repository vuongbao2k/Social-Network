# Buổi 21 — Integration Test với Testcontainers (Spring Boot 3)

**Ngày:** 2025-08-20 (UTC+7)

---

## 🎯 Mục tiêu
- Thiết lập **Integration Test** thay vì chỉ Unit/Mock test:
  - Chạy **MySQL thật** trong Docker bằng **Testcontainers**.
  - Nối Spring Boot ↔ MySQL container qua `@DynamicPropertySource`.
  - Test `UserController` **end-to-end** (từ HTTP → Service → Repository → DB).
- Lưu ý khi assert: **không kiểm tra `id`** (UUID ngẫu nhiên do DB/app tạo).

---

## 🛠 Công cụ & môi trường
- Spring Boot 3, JUnit 5, MockMvc  
- **Testcontainers** (MySQL, JUnit Jupiter)  
- Docker Desktop / Docker Engine (phải chạy được Docker)

---

## ⚙️ 1) Thêm dependency Testcontainers + BOM (pom.xml)
```xml
<dependencies>
  <!-- ... các deps khác ... -->

  <!-- Testcontainers -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-bom</artifactId>
      <version>1.19.7</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

> Testcontainers BOM giúp đồng bộ version giữa các module `mysql`, `junit-jupiter`,…

---

## 🧪 2) Integration test cho `UserController` với MySQL container
> Sao chép từ `UserControllerTest` (buổi 18), **bỏ hết @MockBean**, thay bằng **container thật** + Dynamic properties.

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class UserControllerIntegrationTest {

    @Container
    static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:latest");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update"); // auto tạo table
    }

    @Autowired
    private MockMvc mockMvc;

    private UserCreationRequest request;
    private LocalDate dateOfBirth;

    @BeforeEach
    void initData() {
        dateOfBirth = LocalDate.of(1990, 1, 1);
        request = UserCreationRequest.builder()
                .username("testuser")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(dateOfBirth)
                .build();
    }

    @Test
    void createUser_validRequest_success() throws Exception {
        // GIVEN
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String requestBody = objectMapper.writeValueAsString(request);

        // WHEN & THEN
        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("testuser"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.firstName").value("Test"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.lastName").value("User"));
        // ⚠️ Không assert "result.id" vì UUID ngẫu nhiên
    }
}
```

### Giải thích nhanh
- `@Testcontainers`: bật lifecycle quản lý container trong test.
- `@Container`: khởi tạo **MySQL docker** (pull image `mysql:latest` nếu chưa có).
- `@DynamicPropertySource`: inject URL/user/pass/driver vào Spring Context → app chạy với DB trong container.
- `ddl-auto=update`: để Hibernate tự tạo bảng (trong test này không dùng migration).
- `MockMvc`: gọi endpoint `/users` như thật, chạy xuyên qua toàn bộ stack.

---

## ✅ Chạy bài test
```bash
./mvnw -Dtest=*IntegrationTest test
# hoặc chạy toàn bộ:
./mvnw test
```
> Docker phải đang chạy. Lần đầu có thể lâu hơn do **pull image `mysql:latest`**.

---

## 💡 Lưu ý & Mẹo tăng độ ổn định
- **Không assert `id`** (UUID random). Tập trung assert **status code**, **payload fields** có thể dự đoán (username, firstName, …).
- Dữ liệu trùng `username` giữa test có thể gây **409/400** do unique logic:
  - Mẹo: thêm hậu tố ngẫu nhiên, ví dụ `"testuser" + System.nanoTime()` hoặc `UUID.randomUUID()`.
- Nếu bạn có bean seeding (`ApplicationRunner`) chỉ chạy khi driver là MySQL (prod), test này đang set driver MySQL → **seeder có thể chạy**:
  - Nếu không cần seeding khi integration test, thêm điều kiện property riêng (ví dụ `app.seed.enabled=true` ở `application.yaml` prod, và **không set** trong test), rồi `@ConditionalOnProperty(prefix="app.seed", name="enabled", havingValue="true")`.

---

## 📌 Điều học được
- **Integration Test** với Testcontainers giúp mô phỏng môi trường gần prod nhất (DB thật) nhưng vẫn **tách biệt & lặp lại** được.
- `@DynamicPropertySource` là chìa khóa để Spring Boot **đọc config runtime từ container**.
- Dùng **MockMvc** cho **HTTP-level** integration test, kết hợp DB thật → kiểm tra full luồng.

---

## 🗺️ Hướng phát triển tiếp theo
- Viết thêm case **validation lỗi** (username ngắn) trong Integration Test → kỳ vọng `400 + code 1003`.
- Thêm Integration Test cho:
  - `/auth/token` (đăng nhập), `/auth/refresh`, `/auth/logout`.
  - Endpoint có **@PreAuthorize/@PostAuthorize** để kiểm thử bảo mật end-to-end.
- Dùng **Testcontainers Reuse** cho tốc độ (enable `~/.testcontainers.properties`).

