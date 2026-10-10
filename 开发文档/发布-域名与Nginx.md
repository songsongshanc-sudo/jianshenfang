# 发布：域名与 Nginx

← 返回 [发布文档](发布文档.md)

本文面向**第一次配服务器**的人：先看「这些词是什么」，再按顺序做。

相关：[环境变量](发布-服务器环境变量.md) · [打包与部署](发布-打包与部署.md) · [闸机](发布-闸机.md) · [上线申请清单](上线申请清单.md)

---

## 0. 这些词是什么（先看再动手）

| 词 | 白话 |
| --- | --- |
| **ECS / 云服务器** | 阿里云租给你的一台 Linux 电脑，7×24 开机。后面说的「服务器」就是它。 |
| **公网 IP** | 外网访问这台机器的地址，形如 `47.98.xxx.xxx`。域名要解析到它。见 [环境变量 §公网IP](发布-服务器环境变量.md#1-ssh-与公网-ip)。 |
| **域名** | 给人记的名字，如 `example.com`。小程序、支付要求用域名 + HTTPS，不能只用 IP。 |
| **DNS 解析** | 告诉全世界：访问 `api.example.com` 时去找哪台机器的公网 IP。 |
| **HTTPS / SSL 证书** | 给网站加锁。微信小程序、支付回调只认 `https://`，不认 `http://`。 |
| **Nginx** | 装在服务器上的「门卫」程序：外网访问 443 端口先到它；它再把请求转给后面的 Java（8080）或管理端静态文件。 |
| **反代（反向代理）** | Nginx 收到 `https://api.../xxx` 后，在本机转给 `http://127.0.0.1:8080/xxx`。浏览器只看见域名，看不到 8080。 |
| **静态托管** | 管理端 `npm run build` 后是一堆 html/js/css。Nginx 直接读磁盘上的文件返回，不必再跑 Node。 |
| **wss** | WebSocket 的加密版，闸机长连接用。和 HTTPS 共用同一张证书、同一个域名。 |

**推荐拓扑（买一个主域名即可）：**

```
用户 / 小程序 / 闸机
        │
        ▼
  https://api.example.com      ←── Nginx（443 + 证书）
        │
        ├── 普通接口、支付回调 ──► Java（本机 8080）
        └── /ws/gate（闸机）   ──► 同一个 Java（WebSocket）

浏览器打开管理后台
        │
        ▼
  https://admin.example.com    ←── 同一台机上的 Nginx
        ├── /api/...           ──► 同一个 Java（8080）
        └── 其它路径           ──► /var/www/gym-admin/ 里的静态文件
```

`api` 和 `admin` 都是**主机名**，解析到**同一台 ECS 的公网 IP**，不是两台服务器。

---

## 1. 买域名

| 去哪办 | 说明 |
| --- | --- |
| [阿里云万网](https://wanwang.aliyun.com/)（推荐） | 和 ECS、备案、证书同云最省事 |
| [腾讯云域名](https://cloud.tencent.com/product/domain) | 也可；备案尽量和云服务器同一家 |

操作要点：

1. 搜索想要的名字（如 `xxxgym.com`），结算购买。  
2. 完成**实名认证**（控制台会提示）。  
3. 记下你的主域名，下文用 **`example.com`** 代替——全文请换成你自己的。

买完后还不能给小程序用：必须先完成下面的 DNS → 备案 → SSL → Nginx。

---

## 2. DNS 解析（把域名指到服务器）

先拿到 ECS [公网 IP](发布-服务器环境变量.md#1-ssh-与公网-ip)。

### 阿里云控制台操作

1. 打开 [云解析 DNS](https://dns.console.aliyun.com/) → 找到你的域名 → **解析设置**。  
2. 点 **添加记录**，加两条（管理端不要可只加第一条）：

| 主机记录 | 记录类型 | 记录值 | 用途 |
| --- | --- | --- | --- |
| `api` | A | 你的公网 IP | 后端、小程序、支付、闸机 → 完整名 `api.example.com` |
| `admin` | A | **同一个**公网 IP | 管理后台 → `admin.example.com` |

3. 保存后等几分钟。本机 PowerShell 检查：

```powershell
nslookup api.example.com
nslookup admin.example.com
```

返回的地址应等于 ECS 公网 IP。若仍是旧值，多等一会或换网络再查。

**备案注意：** 中国大陆 ECS 对外提供网站，域名必须做 [ICP 备案](https://beian.aliyun.com/)。备案未通过前，小程序正式环境不能用这个域名；可先在服务器上把 Nginx/Java 装好自测。

---

## 3. 申请并下载 SSL 证书

1. 打开 [数字证书管理服务](https://yundun.console.aliyun.com/?p=cas)（阿里云 SSL）。  
2. 申请免费证书或付费证书，绑定：  
   - 至少：`api.example.com`  
   - 若用管理端子域名：再签 `admin.example.com`，或一张 **`*.example.com` 通配符**（一张搞定两个主机名）。  
3. 证书签发后点 **下载** → 选 **Nginx** 格式。解压后常见两个文件：  
   - `xxx.pem`（证书）  
   - `xxx.key`（私钥）  

4. 用 scp 传到服务器（IP、文件名换成你的；本机 PowerShell）：

```powershell
scp "C:\Users\你的用户名\Downloads\证书目录\xxx.pem" root@公网IP:/etc/nginx/ssl/api.example.com.pem
scp "C:\Users\你的用户名\Downloads\证书目录\xxx.key" root@公网IP:/etc/nginx/ssl/api.example.com.key
```

若服务器上还没有目录，先 SSH 登录执行：

```bash
sudo mkdir -p /etc/nginx/ssl
sudo chmod 700 /etc/nginx/ssl
```

管理端若是单独一张证书，同样放到例如：

- `/etc/nginx/ssl/admin.example.com.pem`  
- `/etc/nginx/ssl/admin.example.com.key`

---

## 4. 安全组要放行端口

阿里云 ECS → 实例 → **安全组** → 入方向规则，至少放行：

| 端口 | 用途 |
| --- | --- |
| 22 | SSH 登录（可限制仅你公司出口 IP） |
| 80 | HTTP（用来跳转到 HTTPS，建议开） |
| 443 | HTTPS / WSS（必须开） |

**不要**把 8080 对公网开放。外网只进 Nginx 的 443；Java 只听本机 `127.0.0.1:8080`。

---

## 5. 在服务器上安装 Nginx

以下以 **Ubuntu / Debian** 为例（命令一条一条执行）。若是 Alibaba Cloud Linux / CentOS，把 `apt` 换成 `yum`/`dnf`，包名一般仍是 `nginx`。

```bash
sudo apt update
sudo apt install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx
sudo nginx -v
```

浏览器访问 `http://公网IP`，若出现 Nginx 欢迎页，说明安装成功（安全组 80 已放行）。

---

## 6. 写 Nginx 配置（详细可抄）

### 6.1 准备管理端目录

管理端文件稍后按 [打包与部署 §admin](发布-打包与部署.md#admin) 上传；先建空目录：

```bash
sudo mkdir -p /var/www/gym-admin
```

### 6.2 新建站点配置

```bash
sudo nano /etc/nginx/conf.d/gym.conf
```

把下面整段贴进去，**三处替换**：

- `example.com` → 你的主域名  
- `ssl` 两行路径 → 你实际上传的证书文件  
- 若暂时没有 `admin`，可先只保留第一个 `server { ... }`（api 那一段）

```nginx
# ---------- 接口域名：小程序 / 支付 / 闸机 ----------
server {
    listen 443 ssl;
    server_name api.example.com;

    ssl_certificate     /etc/nginx/ssl/api.example.com.pem;
    ssl_certificate_key /etc/nginx/ssl/api.example.com.key;

    # 上传图片等可能较大，按需调整
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 闸机 WebSocket（/ws/gate）必须有下面三行
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}

# ---------- 管理后台子域名（可选但推荐）----------
server {
    listen 443 ssl;
    server_name admin.example.com;

    ssl_certificate     /etc/nginx/ssl/admin.example.com.pem;
    ssl_certificate_key /etc/nginx/ssl/admin.example.com.key;
    # 若用了通配符证书，上面两行也可写成和 api 相同的 pem/key

    root /var/www/gym-admin;
    index index.html;
    client_max_body_size 20m;

    # 浏览器请求 /api/... 转到 Java（管理端 baseURL 是 "/"，不用改代码）
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 前端路由（刷新页面不 404）
    location / {
        try_files $uri $uri/ /index.html;
    }
}

# ---------- 80 跳到 HTTPS（可选）----------
server {
    listen 80;
    server_name api.example.com admin.example.com;
    return 301 https://$host$request_uri;
}
```

保存：nano 里 `Ctrl+O` 回车，再 `Ctrl+X`。

### 6.3 检查并重载

```bash
sudo nginx -t
```

必须看到 `syntax is ok` 和 `test is successful`，再执行：

```bash
sudo systemctl reload nginx
```

若报错证书路径不对：用 `ls -l /etc/nginx/ssl/` 核对文件名后改配置再 `nginx -t`。

### 6.4 本机怎么验证

1. Java 已按 [环境变量 + systemd](发布-服务器环境变量.md) 跑起来。  
2. 浏览器打开：`https://api.example.com/api/health`（或你们实际的健康检查路径）。  
3. 管理端 dist 上传后打开：`https://admin.example.com`。  
4. 闸机：`wss://api.example.com/ws/gate`（见 [发布-闸机](发布-闸机.md)）。

常见失败：

| 现象 | 先查 |
| --- | --- |
| 浏览器证书警告 | 证书域名是否匹配；是否用了错的 pem/key |
| 502 Bad Gateway | Java 没起来：`sudo systemctl status gym-server` |
| 管理端能开但登录/接口失败 | `admin` 的 `location /api/` 是否配了；安全组是否误关 443 |
| 闸机连不上 | 是否走了 `wss://`；api 的 `Upgrade`/`Connection` 是否在 |
| DNS 不对 | `nslookup` 是否已指向当前公网 IP |

---

## 7. 管理端用子域名时还要做什么

**不用改业务代码。** `code/admin/src/api/http.ts` 里 `baseURL` 是 `"/"`，浏览器会请求 `https://admin.example.com/api/...`，由上面 Nginx 转到 Java。

清单：

1. DNS 已加 `admin` A 记录。  
2. SSL 已覆盖 `admin.example.com`。  
3. Nginx 第二个 `server` 已启用，`root` 指向 `/var/www/gym-admin`。  
4. 高德 Key 白名单加上 `https://admin.example.com`，见 [OSS与高德](发布-OSS与高德.md#高德地图)。  
5. 按 [打包与部署](发布-打包与部署.md#admin) 上传 `dist`。

也可以不分子域名：只用 `api.example.com`，把管理端静态文件和 `/api` 反代写在同一个 `server` 里（需要时再改，本期推荐分开更清晰）。

---

## 8. 和别的文档怎么衔接

| 你做到哪一步 | 下一篇 |
| --- | --- |
| 域名解析好了、证书下好了 | 本文 §5～§6 装 Nginx |
| Nginx 已 reload | [服务器环境变量](发布-服务器环境变量.md)：装 JDK/MySQL/Redis、写 env、起 Java |
| Java 已起来 | [打包与部署](发布-打包与部署.md)：传 jar、传 admin dist |
| 外网 HTTPS 通了 | [微信与支付](发布-微信与支付.md) 填合法域名与支付回调 |
