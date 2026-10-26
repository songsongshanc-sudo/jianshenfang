<template>
  <view class="page">
    <view class="hero">
      <text class="title">信息完善</text>
      <text class="text">完善姓名和人脸照片，即可完成注册。</text>
    </view>

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
          <text class="label">人脸照片</text>
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
    </view>

    <button class="primary" :class="{ wait: !canSubmit }" @click="submit">完成注册</button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { complete, facePreviewBase64, presignFace, saveSession, uploadFace, verifyFace } from "../../api/auth";

const name = ref("");
const preview = ref("");
const faceReady = ref(false);
const localTest = ref(false);
const submitting = ref(false);

function allowLocal() {
  // 测试逻辑：微信正式版为 false，开发版/体验版/网页调试为 true。见 开发文档/前端测试逻辑-待删除.md
  try {
    return uni.getAccountInfoSync().miniProgram.envVersion !== "release";
  } catch {
    return true;
  }
}

const canSubmit = computed(() => faceReady.value && String(name.value ?? "").trim().length > 0 && !submitting.value);

onLoad(() => {
  localTest.value = allowLocal();
  const tokenType = uni.getStorageSync("mpTokenType");
  if (tokenType === "ACCESS") {
    uni.switchTab({ url: "/pages/home/home" });
    return;
  }
  if (tokenType !== "REGISTER") {
    uni.redirectTo({ url: "/pages/login/login" });
  }
});

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
  faceReady.value = true;
  preview.value = await facePreviewBase64().catch(() => "");
  uni.showToast({ title: "人脸核验通过", icon: "none" });
}

async function submit() {
  if (!faceReady.value) {
    uni.showToast({ title: "请先完成人脸核验", icon: "none" });
    return;
  }
  const realName = String(name.value ?? "").trim();
  if (!realName) {
    uni.showToast({ title: "请填写姓名", icon: "none" });
    return;
  }
  submitting.value = true;
  try {
    const data = await complete(realName);
    saveSession(data);
    uni.showToast({ title: data.memberNo ? `会员编号 ${data.memberNo}` : "注册成功", icon: "none" });
    setTimeout(() => uni.switchTab({ url: "/pages/home/home" }), 500);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.page { min-height: 100vh; padding: 24rpx 24rpx 48rpx; box-sizing: border-box; }
.hero { padding: 24rpx 8rpx 32rpx; }
.title { display: block; font-size: 44rpx; font-weight: 800; }
.text { display: block; margin-top: 12rpx; color: #8a8f98; font-size: 26rpx; line-height: 1.6; }
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
.label { display: block; margin-bottom: 10rpx; font-size: 26rpx; font-weight: 700; }
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
input { background: #f8fafc; }
.ghost { margin-top: 16rpx; height: 64rpx; background: #fff7ed; color: #c2410c; font-size: 26rpx; }
.primary { margin-top: 24rpx; }
.primary.wait { opacity: 0.45; }
</style>
