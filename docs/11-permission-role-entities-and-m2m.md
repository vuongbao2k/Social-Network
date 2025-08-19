# Buổi 11 — Thiết kế Permission/Role Entity & Chuyển User.roles sang Many-to-Many
**Ngày:** 2025-08-19 (UTC+7)

---

## 🎯 Mục tiêu
- Tách quyền ra thành 2 thực thể độc lập: **Permission** và **Role**.
- Cập nhật **User** để quan hệ **ManyToMany** với **Role** (không còn `Set<String>`).
- Bổ sung **Repository/Service/Controller/Mapper/DTO** cho Permission và Role.
- Tạm **comment** các đoạn logic đang phụ thuộc vào kiểu `Set<String>` roles (JWT scope, seeding đơn giản, v.v.) — sẽ xử lý chuẩn ở buổi sau.
- Lưu ý **xoá bảng `user` (và các bảng liên quan)** trong DB để JPA tự tạo lại do thay đổi cấu trúc.

---

## 🛠 Công cụ & môi trường
- Spring Boot 3.5.4, Java 21  
- Spring Data JPA/Hibernate (`ddl-auto=update` hoặc xoá bảng để JPA recreate)  
- Lombok, MapStruct  
- MySQL  
- Spring Security (đang dùng OAuth2 Resource Server + Method Security từ các buổi trước)

---

## ⚙️ 1) Entity & Quan hệ

### 1.1 `Permission` (id = `name`)
```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Permission {

    @Id
    String name;

    String description;
}
```

### 1.2 `Role` (id = `name`) — ManyToMany với Permission
```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Role {

    @Id
    String name;

    String description;

    @ManyToMany
    Set<Permission> permissions;
}
```

> Ghi chú: nếu muốn tên bảng liên kết rõ ràng, buổi sau có thể thêm `@JoinTable(name=..., joinColumns=..., inverseJoinColumns=...)`.

### 1.3 `User` — ManyToMany với Role  
(Thay `Set<String> roles` → `Set<Role> roles`)
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

    @ManyToMany
    Set<Role> roles;
}
```

> 🔄 **Database**: Vì đổi cấu trúc (ManyToMany), **xoá bảng `user` và các bảng mapping cũ** (ví dụ `user_roles`) rồi chạy lại để Hibernate tự tạo bảng mới. (Sau này sẽ chuyển qua migration bằng Flyway/Liquibase.)

---

## 🧩 2) Mapper điều chỉnh (tránh map vòng/không cần thiết)

### 2.1 `UserMapper` — **ignore field roles** khi map sang response (sẽ xử lý ở buổi sau)
```java
@Mapper(componentModel = "spring")
public interface UserMapper {

    // ... các mapping khác giữ nguyên

    @Mapping(target = "roles", ignore = true) // tạm ignore để tránh recursive/thiếu DTO role
    UserResponse toUserResponse(User user);
}
```

> Lý do: ta sẽ hiển thị roles trong `UserResponse` khi đã có DTO cho Role/Permission hoàn chỉnh; hiện tại ưu tiên ổn định build.

---

## 🧱 3) Permission Module

### 3.1 DTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PermissionRequest {
    String name;
    String description;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PermissionResponse {
    String name;
    String description;
}
```

### 3.2 Mapper
```java
@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);
    PermissionResponse toPermissionResponse(Permission permission);
}
```

### 3.3 Repository
```java
@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    // custom query nếu cần (giữ nguyên)
}
```

### 3.4 Service
```java
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionService {

    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;

    public PermissionResponse createPermission(PermissionRequest request) {
        var permission = permissionMapper.toPermission(request);
        return permissionMapper.toPermissionResponse(permissionRepository.save(permission));
    }

    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll()
                .stream()
                .map(permissionMapper::toPermissionResponse)
                .toList();
    }

    public void deletePermission(String id) {
        permissionRepository.deleteById(id);
    }
}
```

### 3.5 Controller
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/permissions")
public class PermissionController {

    PermissionService permissionService;

