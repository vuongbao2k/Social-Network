# Buổi 29 — Publish Docker Image lên Docker Hub

**Ngày:** 2025-08-24 (UTC+7)

---

## 🎯 Mục tiêu
- Tạo tài khoản Docker Hub và publish image backend lên registry public.  
- Hiểu quy trình **build → tag → push → pull**.  
- Có thể dùng image từ bất kỳ máy nào (không cần build lại source).

---

## 🛠 Công cụ & môi trường
- Docker CLI / Docker Desktop  
- Tài khoản Docker Hub (đã đăng ký & verify email)  

---

## ⚙️ 1) Đăng nhập Docker Hub
```bash
docker login
```
👉 Nhập username và password Docker Hub (`jabao` trong ví dụ này).

---

## 🧱 2) Build Docker image với tag đầy đủ
```bash
docker build -t jabao/identity-service:0.9.0 .
```

📌 Giải thích:
- `jabao` = Docker Hub username.  
- `identity-service` = repository (image name).  
- `0.9.0` = version tag (semantic version, có thể thay bằng `latest`).  

---

## 🔍 3) Kiểm tra image local
```bash
docker image ls
```

Sẽ thấy image `jabao/identity-service:0.9.0` vừa build.

---

## ☁️ 4) Push image lên Docker Hub
```bash
docker image push jabao/identity-service:0.9.0
```

→ Lên [https://hub.docker.com/repository/docker/jabao/identity-service](https://hub.docker.com/repository/docker/jabao/identity-service) sẽ thấy image.

---

## 🗑️ 5) Xoá image local để kiểm chứng
```bash
docker image rm jabao/identity-service:0.9.0
```

---

## 📥 6) Pull lại từ Docker Hub
```bash
docker pull jabao/identity-service:0.9.0
```

👉 Từ giờ, ở **bất kỳ máy nào**, chỉ cần pull image về là có thể chạy:
```bash
docker run --name MS-identity-service -p 8080:8080 \
  -e DBMS_CONNECTION=jdbc:mysql://mysql:3306/identity_service \
  -e DBMS_USERNAME=root \
  -e DBMS_PASSWORD=1234 \
  jabao/identity-service:0.9.0
```

---

## 📌 Điều học được
- Cách build Docker image với **namespace = Docker Hub username**.  
- Push & pull image từ Docker Hub.  
- Lợi ích: triển khai nhanh trên nhiều máy mà không cần build lại từ source.
