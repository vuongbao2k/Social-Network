# Buổi 30 — Deploy ứng dụng lên AWS EC2 với Docker

**Ngày:** 2025-08-24 (UTC+7)

---

## 🎯 Mục tiêu
- Khởi tạo máy chủ EC2 trên AWS.  
- Cài đặt Docker trên Ubuntu server.  
- Deploy MySQL + ứng dụng `identity-service` bằng Docker container.  
- Truy cập ứng dụng từ bên ngoài qua **Public IP**.  

---

## 🛠 Công cụ & môi trường
- AWS Account (Free Tier)  
- Ubuntu EC2 instance (t2.micro, 15GB storage)  
- Termius (SSH client, dùng free)  
- Docker CE, Docker Compose plugin  

---

## ⚙️ 1) Khởi tạo EC2 instance
1. Vào AWS Console → EC2 → **Launch instance**.  
2. OS: **Ubuntu (Free Tier)**.  
3. Instance type: **t2.micro (Free Tier)**.  
4. Storage: tăng lên **15 GiB** (mặc định 8).  
5. Key pair: tạo mới (RSA, `.pem`), tải file key về máy.  
6. Launch instance → sẽ thấy instance có **Public IP**.  

---

## 🔑 2) SSH vào EC2 bằng Termius
- Import key `.pem` vào **Keychain** của Termius.  
- Tạo host mới → Address = Public IP, Username = `ubuntu`, Auth = key vừa import.  
- Connect thành công sẽ thấy shell của Ubuntu.  

---

## 🐳 3) Cài đặt Docker
Chạy lần lượt các lệnh theo docs:

```bash
# Update & setup keyrings
sudo apt-get update
sudo apt-get install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# Add Docker repo
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update

# Install Docker CE
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

# Verify
sudo docker run hello-world
```

👉 Có thể chạy `htop` để xem CPU/RAM.  

---

## 🌐 4) Tạo Docker network
```bash
sudo docker network create jabao-network
```

---

## 🛢️ 5) Deploy MySQL container
```bash
sudo docker run --network jabao-network --name mysql \
  -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=1234 \
  -d mysql:latest
```

📌 Nếu chưa có image `mysql:latest` thì Docker sẽ tự pull.  

---

## 🔓 6) Mở port trong Security Group
- Vào **EC2 → Security → Security groups** của instance.  
- Edit inbound rules → Add rule:  
  - **3306 (MySQL)** → `0.0.0.0/0`  
  - **8080 (App)** → `0.0.0.0/0`  
- Save lại.  

---

## 🚀 7) Run ứng dụng từ Docker Hub
Ứng dụng đã build & push lên Docker Hub: `jabao/identity-service:0.9.0`.

```bash
sudo docker run --name MS-identity-service \
  --network jabao-network \
  -p 8080:8080 \
  -e DBMS_CONNECTION=jdbc:mysql://mysql:3306/identity_service \
  -e DBMS_USERNAME=root \
  -e DBMS_PASSWORD=1234 \
  -d jabao/identity-service:0.9.0
```

📌 Thêm `-d` để chạy dưới nền.  

---

## ▶️ 8) Quản lý container
- Xem container:
  ```bash
  sudo docker ps -a
  ```
- Start lại container đã stop:
  ```bash
  sudo docker start MS-identity-service
  ```

---

## 🧪 9) Test ứng dụng
- Gọi API bằng Postman:  
  ```
  http://<PUBLIC_IP>:8080/identity/users/my-info
  ```
- Lưu ý: thay `localhost` bằng **Public IP của EC2**.  

---

## 📌 Điều học được
- Cách tạo EC2 instance & SSH bằng keypair.  
- Cài Docker & chạy container trong môi trường cloud.  
- Cấu hình **network + security group** để kết nối MySQL và app.  
- Deploy ứng dụng trực tiếp từ Docker Hub lên cloud server.  
