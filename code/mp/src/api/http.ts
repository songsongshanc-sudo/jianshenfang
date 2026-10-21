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
  "http://127.0.0.1:8080";
  // #endif
  // 小程序开发者工具仍走本机。H5 和页面放在同一台服务器上，用当前网址。见 开发文档/前端测试逻辑-待删除.md

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
          uni.showToast({ title: body?.message || "请求失败", icon: "none" });
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
