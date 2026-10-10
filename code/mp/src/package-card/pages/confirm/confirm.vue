<template>
  <view class="page">
    <text class="title">{{ card?.name || "确认购买" }}</text>
    <text v-if="storeName" class="line">门店 {{ storeName }}</text>
    <text v-if="card" class="line">{{ yuan(card.priceFen) }}，有效 {{ card.validDays }} 天</text>
    <text class="warn">确认购买门店，购买后不能退款，不能停卡，无法更改门店。</text>
    <view class="agree" @click="toggle">
      <text class="box">{{ checked ? "☑" : "☐" }}</text>
      <text>我已阅读并同意</text>
      <text class="link" @click.stop="openAgreement">《会员协议》</text>
    </view>
    <button
      v-if="!paid && created?.mockPay && created.status === 'PENDING'"
      type="primary"
      @click="pay"
    >模拟支付成功</button>
    <button
      v-else-if="!paid && created && !created.mockPay && created.status === 'PENDING'"
      type="primary"
      :disabled="submitting"
      @click="continuePay"
    >{{ submitting ? "调起支付…" : "继续支付" }}</button>
    <button v-else-if="!paid" type="primary" :disabled="!canPay || submitting" @click="submit">
      {{ submitting ? "下单中…" : buttonText }}
    </button>
    <view v-if="paid" class="done">支付成功，会员已生效</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { cards, storeDetail, yuan, type CardItem } from "../../../api/catalog";
import {
  createOrder,
  currentAgreement,
  mockPay,
  orderDetail,
  requestWxPay,
  waitUntilPaid,
  type AgreementDoc,
  type CreatedOrder,
} from "../../../api/order";

const card = ref<CardItem | null>(null);
const storeName = ref("");
const storeId = ref("");
const agreement = ref<AgreementDoc | null>(null);
const checked = ref(false);
const seconds = ref(0);
const created = ref<CreatedOrder | null>(null);
const paid = ref(false);
const submitting = ref(false);
const paying = ref(false);
const idempotencyKey = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
let timer = 0;

const canPay = computed(() => checked.value && seconds.value === 0 && !created.value && !submitting.value);
const buttonText = computed(() => {
  if (!checked.value) return "请先勾选协议";
  if (seconds.value > 0) return `请阅读 ${seconds.value} 秒`;
  return "确认支付";
});

onLoad(async (query) => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.navigateTo({ url: "/pages/login/login" });
    return;
  }
  storeId.value = (query?.storeId as string) || "";
  const cardId = query?.cardId as string;
  if (!storeId.value || !cardId) {
    return;
  }
  const [store, list, doc] = await Promise.all([
    storeDetail(storeId.value),
    cards(storeId.value, "ALL"),
    currentAgreement(),
  ]);
  storeName.value = store.name;
  card.value = list.find((item) => item.id === cardId) || null;
  agreement.value = doc;
});

function toggle() {
  if (created.value) {
    return;
  }
  checked.value = !checked.value;
  if (checked.value) {
    seconds.value = 4;
    timer = setInterval(() => {
      seconds.value -= 1;
      if (seconds.value <= 0) {
        clearInterval(timer);
      }
    }, 1000) as unknown as number;
  } else {
    clearInterval(timer);
    seconds.value = 0;
  }
}

function openAgreement() {
  uni.navigateTo({ url: "/package-card/pages/agreement/agreement" });
}

async function submit() {
  if (!canPay.value || !card.value || !agreement.value || submitting.value) {
    if (!agreement.value) {
      uni.showToast({ title: "协议还没有发布", icon: "none" });
    }
    return;
  }
  submitting.value = true;
  try {
    created.value = await createOrder({
      storeId: String(storeId.value),
      cardProductId: String(card.value.id),
      agreementId: String(agreement.value.id),
      agreementVersion: agreement.value.versionNo,
    }, idempotencyKey);
    if (created.value.status === "PAID") {
      paid.value = true;
      return;
    }
    if (!created.value.mockPay) {
      await finishWxPay();
    }
  } catch {
    // 订单已创建时保留，可点「继续支付」
  } finally {
    submitting.value = false;
  }
}

async function continuePay() {
  if (!created.value || created.value.mockPay || submitting.value || !card.value || !agreement.value) {
    return;
  }
  submitting.value = true;
  try {
    created.value = await createOrder({
      storeId: String(storeId.value),
      cardProductId: String(card.value.id),
      agreementId: String(agreement.value.id),
      agreementVersion: agreement.value.versionNo,
    }, idempotencyKey);
    if (created.value.status === "PAID") {
      paid.value = true;
      return;
    }
    await finishWxPay();
  } catch {
    uni.showToast({ title: "支付未完成", icon: "none" });
  } finally {
    submitting.value = false;
  }
}

async function finishWxPay() {
  if (!created.value) {
    return;
  }
  await requestWxPay(created.value);
  const fresh = await waitUntilPaid(String(created.value.orderId));
  created.value = { ...created.value, status: fresh.status };
  paid.value = fresh.status === "PAID";
  if (paid.value) {
    uni.showToast({ title: "会员已生效", icon: "none" });
  } else {
    uni.showToast({ title: "已支付，会员稍后生效", icon: "none" });
  }
}

async function pay() {
  if (!created.value || paying.value) {
    return;
  }
  paying.value = true;
  try {
    const detail = await mockPay(String(created.value.orderId));
    const fresh = await orderDetail(String(detail.id || created.value.orderId));
    created.value = { ...created.value, status: fresh.status };
    paid.value = fresh.status === "PAID";
    if (paid.value) {
      uni.showToast({ title: "会员已生效", icon: "none" });
    }
  } finally {
    paying.value = false;
  }
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.title { display: block; padding: 28rpx; border-radius: 28rpx; background: #1c1917; color: #fff; font-size: 40rpx; font-weight: 700; }
.line { display: block; margin-top: 16rpx; padding: 0 8rpx; font-size: 28rpx; }
.warn { display: block; margin: 24rpx 0; padding: 20rpx 24rpx; border-radius: 20rpx; background: #fff7ed; color: #9a3412; font-size: 26rpx; line-height: 1.6; }
.agree { display: flex; align-items: center; margin-bottom: 24rpx; font-size: 26rpx; }
.box { margin-right: 8rpx; color: #c2410c; }
.link { color: #c2410c; }
.done { margin-top: 24rpx; padding: 28rpx; border-radius: 24rpx; background: #fff; font-size: 32rpx; font-weight: 700; text-align: center; }
</style>
