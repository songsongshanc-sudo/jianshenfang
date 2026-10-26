# 发布：OSS 与高德

← 返回 [发布文档](发布文档.md)

密钥写入 [服务器环境变量](发布-服务器环境变量.md)。人脸照片走服务器私有目录，和下面的公开图不是同一个文件夹。

公开图不买对象存储。轮播、门店封面、到店指引、教练照片、课程封面、器械图、富文本图片都写在云服务器磁盘上。一家店大约几十张图，放本地即可。器械教程视频不放这里，发到微信视频号，小程序只保存编号并跳转播放。公开文件超过约 10GB 时，再考虑 OSS。

---

## 公开图片（云服务器本地）

上传接口是 `POST /api/admin/files/images`。文件目录由 `PUBLIC_FILE_DIR` 决定，正式环境用 `/var/lib/gym/public`。从公网打开管理端时，返回的网址是当前网站的 `/api/uploads/...`，不再使用 `127.0.0.1`。`PUBLIC_FILE_BASE_URL` 写成 `https://api.xz100w.com` 之后，新上传的图片用这个域名。

| 变量 | 正式环境 |
| --- | --- |
| `PUBLIC_FILE_DIR` | `/var/lib/gym/public` |
| `PUBLIC_FILE_BASE_URL` | 现在用公网 IP 时填 `http://59.110.11.215`。域名和证书就绪后改成 `https://api.xz100w.com` |

改 `PUBLIC_FILE_BASE_URL` 后要 `sudo systemctl restart gym-server`。已经上传的图片网址不会自动改，换域名后需要重新上传，或接受旧地址。

本机测试不设这两项：文件在 `code/server/data/public`，网址是 `http://127.0.0.1:8080/uploads/...`。

小程序 downloadFile 使用接口域名即可，不必再配一个对象存储域名。见 [微信与支付](发布-微信与支付.md)。

`/uploads/` 已经写在 [现在能做与域名下来再做](现在能做与域名下来再做.md) 的 Nginx 命令里。接口域名那一份把所有路径转给 Java，图片也会走到后端。

---

## 高德地图（管理端）

用途：门店坐标选点。Key 打进前端构建产物，**不要**写进后端 env。

1. [高德开放平台](https://lbs.amap.com/) 申请 Web 端（JS API）Key。  
2. 本地：`code/admin/.env.local`（勿提交 Git）：

```env
VITE_AMAP_KEY=你的Key
VITE_AMAP_SECURITY_CODE=你的安全密钥
```

3. 正式包：同名变量在构建机环境或 `.env.production` 中提供，再执行 [`npm run build`](发布-打包与部署.md#admin)。改 Key 必须重打 admin。  
4. Nginx 托管 `dist` 即可，无需后端再配高德。

Key 与安全密钥勿贴到聊天或公开仓库。
