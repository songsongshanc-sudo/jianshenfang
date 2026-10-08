<template>
  <view class="page">
    <text class="title">我的订单</text>
    <view v-if="loaded && rows.length === 0" class="empty">还没有订单</view>
    <view v-for="item in rows" :key="item.id" class="row">
      <text class="name">{{ item.productName }}</text>
      <text class="meta">{{ item.storeName }} · {{ orderStatusText(item.status) }}</text>
      <text class="meta">¥{{ (item.amountFen / 100).toFixed(2) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { myOrders, orderStatusText, type OrderRow } from "../../../api/order";

const rows = ref<OrderRow[]>([]);
const loaded = ref(false);

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    rows.value = [];
    loaded.value = true;
    return;
  }
  try {
    rows.value = await myOrders();
  } catch {
    rows.value = [];
  }
  loaded.value = true;
});
</script>

<style scoped>
.page { padding: 24rpx; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.empty { margin-top: 24rpx; color: #78716c; }
.row { margin-top: 16rpx; padding: 24rpx; border-radius: 16rpx; background: #fff; }
.name { display: block; font-size: 32rpx; font-weight: 600; }
.meta { display: block; margin-top: 8rpx; color: #57534e; font-size: 26rpx; }
</style>
