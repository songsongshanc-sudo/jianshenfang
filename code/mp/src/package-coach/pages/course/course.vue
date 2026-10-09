<template>
  <view class="page" v-if="pack">
    <text class="title">{{ pack.name }}</text>
    <text class="meta">{{ (pack.price_fen / 100).toFixed(2) }} 元 · {{ pack.lesson_count }} 课时</text>
    <text class="block">课程内容</text>
    <text class="meta">{{ pack.content }}</text>
    <text class="block">适合人群</text>
    <text class="meta">{{ pack.audience }}</text>
    <button @click="buy">立即购买</button>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { packs } from "../../../api/shop";
import { currentAgreement } from "../../../api/order";
import { request } from "../../../api/http";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const pack = ref<{ id: string; name: string; price_fen: number; lesson_count: number; content: string; audience: string } | null>(null);
onLoad(async (query) => {
  const list = await packs(current.storeId);
  pack.value = list.find((item) => item.id === query?.id) || null;
});
async function buy() {
  if (!pack.value) return;
  const agreement = await currentAgreement();
  if (!agreement) {
    uni.showToast({ title: "协议还没有发布", icon: "none" });
    return;
  }
  const created = await request<{ orderId: string; mockPay: boolean }>({
    url: `/api/mp/packs/${pack.value.id}/orders`,
    method: "POST",
    header: { "Idempotency-Key": `course-${Date.now()}` },
    data: { agreementId: agreement.id, agreementVersion: agreement.versionNo }
  });
  if (created.mockPay) {
    await request({ url: `/api/mp/orders/${created.orderId}/mock-pay`, method: "POST" });
    uni.showToast({ title: "模拟支付成功", icon: "none" });
    uni.navigateTo({ url: "/package-coach/pages/lessons/lessons" });
  }
}
</script>
<style scoped>
.page { padding: 24rpx; } .title { font-size: 36rpx; font-weight: 600; } .meta, .block { display: block; margin-top: 12rpx; }
.block { font-weight: 600; }
</style>
