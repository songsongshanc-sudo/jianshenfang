import { request } from "./http";

const baseURL = "http://127.0.0.1:8080"; // 测试地址，与 http.ts 同一处，正式版一起改。见 开发文档/前端测试逻辑-待删除.md

export type EnterResult = {
  tokenType: string;
  accessToken: string;
  refreshToken: string | null;
  registerStatus: string;
  memberNo: string | null;
  faceReady: boolean;
};

export function saveSession(data: { tokenType: string; accessToken: string }) {
  uni.setStorageSync("mpToken", data.accessToken);
  uni.setStorageSync("mpTokenType", data.tokenType);
}

export async function silentEnter(): Promise<EnterResult | null> {
  const code = await new Promise<string>((resolve) => {
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

export function privacyUrl() {
  return request<{ url: string }>({ url: "/api/mp/privacy", method: "GET" });
}

export function sendSms(phone: string) {
  return request<void>({ url: "/api/mp/auth/sms", method: "POST", data: { phone } });
}

export function claimMember(body: { phone: string; inviteCode: string }) {
  return request<{ tokenType: string; accessToken: string; memberNo: string | null }>({
    url: "/api/mp/auth/claim",
    method: "POST",
    data: body,
  });
}

export function registerMember(body: { name: string; phone: string; smsCode: string; privacyAgreed: boolean }) {
  return request<{ tokenType: string; accessToken: string; memberNo: string | null }>({
    url: "/api/mp/auth/register",
    method: "POST",
    data: body,
  });
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
