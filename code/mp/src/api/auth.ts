import { request } from "./http";

const baseURL =
  // #ifdef H5
  "";
  // #endif
  // #ifndef H5
  "http://59.110.11.215";
  // #endif
  // 与 http.ts 同一规则。见 开发文档/前端测试逻辑-待删除.md

export type EnterResult = {
  tokenType: string;
  accessToken: string;
  refreshToken: string | null;
  registerStatus: string;
  memberNo: string | null;
  faceReady: boolean;
};

export type LoginResult = {
  tokenType: string;
  accessToken: string;
  refreshToken: string | null;
  registerStatus: string;
  memberNo: string | null;
};

export function saveSession(data: { tokenType: string; accessToken: string }) {
  uni.setStorageSync("mpToken", data.accessToken);
  uni.setStorageSync("mpTokenType", data.tokenType);
}

export function clearSession() {
  uni.removeStorageSync("mpToken");
  uni.removeStorageSync("mpTokenType");
}

export async function silentEnter(): Promise<EnterResult | null> {
  const code = await loginCode();
  try {
    const data = await request<EnterResult>({
      url: "/api/mp/auth/enter",
      method: "POST",
      data: { code },
    });
    saveSession(data);
    return data;
  } catch {
    return null;
  }
}

function loginCode(): Promise<string> {
  // #ifdef H5
  return Promise.resolve(h5DeviceCode());
  // #endif
  // #ifndef H5
  return new Promise((resolve) => {
    uni.login({
      provider: "weixin",
      success(res) {
        resolve(res.code || `local${Date.now()}`);
      },
      fail() {
        resolve(`local${Date.now()}`);
      },
    });
  });
  // #endif
}

function h5DeviceCode() {
  const saved = uni.getStorageSync("h5DeviceCode") as string;
  if (saved && /^h5[a-z0-9]{8,40}$/.test(saved)) {
    return saved;
  }
  const next = `h5${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
  uni.setStorageSync("h5DeviceCode", next);
  return next;
}

export function privacyUrl() {
  return request<{ url: string }>({ url: "/api/mp/privacy", method: "GET" });
}

export function sendSms(phone: string) {
  return request<void>({ url: "/api/mp/auth/sms", method: "POST", data: { phone } });
}

/** 本机号登录：微信 getPhoneNumber 返回的 code。 */
export function loginByPhone(phoneCode: string) {
  return request<LoginResult>({ url: "/api/mp/auth/phone", method: "POST", data: { phoneCode } });
}

/** 其他号码登录：手机号 + 验证码。 */
export function loginBySms(phone: string, smsCode: string) {
  return request<LoginResult>({ url: "/api/mp/auth/login", method: "POST", data: { phone, smsCode } });
}

/** 信息完善：提交姓名，激活账号。 */
export function complete(name: string) {
  return request<LoginResult>({ url: "/api/mp/auth/complete", method: "POST", data: { name } });
}

/** 退出登录：服务端 revoke 当前 token。 */
export function logout() {
  return request<void>({ url: "/api/mp/auth/logout", method: "POST" });
}

export function presignFace() {
  return request<{ objectKey: string }>({
    url: "/api/mp/files/presign",
    method: "POST",
    data: { biz: "FACE", contentType: "image/jpeg" },
  });
}

export function uploadFace(objectKey: string, body: ArrayBuffer) {
  return request<void>({
    url: "/api/mp/files/upload",
    method: "POST",
    header: { "X-Object-Key": objectKey, "Content-Type": "application/octet-stream" },
    data: body,
  });
}

export function verifyFace(objectKey: string) {
  return request<{ tokenType: string; accessToken: string; registerStatus: string }>({
    url: "/api/mp/face",
    method: "POST",
    data: { objectKey },
  });
}

export function facePreviewBase64(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: baseURL + "/api/mp/face/preview",
      method: "GET",
      responseType: "arraybuffer",
      header: { Authorization: `Bearer ${uni.getStorageSync("mpToken")}` },
      success(res) {
        if (res.statusCode !== 200 || !res.data) {
          reject(new Error("preview"));
          return;
        }
        const bytes = res.data as ArrayBuffer;
        const base64 = uni.arrayBufferToBase64(bytes);
        resolve(`data:image/jpeg;base64,${base64}`);
      },
      fail: reject,
    });
  });
}
