# Buổi 28 — Đóng gói Docker Image & Chạy bằng Docker Network

**Ngày:** 2025-08-23 (UTC+7)

---

## 🎯 Mục tiêu
- Tạo **Dockerfile** kiểu **multi-stage build** (build bằng Maven, chạy bằng JDK runtime).  
- Build image backend: `identity-service`.  
- Chạy **MySQL** và **backend** trong **cùng Docker network** để **không phải dùng IP tĩnh**.  
- Sử dụng biến môi trường `DBMS_CONNECTION` (đã cấu hình ở Buổi 26) để trỏ DB.

---

## 🛠 Công cụ & môi trường
- Docker (Desktop/Engine)  
- Maven wrapper (nếu có)  
- Java 21, Spring Boot 3.5.x  
- MySQL Docker image

---

## 📦 1) Dockerfile (multi-stage)
> **Stage 1**: build bằng Maven + JDK 21  
> **Stage 2**: run bằng JRE (Amazon Corretto 21)

```dockerfile
# Stage 1: build
# Start with a Maven image that includes JDK 21
FROM maven:3.9.8-amazoncorretto-21 AS build

# Copy source code and pom.xml file to /app folder
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build source code with maven
RUN mvn package -DskipTests

# Stage 2: create image
# Start with Amazon Corretto JDK 21
FROM amazoncorretto:21.0.4

# Set working folder to /app and copy compiled file from above step
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

👉 Ưu điểm:
- Image run **nhẹ** hơn vì không mang theo Maven.  
- Build **tách biệt**: layer build và layer run.

> (Khuyến nghị) Tạo thêm `.dockerignore` để giảm context:
> ```
> target
> .idea
> .git
> .mvn
> mvnw
> mvnw.cmd
> **/*.iml
> ```

---

## 🧱 2) Build image
```bash
docker build -t identity-service:0.0.5 .
```

> Nếu dùng máy Mac M1/M2 cần build đa kiến trúc:  
> `docker buildx build --platform linux/amd64 -t identity-service:0.0.5 .`

---

## 🐬 3) (Tuỳ chọn, KHÔNG KHUYẾN NGHỊ) Chạy bằng IP MySQL
> Cách này **không ổn định** vì IP container có thể thay đổi.

```bash
docker run --name identity-service \
  -p 8080:8080 \
  -e DBMS_CONNECTION=jdbc:mysql://172.18.0.2:3306/identity_service \
  identity-service:0.0.5
```

---

## 🌐 4) Tạo Docker network & chạy MySQL + App
### 4.1 Tạo network dùng chung
```bash
docker network create jabao-network
# xem danh sách network:
docker network ls
```

### 4.2 Chạy MySQL trên network
```bash
docker run --network jabao-network --name mysql \
  -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=1234 \
  -d mysql:latest
```
> - Host port `3307` → Container port `3306` để không đụng MySQL host.  
> - (Khuyến nghị) Gắn volume để dữ liệu bền vững:
>   ```bash
>   docker run --network jabao-network --name mysql \
>     -p 3307:3306 \
>     -e MYSQL_ROOT_PASSWORD=1234 \
>     -v mysql_data:/var/lib/mysql \
>     -d mysql:latest
>   ```

### 4.3 Chạy **identity-service** trên cùng network
```bash
docker run --name MS-identity-service \
  --network jabao-network \
  -p 8080:8080 \
  -e DBMS_CONNECTION=jdbc:mysql://mysql:3306/identity_service \
  -e DBMS_USERNAME=root \
  -e DBMS_PASSWORD=1234 \
  identity-service:0.0.5
```

🔑 **Tại sao `jdbc:mysql://mysql:3306/...`?**  
Vì trong cùng network, **service name `mysql`** sẽ được Docker DNS resolve thành IP container MySQL → **không cần** IP tĩnh.

> Nếu cần bật profile prod:
> ```bash
> -e SPRING_PROFILES_ACTIVE=prod
> ```
> (Sẽ dùng `application-prod.yaml` cho JWT duration khác.)

---

## 🧪 5) Kiểm thử nhanh
- API base: `http://localhost:8080/identity`  
- Kiểm tra health bằng lệnh:
  ```bash
  docker logs -f MS-identity-service
  ```
- FE React (buổi 24) gọi:
  - `POST /auth/token`
  - `GET /users/my-info` (Bearer token)

---

## 💡 Mẹo & Best Practices
- **Healthcheck** (tuỳ chọn) trong Dockerfile:
  ```dockerfile
  HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/identity/actuator/health || exit 1
  ```
  (Cần bật Actuator trước.)
- **Giảm kích thước image**: dùng `eclipse-temurin:21-jre` hoặc `alpine` nếu tương thích.  
- **Docker Compose** tiện hơn: khai báo **mysql** + **app** + **network** trong 1 file (có thể làm ở buổi sau).

---

## 📌 Điều học được
- Cách viết **Dockerfile multi-stage** cho Spring Boot.  
- Sử dụng **Docker network** để service gọi nhau bằng **service name** thay vì IP.  
- Cách truyền **biến môi trường** (DBMS_CONNECTION/USERNAME/PASSWORD) khi chạy container.

