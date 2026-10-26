<template>
  <view class="page">
    <view class="hero">
      <view class="hero-top">
        <view class="avatar">会</view>
        <view class="who">
          <text class="nick">手机号登录</text>
          <text class="sub">使用本机号一键登录，无需验证码</text>
          <text class="sub">或使用其他手机号验证码登录</text>
        </view>
      </view>
    </view>

    <view class="agree-line">
      <checkbox :checked="agreed" color="#ea580c" @click="agreed = !agreed" />
      <text @click="agreed = !agreed">我已阅读并同意</text>
      <text class="link" @click.stop="openPrivacy">《隐私保护指引》</text>
    </view>

    <button v-if="agreed" class="primary" open-type="getPhoneNumber" @getphonenumber="onWechatPhone">
      使用本机号登录
    </button>
    <button v-else class="primary wait" @click="needAgree">使用本机号登录</button>

    <view class="other" @click="showSms = !showSms">
      <text>其他号码登录</text>
      <text class="arrow">{{ showSms ? "收起" : "展开" }}</text>
    </view>

    <template v-if="showSms">
      <view class="card">
        <text class="label">手机号</text>
        <input v-model="phone" type="number" maxlength="11" placeholder="11 位手机号" />
        <text class="label">验证码</text>
        <view class="code-row">
          <input v-model="smsCode" type="number" maxlength="6" placeholder="验证码" />
          <button class="mini" size="mini" @click="sendCode">{{ sending ? "发送中" : "获取验证码" }}</button>
        </view>
      </view>
      <button class="primary" :class="{ wait: !canSmsLogin }" @click="submitSmsLogin">登录</button>
    </template>

    <view class="cancel" @click="leave">暂不登录，先看看</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { loginByPhone, loginBySms, privacyUrl, saveSession, sendSms, silentEnter, type LoginResult } from "../../api/auth";

const DEV_CODE = "246810"; // 测试逻辑：仅非微信正式版预填。见 开发文档/前端测试逻辑-待删除.md
const agreed = ref(false);
const showSms = ref(false);
const phone = ref("");
const smsCode = ref("");
const sending = ref(false);
const smsOk = ref(false);
const privacy = ref("");
const localTest = ref(false);

function allowLocal() {
  // 测试逻辑：微信正式版为 false，开发版/体验版/网页调试为 true。见 开发文档/前端测试逻辑-待删除.md
  try {
    return uni.getAccountInfoSync().miniProgram.envVersion !== "release";
  } catch {
    return true;
  }
}

const phoneText = computed(() => String(phone.value ?? "").replace(/\D/g, ""));
const codeText = computed(() => String(smsCode.value ?? "").replace(/\D/g, ""));
const canSmsLogin = computed(() => agreed.value && /^1\d{10}$/.test(phoneText.value) && codeText.value.length >= 4);

onLoad(async () => {
  localTest.value = allowLocal();
  if (localTest.value) {
    smsCode.value = DEV_CODE;
  }
  const entered = await silentEnter();
  if (entered?.tokenType === "ACCESS") {
    uni.switchTab({ url: "/pages/home/home" });
    return;
  }
  try {
    privacy.value = (await privacyUrl()).url || "";
  } catch {
    privacy.value = "";
  }
});

function needAgree() {
  uni.showToast({ title: "请先同意隐私保护指引", icon: "none" });
}

function openPrivacy() {
  if (!privacy.value) {
    uni.showToast({ title: "门店还未配置隐私指引", icon: "none" });
    return;
  }
  uni.navigateTo({ url: `/pages/webview/webview?url=${encodeURIComponent(privacy.value)}` });
}

function handleLogin(data: LoginResult) {
  saveSession(data);
  if (data.tokenType === "ACCESS") {
    uni.switchTab({ url: "/pages/home/home" });
    return;
  }
  uni.navigateTo({ url: "/pages/register/register" });
}

async function onWechatPhone(event: { detail: { code?: string } }) {
  if (!agreed.value) {
    uni.showToast({ title: "请先同意隐私保护指引", icon: "none" });
    return;
  }
  const code = event.detail.code;
  if (!code) {
    uni.showToast({ title: "没有取得手机号", icon: "none" });
    return;
  }
  const data = await loginByPhone(code);
  handleLogin(data);
}

async function sendCode() {
  if (!/^1\d{10}$/.test(phoneText.value)) {
    uni.showToast({ title: "请填写 11 位手机号", icon: "none" });
    return;
  }
  sending.value = true;
  try {
    await sendSms(phoneText.value);
    smsOk.value = true;
    uni.showToast({ title: "验证码已发送", icon: "none" });
  } finally {
    sending.value = false;
  }
}

async function submitSmsLogin() {
  if (!agreed.value) {
    uni.showToast({ title: "请先同意隐私保护指引", icon: "none" });
    return;
  }
  if (!/^1\d{10}$/.test(phoneText.value)) {
    uni.showToast({ title: "请填写 11 位手机号", icon: "none" });
    return;
  }
  if (codeText.value.length < 4) {
    uni.showToast({ title: "请填写验证码", icon: "none" });
    return;
  }
  if (!smsOk.value) {
    try {
      await sendSms(phoneText.value);
      smsOk.value = true;
    } catch {
      return;
    }
  }
  const data = await loginBySms(phoneText.value, codeText.value);
  handleLogin(data);
}

function leave() {
  uni.removeStorageSync("mpToken");
  uni.removeStorageSync("mpTokenType");
  uni.switchTab({ url: "/pages/home/home" });
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 24rpx 24rpx 48rpx; box-sizing: border-box; }
.hero {
  padding: 32rpx 28rpx;
  border-radius: 20rpx;
  background: linear-gradient(160deg, #1f2937 0%, #111827 70%);
  color: #fff;
}
.hero-top { display: flex; align-items: center; gap: 20rpx; }
.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #ea580c;
  color: #fff;
  text-align: center;
  line-height: 96rpx;
  font-size: 40rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.who { flex: 1; min-width: 0; }
.nick { display: block; font-size: 36rpx; font-weight: 700; }
.sub { display: block; margin-top: 6rpx; color: rgba(255, 255, 255, 0.72); font-size: 24rpx; line-height: 1.5; }
.agree-line { display: flex; align-items: center; margin-top: 28rpx; font-size: 26rpx; }
.link { margin-left: 4rpx; color: #ea580c; font-size: 26rpx; }
.primary { margin-top: 20rpx; }
.primary.wait { opacity: 0.45; }
.other { display: flex; align-items: center; justify-content: center; gap: 8rpx; margin-top: 28rpx; color: #ea580c; font-size: 26rpx; }
.arrow { color: #8a8f98; font-size: 24rpx; }
.card { margin-top: 24rpx; padding: 28rpx 24rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.label { display: block; margin: 20rpx 0 10rpx; font-size: 26rpx; font-weight: 700; }
.label:first-child { margin-top: 0; }
input { background: #f8fafc; }
.code-row { display: flex; gap: 12rpx; align-items: center; }
.code-row input { flex: 1; }
.mini { flex-shrink: 0; margin: 0; }
.cancel { margin-top: 32rpx; padding: 20rpx; text-align: center; color: #8a8f98; font-size: 26rpx; }
</style>
