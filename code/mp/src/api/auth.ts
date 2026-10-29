import { request } from "./http";

const baseURL =
  // #ifdef H5
  "";
  // #endif
  // #ifndef H5
  "http://59.110.11.215";
  // #endif
  // 与 http.ts 同一规则。H5 用当前网址，微信小程序用测试云。

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
  try {
    const code = await loginCode();
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
  return new Promise((resolve, reject) => {
    uni.login({
      provider: "weixin",
      success(res) {
        if (res.code) {
          resolve(res.code);
          return;
        }
        reject(new Error("没有微信登录凭证"));
      },
      fail: reject,
    });
  });
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
