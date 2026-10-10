<template>
  <view class="page">
    <view v-if="rows.length === 0" class="empty">开通会员并刷脸进店后，这里显示进店记录</view>
    <view v-for="item in rows" :key="item.id" class="card">
      <text class="name">{{ item.storeName || "门店" }}</text>
      <text class="meta">{{ item.result }}</text>
      <text class="meta">{{ formatTime(item.createdAt) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { request } from "../../../api/http";

const rows = ref<Array<{ id: string; storeName: string; result: string; createdAt: string }>>([]);

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.navigateTo({ url: "/pages/login/login" });
    return;
  }
  try {
    rows.value = await request({ url: "/api/mp/visits" });
  } catch {
    rows.value = [];
  }
});

function formatTime(value: string) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 19);
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card {
  margin-bottom: 16rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 8rpx 28rpx rgba(28, 25, 23, 0.06);
}
.name { display: block; font-size: 30rpx; font-weight: 700; }
.meta { display: block; margin-top: 8rpx; color: #78716c; font-size: 24rpx; }
.empty { padding: 80rpx 24rpx; color: #78716c; text-align: center; }
</style>
