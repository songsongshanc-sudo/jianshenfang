<template>
  <view class="page">
    <view v-if="!ssid" class="empty">注册完成并登录后才能查看本店 WiFi</view>
    <view v-else class="card">
      <text class="line">名称：{{ ssid }}</text>
      <text class="line">密码：{{ password || "无密码" }}</text>
      <button @click="copy">复制密码</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { wifi } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";

const current = useCurrentStore();
const ssid = ref("");
const password = ref("");

onLoad(async (query) => {
  const storeId = (query?.storeId as string) || current.storeId;
  if (!storeId || uni.getStorageSync("mpTokenType") !== "ACCESS") return;
  try {
    const data = await wifi(storeId);
    ssid.value = data.ssid || "";
    password.value = data.password || "";
  } catch {
    ssid.value = "";
  }
});

function copy() {
  uni.setClipboardData({ data: password.value });
}
</script>

<style scoped>
.page { padding: 24rpx; }
.card { padding: 32rpx; background: #fff; border-radius: 28rpx; box-shadow: 0 8rpx 28rpx rgba(28, 25, 23, 0.06); }
button { margin-top: 28rpx; }
.line { display: block; margin-top: 12rpx; }
.empty { color: #78716c; }
</style>
