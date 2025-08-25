# Buổi 31 — Tích hợp OAuth2 (Google Login) với ReactJS

**Ngày:** 2025-08-24 (UTC+7)

---

## 🎯 Mục tiêu
- Hiểu cơ bản về các **grant types** trong OAuth2.  
- Tạo OAuth2 Client trên **Google Cloud Console**.  
- Tích hợp **Google Login** vào ứng dụng ReactJS.  
- Lấy thông tin user (email, name, picture) từ Google API.  

---

## 🛠 Công cụ & môi trường
- Google Cloud Console (OAuth2 credentials).  
- ReactJS (React Router, Material UI).  
- LocalStorage để lưu access token.  

---

## 📖 1) Tạo OAuth2 Client ID trên Google
1. Vào **Google Cloud Console** → tạo project.  
2. Vào **APIs & Services → Credentials → Create Credentials → OAuth Client ID**.  
3. Application type = **Web app**.  
4. Authorized origins:
   - `http://localhost:3000`  
5. Authorized redirect URIs:
   - `http://localhost:3000/authenticate`  
6. Bấm **Create** → download file JSON → copy thông tin vào file `configuration.js` trong frontend:

```js
export const OAuthConfig = {
  clientId: "850035654893-lft23uc6jkrs8u7l8t2svf8dfnbtpa4q.apps.googleusercontent.com",
  redirectUri: "http://localhost:3000/authenticate",
  authUri: "https://accounts.google.com/o/oauth2/auth",
};
```

---

## 📚 2) Một số grant type phổ biến
- **Password Grant** → App nhận username/password từ user, gửi cho Authorization Server để đổi lấy token. (Ít dùng vì không an toàn).  
- **Client Credentials** → App (chứ không phải user) authenticate bằng client_id/secret để lấy token (thường cho service-to-service).  
- **Implicit Grant** → Trình duyệt redirect đến Google Login, sau khi user consent sẽ trả token trực tiếp (dễ bị lộ token, nay ít dùng).  
- **Authorization Code Grant** → Redirect đến Google Login, trả về authorization code, client exchange code để lấy token (an toàn hơn implicit, hay dùng với backend).  

👉 Trong frontend demo này, ta sử dụng **Implicit Flow** (lấy access_token trực tiếp từ Google).  

---

## ⚛️ 3) Cấu trúc ReactJS (Frontend)
### a. Trang **Login.js**
- Thêm nút *Continue with Google*.  
- Khi click → redirect đến Google OAuth2 authorization endpoint.  

```jsx
const handleClick = () => {
  const callbackUrl = OAuthConfig.redirectUri;
  const authUrl = OAuthConfig.authUri;
  const googleClientId = OAuthConfig.clientId;

  const targetUrl = `${authUrl}?redirect_uri=${encodeURIComponent(
    callbackUrl
  )}&response_type=token&client_id=${googleClientId}&scope=openid%20email%20profile`;

  window.location.href = targetUrl;
};
```

### b. Trang **Authenticate.js**
- Parse `access_token` từ URL sau khi redirect về.  
- Lưu token vào LocalStorage và điều hướng về Home.  

```jsx
useEffect(() => {
  const accessTokenRegex = /access_token=([^&]+)/;
  const isMatch = window.location.href.match(accessTokenRegex);

  if (isMatch) {
    const accessToken = isMatch[1];
    setToken(accessToken); // Lưu vào localStorage
    setIsLoggedin(true);
  }
}, []);
```

### c. Trang **Home.js**
- Lấy access token từ LocalStorage.  
- Gọi Google API để lấy thông tin user.  

```jsx
const getUserDetails = async (accessToken) => {
  const response = await fetch(
    `https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token=${accessToken}`
  );
  const data = await response.json();
  setUserDetails(data);
};
```

Hiển thị avatar, tên, email:

```jsx
<img src={userDetails.picture} alt={userDetails.given_name} />
<h1>{userDetails.name}</h1>
<p>{userDetails.email}</p>
```

---

## 🧪 4) Demo flow
1. Truy cập `http://localhost:3000/login`.  
2. Click **Continue with Google** → chuyển đến Google Login.  
3. Login + consent → redirect về `http://localhost:3000/authenticate#access_token=...`.  
4. `Authenticate.js` parse token → lưu vào localStorage.  
5. `Home.js` dùng token gọi `https://www.googleapis.com/oauth2/v1/userinfo`.  
6. Hiển thị thông tin user.  

---

## 📌 Điều học được
- Quy trình OAuth2 implicit flow.  
- Tích hợp **Google OAuth2 login** vào ReactJS.  
- Sử dụng access token để lấy dữ liệu user từ Google API.  
- Cấu hình **Authorized Redirect URI** và **Origins** trên Google Cloud.  
