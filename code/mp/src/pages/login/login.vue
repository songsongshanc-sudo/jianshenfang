<template>
  <view class="page">
    <view class="hero">
      <text class="mark">24</text>
      <text class="title">手机号登录</text>
      <text class="text">登录后需要拍摄正脸，人脸入库成功才算注册完成。</text>
    </view>
    <button class="primary" open-type="getPhoneNumber" @getphonenumber="onWechatPhone">微信手机号登录</button>
    <view v-if="localTest" class="local">
      <text class="label">本机测试</text>
      <input v-model="phone" class="input" type="number" maxlength="11" placeholder="11 位手机号" />
      <button class="ghost" @click="submitLocal">用这个手机号继续</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../api/http";

const phone = ref("");
const localTest = ref(false);
let sessionReady = false;

function allowLocal() {
  try {
    return uni.getAccountInfoSync().miniProgram.envVersion !== "release";
  } catch {
    return true;
  }
}

async function ensureSession() {
  if (sessionReady) return;
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
  const data = await request<{ sessionToken: string }>({
    url: "/api/mp/auth/session",
    method: "POST",
    data: { code },
  });
  uni.setStorageSync("mpToken", data.sessionToken);
  sessionReady = true;
}

async function loginWith(phoneCode: string) {
  await ensureSession();
  const data = await request<{ tokenType: string; accessToken: string; memberNo: string | null }>({
    url: "/api/mp/auth/phone",
    method: "POST",
    data: { phoneCode },
  });
  uni.setStorageSync("mpToken", data.accessToken);
  uni.setStorageSync("mpTokenType", data.tokenType);
  if (data.tokenType === "ACCESS") {
    uni.switchTab({ url: "/pages/home/home" });
    return;
  }
  uni.navigateTo({ url: "/pages/face/face" });
}

async function onWechatPhone(event: { detail: { code?: string } }) {
  const code = event.detail.code;
  if (!code) {
    uni.showToast({ title: localTest.value ? "请用下方测试手机号" : "没有取得手机号", icon: "none" });
    return;
  }
  await loginWith(code);
}

async function submitLocal() {
  if (!/^1\d{10}$/.test(phone.value)) {
    uni.showToast({ title: "请填写 11 位手机号", icon: "none" });
    return;
  }
  await loginWith(phone.value);
}

onLoad(async () => {
  localTest.value = allowLocal();
  try {
    await ensureSession();
  } catch {
    sessionReady = false;
  }
});
</script>

<style scoped>
.page { min-height: 100vh; padding: 48rpx 40rpx; background: #f6f3ee; }
.hero { padding: 48rpx 8rpx 64rpx; }
.mark { width: 88rpx; height: 88rpx; border-radius: 28rpx; background: #c2410c; color: #fff; text-align: center; line-height: 88rpx; font-weight: 800; }
.title { display: block; margin-top: 28rpx; font-size: 48rpx; font-weight: 700; }
.text { display: block; margin-top: 16rpx; color: #57534e; line-height: 1.6; }
.primary { margin-top: 12rpx; background: #c2410c; color: #fff; border-radius: 16rpx; }
.local { margin-top: 48rpx; padding: 28rpx; background: #fff; border-radius: 20rpx; }
.label { color: #78716c; font-size: 24rpx; }
.input { margin: 16rpx 0; padding: 16rpx 20rpx; background: #faf7f4; border-radius: 12rpx; }
.ghost { background: #fff7ed; color: #9a3412; }
</style>