    @PostMapping
    public ApiResponse<PermissionResponse> createPermission(@RequestBody PermissionRequest request) {
        return ApiResponse.<PermissionResponse>builder()
                .result(permissionService.createPermission(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<PermissionResponse>> getAllPermissions() {
        return ApiResponse.<List<PermissionResponse>>builder()
                .result(permissionService.getAllPermissions())
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deletePermission(@PathVariable String id) {
        permissionService.deletePermission(id);
        return ApiResponse.<String>builder()
                .result("Permission deleted successfully")
                .build();
    }
}
```

---

## 🧱 4) Role Module

### 4.1 DTO  
- **Request** nhận `Set<String>` là danh sách **permission IDs** (chính là `Permission.name`).  
- **Response** trả `Set<PermissionResponse>`.

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleRequest {
    String name;
    String description;
    Set<String> permissions; // các permission id/name
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoleResponse {
    String name;
    String description;
    Set<PermissionResponse> permissions;
}
```

### 4.2 Mapper  
(Lưu ý: **ignore** permissions để xử lý thủ công trong Service.)
```java
@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "permissions", ignore = true)
    Role toRole(RoleRequest request);

    RoleResponse toRoleResponse(Role role);
}
```

### 4.3 Repository
```java
@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
}
```

### 4.4 Service
```java
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleService {

    RoleRepository roleRepository;
    RoleMapper roleMapper;
    PermissionRepository permissionRepository;

    public RoleResponse createRole(RoleRequest request) {
        var role = roleMapper.toRole(request);
        var permissions = permissionRepository.findAllById(request.getPermissions()); // theo IDs
        role.setPermissions(new HashSet<>(permissions));

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll()
                .stream()
                .map(roleMapper::toRoleResponse)
                .toList();
    }

    public void deleteRole(String id) {
        roleRepository.deleteById(id);
    }
}
```

### 4.5 Controller
```java
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/roles")
public class RoleController {

    RoleService roleService;

    @PostMapping
    public ApiResponse<RoleResponse> createRole(@RequestBody RoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.createRole(request))
                .build();
    }

    @GetMapping
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.<List<RoleResponse>>builder()
                .result(roleService.getAllRoles())
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteRole(@PathVariable String id) {
        roleService.deleteRole(id);
        return ApiResponse.<String>builder()
                .result("Role deleted successfully")
                .build();
    }
}
```

---

## 🧪 5) Test nhanh

### 5.1 Tạo permission
```bash
curl -X POST http://localhost:8080/identity/permissions \
  -H "Content-Type: application/json" \
  -d '{"name":"USER_READ","description":"Read user data"}'
```

### 5.2 Tạo role kèm permissions
```bash
curl -X POST http://localhost:8080/identity/roles \
  -H "Content-Type: application/json" \
  -d '{"name":"ADMIN","description":"Administrator","permissions":["USER_READ"]}'
```

### 5.3 Liệt kê roles/permissions
```bash
curl http://localhost:8080/identity/permissions
curl http://localhost:8080/identity/roles
```

> Khi xóa: đảm bảo ràng buộc quan hệ — **không** xóa `Permission X` nếu còn `Role` đang chứa `Permission X`. Tương tự cho `Role` đang được `User` tham chiếu.

---

## 🧩 Giải thích logic ngắn gọn
- **Tách quyền**: `Permission` là đơn vị nhỏ nhất; `Role` là tập hợp các `Permission`.  
- **User** nắm **nhiều Role** (ManyToMany). Điều này linh hoạt và chuẩn hơn `Set<String>`.  
- **Mapper ignore** ở `UserMapper.toUserResponse` để tránh vòng lặp hoặc leak thông tin khi ta chưa chuẩn hóa DTO Role/Permission trong user (sẽ hoàn thiện buổi sau).  
- **RoleService**: nhận `Set<String>` permission ids → load từ DB → set vào `Role`.  
- **DB recreate**: Cấu trúc quan hệ thay đổi ⇒ xoá bảng cũ để JPA tạo bảng liên kết ManyToMany.

---

## ⚠️ Tạm thời COMMENT / TODO
- **JWT scope/buildScope**: các chỗ đang loop `Set<String>` → **tạm comment** hoặc sửa tạm cho build từ `role.getName()` (khuyến nghị: comment để buổi sau chuẩn hóa mapping Role→Authorities).  
- **Seeder admin**: nếu trước đây tạo admin bằng `Set<String>` → cần cập nhật tạo `Role` entity trước, rồi gán vào `User`. (Buổi sau sẽ làm chuẩn.)  
- **Method Security**: nếu rule đang dựa vào `ROLE_` từ JWT scope cũ, có thể ảnh hưởng. Buổi sau ta đồng bộ **authority claim** dựa trên Role entity.

---

## 📌 Điều học được
- Mô hình **RBAC** (Role-Based Access Control) với **Permission** & **Role** là bền vững và mở rộng dễ hơn so với chuỗi ký tự.  
- Cách tổ chức module CRUD cho Permission/Role theo **DTO + Mapper + Service + Controller**.  
- Lưu ý **ràng buộc quan hệ** khi xóa dữ liệu ManyToMany.  
- Kinh nghiệm **migration**: khi đổi cấu trúc entity, cần dọn DB/migration tương ứng.

---

## 🗺️ Hướng phát triển tiếp theo (TODO)
- Chuẩn hóa **JWT claims**: đưa **roles & permissions** vào token (ví dụ `scope`/`roles`/`authorities`) từ **Role entity**.  
- Cập nhật **JwtAuthenticationConverter** để map authorities từ claims mới.  
- Cập nhật **ApplicationInitConfig**: seed `Permission` → `Role` → `Admin User`.  
- Cập nhật **UserResponse** để hiển thị roles gọn (ví dụ: `Set<String> roleNames`) hoặc lồng DTO đơn giản.
