# 发布：OSS 与高德

← 返回 [发布文档](发布文档.md)

密钥写入 [服务器环境变量](发布-服务器环境变量.md)。人脸照片走服务器私有目录，**不进**公开 OSS。

---

## 阿里云 OSS（公开图）

用途：教练头像、场地封面、小程序轮播等。上传经 `POST /api/admin/uploads`。

1. [开通 OSS](https://www.aliyun.com/product/oss)，建 Bucket（建议与 ECS 同地域）。  
2. Bucket 设为**公共读、私有写**，或配合 CDN/自定义域名。  
3. 控制台看 Endpoint、Bucket、访问域名。  
4. [AccessKey](https://ram.console.aliyun.com/)（建议 RAM 子账号，最小权限）→ `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`。  
5. 环境变量示例：

| 变量 | 现为（yml） | 改成 |
| --- | --- | --- |
| `OSS_ENDPOINT` | （空） | 如 `https://oss-cn-hangzhou.aliyuncs.com`（与 [env 模板](发布-服务器环境变量.md#2-新建-etcgymgym-serverenv) 一致） |
| `OSS_BUCKET` | （空） | Bucket 名 |
| `OSS_ACCESS_KEY_ID` | （空） | RAM AK |
| `OSS_ACCESS_KEY_SECRET` | （空） | RAM SK |
| `OSS_PUBLIC_BASE_URL` | （空） | `https://你的Bucket域名` 或 CDN 域名 |

对象键前缀在代码里固定为 `public/...`，无需另配。

`OSS_PUBLIC_BASE_URL` 须加入小程序 **downloadFile 合法域名**，见 [微信与支付](发布-微信与支付.md)。

未配 OSS 时管理端上传会失败或不可用；不要用假域名凑数。

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
