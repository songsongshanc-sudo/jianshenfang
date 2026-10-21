<template>
  <view class="page">
    <view class="hero">
      <view class="hero-top">
        <view class="avatar">会</view>
        <view class="who">
          <text class="nick">{{ mode === "invite" ? "绑定码登录" : "会员注册" }}</text>
          <text class="sub">{{ mode === "invite" ? "门店已录入的会员或教练，填写手机号和绑定码，不用注册" : "填写姓名和手机号，并完成人脸核验" }}</text>
          <text class="sub">{{ mode === "invite" ? "绑定后只用微信进入，绑定码随即失效" : "注册后不能修改" }}</text>
        </view>
      </view>
    </view>

    <view class="modes">
      <view class="mode" :class="{ on: mode === 'register' }" @click="mode = 'register'">新用户注册</view>
      <view class="mode" :class="{ on: mode === 'invite' }" @click="mode = 'invite'">绑定码登录</view>
    </view>

    <template v-if="mode === 'invite'">
      <view class="card">
        <text class="label tight">手机号</text>
        <input v-model="phone" type="number" maxlength="11" placeholder="11 位手机号" />
        <text class="label">绑定码</text>
        <input v-model="inviteCode" maxlength="8" placeholder="8 位绑定码" />
      </view>
      <button class="primary" :class="{ wait: !canClaim }" @click="submitInvite">登录并绑定微信</button>
      <view class="cancel" @click="leave">暂不登录，先看看</view>
    </template>

    <template v-else>
    <view class="card">
      <view class="face-row">
        <view class="face" @click="shoot">
          <image v-if="preview" class="preview" :src="preview" mode="aspectFill" />
          <view v-else class="face-empty">
            <text class="face-mark">拍</text>
            <text class="face-tip">拍摄正脸</text>
          </view>
        </view>
        <view class="face-side">
          <text class="label tight">人脸照片</text>
          <text class="hint">请正对镜头拍摄，不能从相册选择。</text>
          <view v-if="faceReady" class="ok-pill">
            <text class="ok-mark">✓</text>
            <text>人脸核查成功</text>
          </view>
          <button class="ghost" @click="shoot">{{ faceReady ? "重新拍摄" : "拍摄正脸" }}</button>
        </view>
      </view>
    </view>

    <view class="card">
      <text class="label">姓名</text>
      <input v-model="name" maxlength="32" placeholder="真实姓名" />
      <text class="label">手机号</text>
      <input v-model="phone" type="number" maxlength="11" placeholder="11 位手机号" />
      <text class="label">验证码</text>
      <view class="code-row">
        <input v-model="smsCode" type="number" maxlength="6" placeholder="验证码" />
        <button class="mini" size="mini" @click="sendCode">{{ sending ? "发送中" : "获取验证码" }}</button>
      </view>
    </view>

    <view class="agree-line">
      <checkbox :checked="agreed" color="#ea580c" @click="agreed = !agreed" />
      <text @click="agreed = !agreed">我已阅读并同意</text>
      <text class="link" @click.stop="openPrivacy">《隐私保护指引》</text>
    </view>

    <button class="primary" :class="{ wait: !canRegister }" @click="submit">注册</button>
    <view class="cancel" @click="leave">暂不注册，先看看</view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import {
  facePreviewBase64,
  presignFace,
  privacyUrl,
  claimMember,
  registerMember,
  saveSession,
  sendSms,
  silentEnter,
  uploadFace,
  verifyFace,
} from "../../api/auth";

const DEV_CODE = "246810"; // 测试逻辑：仅非微信正式版预填。见 开发文档/前端测试逻辑-待删除.md
const mode = ref<"register" | "invite">("register");
const agreed = ref(false);
const privacy = ref("");
const preview = ref("");
const faceReady = ref(false);
const name = ref("");
const phone = ref("");
const smsCode = ref("");
const inviteCode = ref("");
const smsOk = ref(false);
const sending = ref(false);
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
const inviteText = computed(() => String(inviteCode.value ?? "").trim().toUpperCase());
const canClaim = computed(() => /^1\d{10}$/.test(phoneText.value) && /^[A-Z2-9]{8}$/.test(inviteText.value));
const canRegister = computed(() => agreed.value
  && faceReady.value
  && String(name.value ?? "").trim().length > 0
  && /^1\d{10}$/.test(phoneText.value)
  && codeText.value.length >= 4);

onLoad(async (query) => {
  mode.value = query?.mode === "invite" ? "invite" : "register";
  localTest.value = allowLocal();
  if (localTest.value) {
    smsCode.value = DEV_CODE;
  }
  const entered = await silentEnter();
  if (entered?.tokenType === "ACCESS") {
    uni.switchTab({ url: "/pages/home/home" });
    return;
  }
  faceReady.value = !!entered?.faceReady;
  if (faceReady.value) {
    preview.value = await facePreviewBase64().catch(() => "");
  }
  try {
    privacy.value = (await privacyUrl()).url || "";
  } catch {
    privacy.value = "";
  }
});

function openPrivacy() {
  if (!privacy.value) {
    uni.showToast({ title: "门店还未配置隐私指引", icon: "none" });
    return;
  }
  uni.navigateTo({ url: `/pages/webview/webview?url=${encodeURIComponent(privacy.value)}` });
}

function leave() {
  uni.removeStorageSync("mpToken");
  uni.removeStorageSync("mpTokenType");
  uni.switchTab({ url: "/pages/home/home" });
}

