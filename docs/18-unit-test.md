# Buổi 18 — Thêm hỗ trợ LocalDate và Viết Test cho Controller & Service

**Ngày:** 2025-08-20 (UTC+7)

---

## 🎯 Mục tiêu
- Cho phép **serialize/deserialize `LocalDate`** trong request/response JSON.  
- Viết **Unit Test cho Service** và **Integration Test cho Controller** để kiểm chứng logic:  
  - Tạo user thành công.  
  - Bắt lỗi validation username không hợp lệ.  
  - Xử lý khi username đã tồn tại.  

---

## 🛠️ 1. Thêm dependency hỗ trợ LocalDate
```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

---

## ⚙️ 2. Đăng ký module cho `ObjectMapper`
```java
// Khi dùng trực tiếp trong test
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());
```

> Trong ứng dụng thực tế, có thể tạo `@Bean` config để tất cả `ObjectMapper` tự động hỗ trợ `LocalDate`.

---

## 🧪 3. Test cho Controller

### ✅ Case: Tạo user thành công
```java
@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private UserCreationRequest request;
    private UserResponse userResponse;
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

        userResponse = UserResponse.builder()
                .id("12345")
                .username("testuser")
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

        Mockito.when(userService.createUser(ArgumentMatchers.any()))
                .thenReturn(userResponse);

        // WHEN & THEN
        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000))
                .andExpect(MockMvcResultMatchers.jsonPath("result.username").value("testuser"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.firstName").value("Test"))
                .andExpect(MockMvcResultMatchers.jsonPath("result.lastName").value("User"));
    }
```

### ❌ Case: Username không hợp lệ
```java
    @Test
    void createUser_usernameInvalid_fail() throws Exception {
        // GIVEN
        request.setUsername("hi"); // invalid
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String requestBody = objectMapper.writeValueAsString(request);

        // WHEN & THEN
        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1003))
                .andExpect(MockMvcResultMatchers.jsonPath("message")
                        .value("Username must be at least 5 characters"));
    }
}
```

---

## 🧪 4. Test cho Service

### ✅ Case: Tạo user thành công
```java
@SpringBootTest
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    private UserCreationRequest request;
    private UserResponse userResponse;
    private LocalDate dateOfBirth;
    private User user;

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

        user = User.builder()
                .id("12345")
                .username("testuser")
                .firstName("Test")
                .lastName("User")
                .dateOfBirth(dateOfBirth)
                .build();
    }

    @Test
    void createUser_validRequest_success() {
        // GIVEN
        Mockito.when(userRepository.existsByUsername(request.getUsername()))
                .thenReturn(false);
        Mockito.when(userRepository.save(Mockito.any(User.class)))
                .thenReturn(user);

        // WHEN
        UserResponse response = userService.createUser(request);

        // THEN
        Assertions.assertThat(response.getId()).isEqualTo("12345");
        Assertions.assertThat(response.getUsername()).isEqualTo("testuser");
        Assertions.assertThat(response.getFirstName()).isEqualTo("Test");
        Assertions.assertThat(response.getLastName()).isEqualTo("User");
        Assertions.assertThat(response.getDateOfBirth()).isEqualTo(dateOfBirth);
    }
```

### ❌ Case: Username đã tồn tại
```java
    @Test
    void createUser_usernameAlreadyExists_throwsException() {
        // GIVEN
        Mockito.when(userRepository.existsByUsername(request.getUsername()))
                .thenReturn(true);

        // WHEN & THEN
        var exception = assertThrows(AppException.class, () -> {
            userService.createUser(request);
        });

        Assertions.assertThat(exception.getErrorCode().getCode()).isEqualTo(1002);
    }
}
```

---

## 📌 Điều học được
- Cần `jackson-datatype-jsr310` để xử lý `LocalDate` trong JSON.  
- Dùng **MockMvc** test Controller theo hướng **integration**.  
- Dùng **MockBean** test Service theo hướng **unit test**.  
- Viết test đảm bảo **business rule**: unique username, validation rule, trả về code & message chính xác.
