<template>
  <view class="page">
    <view class="card">
      <text class="label">姓名</text>
      <text class="value">{{ name || "—" }}</text>
      <text class="label">手机号</text>
      <text class="value">{{ phone || "—" }}</text>
      <text class="hint">姓名和手机号注册后不能修改。</text>
    </view>
    <view class="card">
      <text class="label">人脸照片</text>
      <image v-if="preview" class="preview" :src="preview" mode="aspectFill" />
      <text class="hint">人脸用于闸机识别，不能更换。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { me } from "../../api/catalog";
import { facePreviewBase64 } from "../../api/auth";
import { useCurrentStore } from "../../stores/currentStore";

const current = useCurrentStore();
const name = ref("");
const phone = ref("");
const preview = ref("");

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.redirectTo({ url: "/pages/login/login" });
    return;
  }
  const profile = await me(current.storeId || undefined);
  name.value = profile.nickname || "";
  phone.value = profile.phone || "";
  preview.value = await facePreviewBase64().catch(() => "");
});
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card { margin-bottom: 16rpx; padding: 28rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.label { display: block; margin-top: 8rpx; color: #8a8f98; font-size: 24rpx; }
.value { display: block; margin-top: 8rpx; font-size: 32rpx; font-weight: 700; }
.hint { display: block; margin-top: 16rpx; color: #8a8f98; font-size: 24rpx; line-height: 1.6; }
.preview { width: 240rpx; height: 240rpx; margin-top: 16rpx; border-radius: 16rpx; background: #f3f4f6; }
</style>
