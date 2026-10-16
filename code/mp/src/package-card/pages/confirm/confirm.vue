<template>
  <view class="page">
    <view class="body">
      <text class="title">{{ card?.name || "确认购买" }}</text>
      <text v-if="storeName" class="line">门店 {{ storeName }}</text>
      <text v-if="card" class="line">{{ yuan(card.priceFen) }}，有效 {{ card.validDays }} 天</text>
      <text class="warn">确认购买门店，购买后不能退款，不能停卡，无法更改门店。</text>

      <view class="guide">
        <text class="guide-title">{{ guide?.title || "会员服务说明" }}</text>
        <rich-text v-if="guide?.content" class="guide-body" :nodes="guideHtml" />
        <text v-else class="guide-empty">暂无服务说明，不影响购卡</text>
      </view>

      <view class="agree" @click="toggle">
        <text class="box">{{ checked ? "☑" : "☐" }}</text>
        <text>我已阅读并同意</text>
        <text class="link" @click.stop="openAgreement">《会员协议》</text>
      </view>
    </view>

    <view class="footer">
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
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { cards, storeDetail, yuan, type CardItem } from "../../../api/catalog";
import {
  createOrder,
  currentAgreement,
  currentServiceGuide,
  mockPay,
  orderDetail,
  requestWxPay,
  waitUntilPaid,
  type AgreementDoc,
  type CreatedOrder,
} from "../../../api/order";
import { fitRichHtml } from "../../../utils/richHtml";

const card = ref<CardItem | null>(null);
const storeName = ref("");
const storeId = ref("");
const agreement = ref<AgreementDoc | null>(null);
const guide = ref<AgreementDoc | null>(null);
const checked = ref(false);
const created = ref<CreatedOrder | null>(null);
const paid = ref(false);
const submitting = ref(false);
const paying = ref(false);
const idempotencyKey = `${Date.now()}-${Math.random().toString(16).slice(2)}`;

const guideHtml = computed(() => fitRichHtml(guide.value?.content));
const canPay = computed(() => checked.value && !!agreement.value && !created.value && !submitting.value);
const buttonText = computed(() => {
  if (!agreement.value) return "协议未发布";
  if (!checked.value) return "请先勾选协议";
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
  const [store, list, doc, serviceGuide] = await Promise.all([
    storeDetail(storeId.value),
    cards(storeId.value, "ALL"),
    currentAgreement(),
    currentServiceGuide(),
  ]);
  storeName.value = store.name;
  card.value = list.find((item) => item.id === cardId) || null;
  agreement.value = doc;
  guide.value = serviceGuide;
});

function toggle() {
  if (created.value) {
    return;
  }
  checked.value = !checked.value;
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
  // 测试逻辑：按钮「模拟支付成功」。见 开发文档/前端测试逻辑-待删除.md
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
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding: 24rpx 24rpx 0;
  background: #f5f5f4;
}
.body { flex: 1; padding-bottom: 24rpx; }
.title {
  display: block;
  padding: 28rpx;
  border-radius: 28rpx;
  background: #1c1917;
  color: #fff;
  font-size: 40rpx;
  font-weight: 700;
}
.line { display: block; margin-top: 16rpx; padding: 0 8rpx; font-size: 28rpx; }
.warn {
  display: block;
  margin: 24rpx 0;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  background: #fff7ed;
  color: #9a3412;
  font-size: 26rpx;
  line-height: 1.6;
}
.guide {
  margin-bottom: 24rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  overflow: hidden;
}
.guide-title {
  display: block;
  margin-bottom: 16rpx;
  font-size: 32rpx;
  font-weight: 700;
}
.guide-body { display: block; width: 100%; overflow: hidden; font-size: 28rpx; line-height: 1.7; color: #44403c; }
.guide-empty { display: block; color: #78716c; font-size: 28rpx; }
.agree {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 8rpx;
  font-size: 26rpx;
}
.box { margin-right: 8rpx; color: #c2410c; }
.link { color: #c2410c; }
.footer {
  position: sticky;
  bottom: 0;
  padding: 16rpx 0 calc(16rpx + env(safe-area-inset-bottom));
  background: #f5f5f4;
}
.done {
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  font-size: 32rpx;
  font-weight: 700;
  text-align: center;
}
</style>
