export interface ApiBody<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

const baseURL = "http://127.0.0.1:8080";

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
