<template>
  <view class="page">
    <text class="title">{{ replacing ? "更换正脸" : "拍摄正脸" }}</text>
    <text class="text">{{ replacing ? "请正对镜头重新拍摄。不能从相册选择。通过后会同步到门店闸机。" : "请正对镜头。不能从相册选择。入库成功后才会得到会员编号。" }}</text>
    <button class="primary" @click="shoot">打开相机</button>
    <button v-if="localTest" class="ghost" @click="submitDemo">本机测试照片</button>
    <text v-if="reason" class="reason">{{ reason }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { request } from "../../api/http";

const reason = ref("");
const localTest = ref(false);
const replacing = ref(false);

function allowLocal() {
  try {
    return uni.getAccountInfoSync().miniProgram.envVersion !== "release";
  } catch {
    return true;
  }
}

async function presign() {
  const data = await request<{ objectKey: string }>({
    url: "/api/mp/files/presign",
    method: "POST",
    data: { biz: "FACE", contentType: "image/jpeg" },
  });
  return data.objectKey;
}

async function upload(objectKey: string, body: ArrayBuffer) {
  await request<void>({
    url: "/api/mp/files/upload",
    method: "POST",
    header: { "X-Object-Key": objectKey, "Content-Type": "application/octet-stream" },
    data: body,
  });
}

async function finish(objectKey: string) {
  try {
    if (replacing.value) {
      await request<void>({
        url: "/api/mp/face/replace",
        method: "POST",
        data: { objectKey },
      });
      uni.showToast({ title: "人脸已更新", icon: "none" });
      setTimeout(() => uni.navigateBack(), 600);
      return;
    }
    const data = await request<{ accessToken: string; memberNo: string }>({
      url: "/api/mp/face",
      method: "POST",
      data: { objectKey },
    });
    uni.setStorageSync("mpToken", data.accessToken);
    uni.setStorageSync("mpTokenType", "ACCESS");
    uni.showToast({ title: `会员编号 ${data.memberNo}`, icon: "none" });
    setTimeout(() => uni.switchTab({ url: "/pages/home/home" }), 600);
  } catch (error) {
    const body = error as { message?: string };
    reason.value = body?.message || "请重新拍摄";
  }
}

function shoot() {
  uni.chooseImage({
    count: 1,
    sourceType: ["camera"],
    sizeType: ["compressed"],
    success(res) {
      const path = res.tempFilePaths[0];
      uni.getFileSystemManager().readFile({
        filePath: path,
        success: async (file) => {
          const objectKey = await presign();
          const body = file.data as ArrayBuffer;
          await upload(objectKey, body);
          await finish(objectKey);
        },
      });
    },
  });
}

async function submitDemo() {
  const bytes = new Uint8Array(64);
  bytes.fill(8);
  const objectKey = await presign();
  await upload(objectKey, bytes.buffer);
  await finish(objectKey);
}

onLoad((query) => {
  localTest.value = allowLocal();
  replacing.value = String(query?.mode || "") === "replace";
  const tokenType = uni.getStorageSync("mpTokenType");
  if (replacing.value) {
    if (tokenType !== "ACCESS") {
      uni.redirectTo({ url: "/pages/login/login" });
    }
    return;
  }
  if (tokenType !== "FACE") {
    uni.redirectTo({ url: "/pages/login/login" });
  }
});
</script>

<style scoped>
.page { min-height: 100vh; padding: 64rpx 40rpx; background: #1c1917; color: #fff; }
.title { display: block; font-size: 48rpx; font-weight: 700; }
.text { display: block; margin: 20rpx 0 48rpx; color: #d6d3d1; line-height: 1.6; }
.primary { background: #c2410c; color: #fff; border-radius: 16rpx; }
.ghost { margin-top: 20rpx; background: transparent; color: #fdba74; border: 1px solid #9a3412; }
.reason { display: block; margin-top: 28rpx; color: #fdba74; }
</style>
