<template>
  <view class="page" v-if="pack">
    <view class="hero">
      <image class="hero-img" :src="heroImg" mode="aspectFill" />
      <view class="hero-mask" />
      <view class="nav" :style="{ paddingTop: statusPad + 'px' }">
        <view class="back" @click="goBack">‹</view>
        <text class="nav-title">课程详情</text>
        <view class="nav-space" />
      </view>
    </view>

    <view class="sheet">
      <text class="title">{{ pack.name }}</text>
      <text class="price">¥{{ (pack.price_fen / 100).toFixed(0) }}</text>

      <view class="block">
        <view class="block-head">
          <view class="dot" />
          <text class="block-title">课程内容</text>
        </view>
        <text class="block-text">{{ pack.content || "暂无说明" }}</text>
      </view>

      <view class="block">
        <view class="block-head">
          <view class="dot" />
          <text class="block-title">适合人群</text>
        </view>
        <text class="block-text">{{ pack.audience || "暂无说明" }}</text>
      </view>

      <view class="block">
        <view class="block-head">
          <view class="dot" />
          <text class="block-title">上课流程</text>
        </view>
        <view class="steps">
          <view class="step">
            <view class="step-badge">1</view>
            <text class="step-text">购买</text>
          </view>
          <text class="arrow">›</text>
          <view class="step">
            <view class="step-badge">2</view>
            <text class="step-text">核销</text>
          </view>
          <text class="arrow">›</text>
          <view class="step">
            <view class="step-badge">3</view>
            <text class="step-text">上课</text>
          </view>
        </view>
      </view>
    </view>

    <view class="footer">
      <button class="buy" @click="buy">立即购买</button>
    </view>
  </view>
  <view v-else class="empty">课程不存在或已下架</view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { packDetail, type CoursePack } from "../../../api/shop";
import { currentAgreement, requestWxPay, waitUntilPaid, type CreatedOrder } from "../../../api/order";
import { request } from "../../../api/http";

const pack = ref<CoursePack | null>(null);
const statusPad = uni.getSystemInfoSync().statusBarHeight || 20;
const heroImg = computed(() => pack.value?.cover_url || pack.value?.coach_photo_url || pack.value?.coach_avatar_url || "");

onLoad(async (query) => {
  const id = (query?.id as string) || "";
  if (!id) return;
  try {
    pack.value = await packDetail(id);
  } catch {
    pack.value = null;
  }
});

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: "/pages/coach/coach" }) });
}

async function buy() {
  if (!pack.value) return;
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.navigateTo({ url: "/pages/login/login" });
    return;
  }
  const agreement = await currentAgreement();
  if (!agreement) {
    uni.showToast({ title: "协议还没有发布", icon: "none" });
    return;
  }
  try {
    const created = await request<CreatedOrder>({
      url: `/api/mp/packs/${pack.value.id}/orders`,
      method: "POST",
      header: { "Idempotency-Key": `course-${Date.now()}` },
      data: { agreementId: agreement.id, agreementVersion: agreement.versionNo }
    });
    if (created.mockPay) {
      await request({ url: `/api/mp/orders/${created.orderId}/mock-pay`, method: "POST" });
      uni.showToast({ title: "模拟支付成功", icon: "none" });
      uni.navigateTo({ url: "/package-coach/pages/lessons/lessons" });
      return;
    }
    await requestWxPay(created);
    const fresh = await waitUntilPaid(String(created.orderId));
    if (fresh.status === "PAID") {
      uni.showToast({ title: "购买成功", icon: "none" });
      uni.navigateTo({ url: "/package-coach/pages/lessons/lessons" });
    } else {
      uni.showToast({ title: "已支付，课时稍后到账", icon: "none" });
    }
  } catch {
    uni.showToast({ title: "支付未完成", icon: "none" });
  }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f5f6f8; padding-bottom: 160rpx; }
.hero { position: relative; height: 520rpx; overflow: hidden; }
.hero-img { width: 100%; height: 100%; display: block; background: #1f2937; }
.hero-mask { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,.35), rgba(0,0,0,.2)); }
.nav {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 8rpx;
  padding-right: 8rpx;
  box-sizing: border-box;
}
.back {
  width: 72rpx;
  height: 72rpx;
  line-height: 64rpx;
  text-align: center;
  font-size: 56rpx;
  font-weight: 300;
  color: #fff;
}
.nav-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #fff; }
.nav-space { width: 72rpx; }
.sheet {
  margin: -48rpx 0 0;
  position: relative;
  padding: 36rpx 24rpx 24rpx;
  border-radius: 32rpx 32rpx 0 0;
  background: #f5f6f8;
}
.title { display: block; font-size: 44rpx; font-weight: 800; color: #1a1a1a; }
.price { display: block; margin-top: 12rpx; color: #ea580c; font-size: 48rpx; font-weight: 800; }
.block {
  margin-top: 20rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.block-head { display: flex; align-items: center; gap: 10rpx; margin-bottom: 12rpx; }
.dot { width: 14rpx; height: 14rpx; border-radius: 4rpx; background: #ea580c; }
.block-title { font-size: 28rpx; font-weight: 700; }
.block-text { display: block; color: #525252; font-size: 26rpx; line-height: 1.7; }
.steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 16rpx;
  border-radius: 16rpx;
  background: #f5f6f8;
}
.step { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.step-badge {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #fff4ed;
  color: #ea580c;
  text-align: center;
  line-height: 48rpx;
  font-size: 24rpx;
  font-weight: 700;
}
.step-text { font-size: 24rpx; color: #525252; }
.arrow { color: #c4c4c4; font-size: 36rpx; padding: 0 4rpx; }
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #eceff3;
}
.buy { margin: 0; }
.empty { padding: 80rpx 24rpx; text-align: center; color: #8a8f98; }
</style>
