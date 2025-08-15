# Buổi 1 – Khởi tạo `identity-service` & CRUD User
**Ngày:** 2025-08-15 (UTC+7)

## 🎯 Mục tiêu
- Khởi tạo service `identity-service` bằng Spring Boot 3.5.4, Java 21, Maven.
- Kết nối MySQL và cấu hình context path.
- Tạo entity `User` và hoàn thiện CRUD API.

---

## 🛠 Công cụ & môi trường
- **Java:** 21
- **Spring Boot:** 3.5.4
- **Build tool:** Maven
- **IDE:** IntelliJ IDEA
- **Database:** MySQL (port 3307)
- **API test:** Postman / curl

---

## 🚀 Các bước thực hiện

### 1) Khởi tạo project với Spring Initializr
- **Group:** `com.jb`
- **Artifact:** `identity-service`
- **Packaging:** `jar`
- **Dependencies:**
  - Spring Web
  - Spring Data JPA
  - MySQL Driver

> Mở project bằng IntelliJ.

---

### 2) Chuyển `application.properties` ➜ `application.yaml` và cấu hình
```yaml
server:
  port: 8080
  servlet:
    context-path: /identity

spring:
  application:
    name: identity-service

  datasource:
    url: jdbc:mysql://localhost:3307/identity_service
    username: root
    password: 1234

  jpa:
    hibernate:
      ddl-auto: update
```
- `ddl-auto: update` giúp tạo bảng lần chạy đầu.
- Base URL khi gọi API: `http://localhost:8080/identity/...`

---

### 3) Tạo Entity `User`
```java
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;

    // getters & setters
}
```

---

### 4) Repository
```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {}
```

---

### 5) DTO – Request
**UserCreationRequest**
```java
public class UserCreationRequest {
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    // getters & setters
}
```

**UserUpdateRequest**
```java
public class UserUpdateRequest {
    private String password;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    // getters & setters
}
```

---

### 6) Service
```java
@Service
public class UserService {
    @Autowired
    UserRepository userRepository;

    public User createUser(UserCreationRequest req) {
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setDateOfBirth(req.getDateOfBirth());
        return userRepository.save(user);
    }

    public List<User> getAllUsers() { return userRepository.findAll(); }

    public User getUserById(String id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public void deleteUser(String id) { userRepository.deleteById(id); }

    public User updateUser(String id, UserUpdateRequest req) {
        User user = getUserById(id);
        user.setPassword(req.getPassword());
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setDateOfBirth(req.getDateOfBirth());
        return userRepository.save(user);
    }
}
```

---

### 7) Controller
```java
@RestController
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping
    public List<User> getAllUsers() { return userService.getAllUsers(); }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable String id) { return userService.getUserById(id); }

    @PostMapping
    public User createUser(@RequestBody UserCreationRequest request) {
        return userService.createUser(request);
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return "User deleted successfully.";
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        return userService.updateUser(id, request);
    }
}
```

---

## 🧪 Lệnh chạy & test API (Postman/curl)

### 1) Chạy ứng dụng
```bash
# Tại thư mục identity-service
mvn spring-boot:run
# (hoặc nếu có wrapper)
./mvnw spring-boot:run
```

### 2) Base URL
- Tất cả endpoint đi qua **context-path**:  
  `http://localhost:8080/identity`

### 3) Danh sách endpoint
- `GET /users` – Lấy tất cả users
- `GET /users/{id}` – Lấy user theo id
- `POST /users` – Tạo user
- `PUT /users/{id}` – Cập nhật user
- `DELETE /users/{id}` – Xóa user

### 4) Ví dụ gọi bằng **curl**
```bash
# Tạo user
curl -X POST http://localhost:8080/identity/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "jane",
    "password": "secret",
    "firstName": "Jane",
    "lastName": "Doe",
    "dateOfBirth": "2000-01-12"
  }'

# Lấy tất cả users
curl http://localhost:8080/identity/users

# Lấy user theo id
curl http://localhost:8080/identity/users/<USER_ID>

# Cập nhật user
curl -X PUT http://localhost:8080/identity/users/<USER_ID> \
  -H "Content-Type: application/json" \
  -d '{
    "password": "newpass",
    "firstName": "Jane",
    "lastName": "D",
    "dateOfBirth": "2000-01-12"
  }'

# Xóa user
curl -X DELETE http://localhost:8080/identity/users/<USER_ID>
```

### 5) Lưu ý format ngày
- `dateOfBirth` phải là `yyyy-MM-dd`  
  - **Đúng:** `"2000-01-12"`  
  - **Sai:** `"12-01-2000"`

---

## 🧠 Lưu ý & mẹo nhanh
- Lần chạy đầu nên bật `ddl-auto: update` để tự tạo bảng.
- URL MySQL trong dev: `jdbc:mysql://localhost:3307/identity_service`
- Nếu lỗi kết nối DB: kiểm tra **port 3307**, user/pass, và DB `identity_service` đã tồn tại chưa.

---

## ❌ Lỗi thường gặp & cách xử lý
- **`Cannot create PoolableConnectionFactory`**  
  *Nguyên nhân:* sai URL DB/user/pass hoặc MySQL chưa chạy.  
  *Cách fix:* kiểm tra `spring.datasource.*`, khởi động MySQL đúng port 3307.
- **`Failed to convert value of type 'java.lang.String' to required type 'LocalDate'`**  
  *Nguyên nhân:* sai format ngày.  
  *Cách fix:* dùng `yyyy-MM-dd` (vd: `2000-01-12`).

---

## 📌 Điều học được
- Khởi tạo dự án Spring Boot với Spring Initializr.
- Cấu hình datasource & context-path trong `application.yaml`.
- CRUD với Spring Data JPA + MySQL.
- Cách tổ chức Controller – Service – Repository.
