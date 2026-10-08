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
    <button v-if="!paid" type="primary" :disabled="!canPay" @click="submit">{{ buttonText }}</button>
    <button v-else-if="created?.mockPay && created.status === 'PENDING'" type="primary" @click="pay">模拟支付成功</button>
    <view v-if="paid" class="done">支付成功，会员已生效</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { cards, storeDetail, yuan, type CardItem } from "../../../api/catalog";
import { createOrder, currentAgreement, mockPay, orderDetail, type AgreementDoc, type CreatedOrder } from "../../../api/order";

const card = ref<CardItem | null>(null);
const storeName = ref("");
const storeId = ref("");
const agreement = ref<AgreementDoc | null>(null);
const checked = ref(false);
const seconds = ref(0);
const created = ref<CreatedOrder | null>(null);
const paid = ref(false);
const idempotencyKey = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
let timer = 0;

const canPay = computed(() => checked.value && seconds.value === 0 && !created.value);
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
  if (!canPay.value || !card.value || !agreement.value) {
    if (!agreement.value) {
      uni.showToast({ title: "协议还没有发布", icon: "none" });
    }
    return;
  }
  created.value = await createOrder({
    storeId: storeId.value,
    cardProductId: card.value.id,
    agreementId: agreement.value.id,
    agreementVersion: agreement.value.versionNo,
  }, idempotencyKey);
  if (created.value.status === "PAID") {
    paid.value = true;
    return;
  }
  if (!created.value.mockPay) {
    uni.showToast({ title: "微信支付尚未配置", icon: "none" });
  }
}

async function pay() {
  if (!created.value) {
    return;
  }
  const detail = await mockPay(created.value.orderId);
  const fresh = await orderDetail(detail.id);
  created.value = { ...created.value, status: fresh.status };
  paid.value = fresh.status === "PAID";
}
</script>

<style scoped>
.page { padding: 24rpx; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.line { display: block; margin-top: 12rpx; font-size: 28rpx; }
.warn { display: block; margin: 24rpx 0; color: #b91c1c; font-size: 28rpx; }
.agree { display: flex; align-items: center; margin-bottom: 24rpx; font-size: 26rpx; }
.box { margin-right: 8rpx; }
.link { color: #c2410c; }
.done { margin-top: 24rpx; font-size: 32rpx; font-weight: 700; }
</style>
