# Buổi 4 – Tích hợp Lombok & MapStruct, Tối ưu DTO/Entity và Service
**Ngày:** 2025-08-15 (UTC+7)

## 🎯 Mục tiêu
- Tích hợp **Lombok** để giảm code boilerplate (getter/setter, constructor...).
- Tích hợp **MapStruct** để mapping giữa Entity và DTO.
- Tạo `UserResponse` DTO cho dữ liệu trả về.
- Áp dụng mapper trong Service và Controller.
- Sử dụng `@RequiredArgsConstructor` + `@FieldDefaults` để bỏ `@Autowired`.

---

## 🛠 Công cụ & môi trường
- Java 21
- Spring Boot 3.5.4
- Maven
- Lombok 1.18.30
- MapStruct 1.5.5.Final
- Lombok MapStruct Binding 0.2.0

---

## 🚀 Các bước thực hiện

### 1) Thêm dependencies và cấu hình Maven
**`pom.xml`**
```xml
<properties>
    <java.version>21</java.version>
    <projectlombok-lombok.version>1.18.30</projectlombok-lombok.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
    <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${projectlombok-lombok.version}</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- Spring Boot -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>

        <!-- Maven Compiler -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>${java.version}</source>
                <target>${java.version}</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${projectlombok-lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>${lombok-mapstruct-binding.version}</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
                <compilerArgs>
                    <arg>-Amapstruct.suppressGeneratorTimestamp=true</arg>
                    <arg>-Amapstruct.defaultComponentModel=spring</arg>
                    <arg>-Amapstruct.verbose=true</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

### 2) Sử dụng Lombok cho DTO và Entity
Ví dụ **`UserCreationRequest.java`**:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @Size(min = 5, message = "USERNAME_INVALID")
    String username;

    @Size(min = 5, message = "PASSWORD_INVALID")
    String password;

    String firstName;
    String lastName;
    LocalDate dateOfBirth;
}
```

Ví dụ **`User.java`**:
```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;
    String username;
    String password;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
}
```

---

### 3) Tạo `UserResponse` DTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String username;
    String password;
    String firstName;
    String lastName;
    LocalDate dateOfBirth;
}
```

---

### 4) Tạo `UserMapper` với MapStruct
```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreationRequest request);
    UserResponse toUserResponse(User user);
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
```

---

### 5) Áp dụng mapper và Lombok trong Service
```java
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserRepository userRepository;
    UserMapper userMapper;

    public UserResponse createUser(UserCreationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        User user = userMapper.toUser(request);
        return userMapper.toUserResponse(userRepository.save(user));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toUserResponse)
                .toList();
    }

    public UserResponse getUserById(String id) {
        return userMapper.toUserResponse(
                userRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND))
        );
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public UserResponse updateUser(String id, UserUpdateRequest userUpdateRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        userMapper.updateUser(user, userUpdateRequest);
        return userMapper.toUserResponse(userRepository.save(user));
    }
}
```

---

### 6) Sửa Controller để trả `UserResponse`
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/users")
public class UserController {
    UserService userService;

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable String id) {
        return userService.getUserById(id);
    }

    @PostMapping
    public ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(userService.createUser(request));
        return response;
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return "User deleted successfully.";
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        return userService.updateUser(id, request);
    }
}
```

---

## 🧠 Lưu ý
- Lombok giúp giảm code lặp lại (getter, setter, constructor).
- MapStruct giúp mapping nhanh, tránh lỗi khi convert thủ công.
- `@RequiredArgsConstructor` + `@FieldDefaults(level=PRIVATE, makeFinal=TRUE)` giúp code gọn và bất biến dependency.
- DTO trả về (`UserResponse`) tách biệt với entity → tránh lộ field nhạy cảm.

---

## 📌 Điều học được
- Cách tích hợp và cấu hình Lombok + MapStruct trong Maven.
- Tối ưu code DTO, Entity bằng annotation.
- Sử dụng mapper để tách logic convert khỏi Service.
- Viết Service & Controller ngắn gọn hơn bằng constructor injection.