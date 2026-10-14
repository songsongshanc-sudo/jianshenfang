<template>
  <view class="page">
    <view class="hero">
      <text class="title">到店参观</text>
      <text class="price">押金 ¥{{ yuan(offer?.config.depositFen || 0) }}</text>
      <text class="meta">仅限当前选中门店 · 可逛 {{ offer?.config.durationMinutes || 0 }} 分钟 · 有效期 {{ offer?.config.validDays || 0 }} 天</text>
    </view>

    <view class="card">
      <text class="section">说明</text>
      <text class="notice">{{ offer?.config.notice || "" }}</text>
    </view>

    <view v-if="offer?.blockReason" class="tip">{{ offer.blockReason }}</view>

    <button class="primary" :disabled="!offer?.canBuy || paying" @click="buy">
      {{ paying ? "处理中…" : "支付押金并开通" }}
    </button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { createVisitOrder, mockVisitPay, visitOffer, type VisitOffer } from "../../../api/visit";
import { requestWxPay, waitUntilPaid } from "../../../api/order";

const offer = ref<VisitOffer | null>(null);
const paying = ref(false);
const storeId = ref("");

onShow(async () => {
  storeId.value = String(uni.getStorageSync("storeId") || "");
  offer.value = await visitOffer(storeId.value || undefined);
});

function yuan(fen: number) {
  return (fen / 100).toFixed(2);
}

async function buy() {
  if (!offer.value?.canBuy || !storeId.value || paying.value) return;
  paying.value = true;
  try {
    const key = `visit-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const created = await createVisitOrder(storeId.value, key);
    if (created.mockPay) {
      await mockVisitPay(created.orderId);
    } else {
      await requestWxPay(created);
      await waitUntilPaid(created.orderId);
    }
    uni.showToast({ title: "已开通参观", icon: "none" });
    setTimeout(() => uni.navigateBack(), 500);
  } catch (error: any) {
    const message = error?.message || error?.errMsg || "支付失败";
    if (!String(message).includes("cancel") && !String(message).includes("取消")) {
      uni.showToast({ title: String(message).slice(0, 40), icon: "none" });
    }
  } finally {
    paying.value = false;
  }
}
</script>

<style scoped>
.page { padding: 28rpx; }
.hero {
  padding: 36rpx 28rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #fff7ed, #ffedd5);
  border: 1px solid #fed7aa;
}
.title { display: block; font-size: 36rpx; font-weight: 800; }
.price { display: block; margin-top: 16rpx; font-size: 48rpx; font-weight: 800; color: #ea580c; }
.meta { display: block; margin-top: 10rpx; color: #9a3412; font-size: 24rpx; }
.card {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.section { display: block; font-size: 28rpx; font-weight: 700; }
.notice { display: block; margin-top: 12rpx; color: #6b7280; font-size: 24rpx; line-height: 1.7; white-space: pre-wrap; }
.tip { margin-top: 20rpx; color: #b45309; font-size: 24rpx; }
.primary {
  margin-top: 40rpx;
  background: #ea580c !important;
  color: #fff !important;
  border-radius: 999rpx;
}
</style>
