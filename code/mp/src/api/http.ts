import { clearSession, promptLogin } from "../utils/loginGate";

export interface ApiBody<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

const baseURL =
  // #ifdef H5
  "";
  // #endif
  // #ifndef H5
  "http://59.110.11.215";
  // #endif
  // 非 H5 连测试云。H5 和页面放在同一台服务器上，用当前网址。

export function request<T>(options: UniApp.RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync("mpToken") as string;
    const header = { ...(options.header || {}) };
    if (token) {
      header.Authorization = `Bearer ${token}`;
    }
    uni.request({
      ...options,
      header,
      url: baseURL + options.url,
      success(res) {
        const body = res.data as ApiBody<T>;
        if (!body || body.code !== 0) {
          if (body?.code === 40100) {
            const pages = getCurrentPages();
            const route = pages[pages.length - 1]?.route || "";
            if (route !== "pages/login/login") {
              clearSession();
            }
            promptLogin();
          } else {
            uni.showToast({ title: body?.message || "请求失败", icon: "none" });
          }
          reject(body);
          return;
        }
        resolve(body.data);
      },
      fail(err) {
        uni.showToast({ title: "网络异常", icon: "none" });
        reject(err);
      },
    });
  });
}