async function shoot() {
  if (localTest.value) {
    // 测试逻辑：不打开相机，上传固定测试字节。见 开发文档/前端测试逻辑-待删除.md
    const bytes = new Uint8Array(64);
    bytes.fill(8);
    await finishFace(bytes.buffer);
    return;
  }
  uni.chooseImage({
    count: 1,
    sourceType: ["camera"],
    sizeType: ["compressed"],
    success(res) {
      const path = res.tempFilePaths[0];
      uni.getFileSystemManager().readFile({
        filePath: path,
        success(file) {
          void finishFace(file.data as ArrayBuffer);
        },
      });
    },
  });
}

async function finishFace(body: ArrayBuffer) {
  const key = await presignFace();
  await uploadFace(key.objectKey, body);
  const result = await verifyFace(key.objectKey);
  saveSession(result);
  faceReady.value = result.registerStatus === "FACE_OK" || result.tokenType === "REGISTER";
  preview.value = await facePreviewBase64().catch(() => "");
  uni.showToast({ title: "人脸核验通过", icon: "none" });
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

function blockReason() {
  if (!faceReady.value) {
    return "请先完成人脸核验";
  }
  if (!String(name.value ?? "").trim()) {
    return "请填写姓名";
  }
  if (!/^1\d{10}$/.test(phoneText.value)) {
    return "请填写 11 位手机号";
  }
  if (codeText.value.length < 4) {
    return "请填写验证码";
  }
  if (!agreed.value) {
    return "请先同意隐私保护指引";
  }
  return "";
}

async function submitInvite() {
  if (!/^1\d{10}$/.test(phoneText.value)) {
    uni.showToast({ title: "请填写 11 位手机号", icon: "none" });
    return;
  }
  if (!/^[A-Z2-9]{8}$/.test(inviteText.value)) {
    uni.showToast({ title: "请填写 8 位绑定码", icon: "none" });
    return;
  }
  const data = await claimMember({ phone: phoneText.value, inviteCode: inviteText.value });
  saveSession({ tokenType: "ACCESS", accessToken: data.accessToken });
  uni.showToast({ title: "登录成功", icon: "none" });
  setTimeout(() => uni.switchTab({ url: "/pages/home/home" }), 500);
}

async function submit() {
  const reason = blockReason();
  if (reason) {
    uni.showToast({ title: reason, icon: "none" });
    return;
  }
  await sendSms(phoneText.value);
  smsOk.value = true;
  const data = await registerMember({
    name: String(name.value).trim(),
    phone: phoneText.value,
    smsCode: codeText.value,
    privacyAgreed: true,
  });
  saveSession({ tokenType: "ACCESS", accessToken: data.accessToken });
  uni.showToast({ title: data.memberNo ? `会员编号 ${data.memberNo}` : "注册成功", icon: "none" });
  setTimeout(() => uni.switchTab({ url: "/pages/home/home" }), 500);
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
.modes { display: flex; gap: 12rpx; margin-top: 20rpx; }
.mode { flex: 1; height: 72rpx; line-height: 72rpx; text-align: center; border-radius: 16rpx; background: #fff; color: #57534e; border: 1px solid #eceff3; font-size: 26rpx; font-weight: 700; }
.mode.on { background: #1f2937; color: #fff; border-color: #1f2937; }
.card { margin-top: 24rpx; padding: 28rpx 24rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.face-row { display: flex; align-items: center; gap: 24rpx; }
.face {
  width: 200rpx;
  height: 240rpx;
  border-radius: 20rpx;
  overflow: hidden;
  background: #f8fafc;
  border: 2rpx dashed #fdba74;
  flex-shrink: 0;
}
.preview { width: 100%; height: 100%; }
.face-empty { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; }
.face-mark {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #fff7ed;
  color: #c2410c;
  text-align: center;
  line-height: 72rpx;
  font-size: 28rpx;
  font-weight: 800;
}
.face-tip { color: #8a8f98; font-size: 22rpx; }
.face-side { flex: 1; min-width: 0; }
.hint { display: block; margin-top: 8rpx; color: #8a8f98; font-size: 22rpx; line-height: 1.5; }
.ok-pill {
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 16rpx;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #ecfdf3;
  color: #15803d;
  font-size: 24rpx;
  font-weight: 700;
}
.ok-mark {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: #16a34a;
  color: #fff;
  text-align: center;
  line-height: 28rpx;
  font-size: 18rpx;
}
.label { display: block; margin: 20rpx 0 10rpx; font-size: 26rpx; font-weight: 700; }
.label.tight { margin-top: 0; }
input { background: #f8fafc; }
.code-row { display: flex; gap: 12rpx; align-items: center; }
.code-row input { flex: 1; }
.mini { flex-shrink: 0; margin: 0; }
.ghost { margin-top: 16rpx; height: 64rpx; background: #fff7ed; color: #c2410c; font-size: 26rpx; }
.agree-line { display: flex; align-items: center; margin-top: 28rpx; font-size: 26rpx; }
.link { margin-left: 4rpx; color: #ea580c; font-size: 26rpx; }
.primary { margin-top: 20rpx; }
.primary.wait { opacity: 0.45; }
.cancel { margin-top: 8rpx; padding: 20rpx; text-align: center; color: #8a8f98; font-size: 26rpx; }
</style>
