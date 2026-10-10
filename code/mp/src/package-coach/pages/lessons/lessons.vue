<template>
  <view class="page">
    <view v-if="coach.coach" class="card coach">
      <text class="title">教练核销</text>
      <text class="hint">扫学员出示的课时二维码即可扣 1 节。微信里点扫码；网页调试可粘贴码。</text>
      <button class="primary" @click="scanRedeem">扫码核销</button>
      <input v-model="scan" placeholder="网页调试：粘贴课时码" />
      <button class="ghost" @click="redeemManual">粘贴核销</button>
    </view>

    <text class="section">我购买的课程</text>
    <view v-if="rows.length === 0" class="empty">当前 0 节课时。学员买课后，才会在这里出现「出示课时码」。</view>
    <view v-for="item in rows" :key="item.pack_id" class="card">
      <text class="title">{{ item.name }}</text>
      <text class="meta">剩余 {{ item.remaining }} 节</text>
      <button class="ghost" @click="show(item.pack_id)">出示课时二维码</button>
    </view>

    <view v-if="token" class="card token">
      <text class="title">请让教练扫码（{{ remainSec }} 秒内有效）</text>
      <image v-if="qrDataUrl" class="qr" :src="qrDataUrl" mode="aspectFit" />
      <text v-else class="hint">二维码生成中…</text>
      <view class="code-row">
        <text class="code" selectable>{{ token }}</text>
        <button class="copy" size="mini" @click="copyToken">复制</button>
      </view>
      <text class="hint">扫码不行就复制发给教练，教练粘贴核销。不能用来进店。</text>
      <button class="ghost" @click="refreshQr">刷新二维码</button>
    </view>

    <view v-if="!coach.coach" class="card muted">
      <text class="title">教练怎么核销？</text>
      <text class="hint">后台把手机号登记为教练后，用该手机号登录，本页顶部会出现「扫码核销」。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import QRCode from "qrcode";
import { coachMe, lessonQr, myLessons, redeemLesson } from "../../../api/shop";

const rows = ref<Array<{ pack_id: string; name: string; remaining: number }>>([]);
const coach = ref<{ coach: boolean; name?: string }>({ coach: false });
const token = ref("");
const qrDataUrl = ref("");
const scan = ref("");
const remainSec = ref(0);
const activePackId = ref("");
let timer = 0;

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.redirectTo({ url: "/pages/login/login" });
    return;
  }
  try {
    coach.value = await coachMe();
  } catch {
    coach.value = { coach: false };
  }
  try {
    rows.value = await myLessons();
  } catch {
    rows.value = [];
  }
});

onUnmounted(() => clearTimer());

function clearTimer() {
  if (timer) {
    clearInterval(timer);
    timer = 0;
  }
}

function startCountdown(seconds: number) {
  clearTimer();
  remainSec.value = seconds;
  timer = setInterval(() => {
    remainSec.value -= 1;
    if (remainSec.value <= 0) {
      clearTimer();
      token.value = "";
      qrDataUrl.value = "";
      uni.showToast({ title: "课时码已过期，请重新出示", icon: "none" });
    }
  }, 1000) as unknown as number;
}

async function renderQr(value: string) {
  qrDataUrl.value = await QRCode.toDataURL(value, {
    width: 280,
    margin: 1,
    errorCorrectionLevel: "M",
  });
}

async function show(packId: string) {
  activePackId.value = String(packId);
  const data = await lessonQr(String(packId));
  token.value = data.token;
  await renderQr(data.token);
  startCountdown(120);
}

async function refreshQr() {
  if (!activePackId.value) return;
  await show(activePackId.value);
}

function copyToken() {
  if (!token.value) return;
  uni.setClipboardData({
    data: token.value,
    success() {
      uni.showToast({ title: "已复制，发给教练即可", icon: "none" });
    },
    fail() {
      uni.showToast({ title: "复制失败，请长按码手动选中", icon: "none" });
    },
  });
}

async function doRedeem(value: string) {
  const code = value.trim();
  if (!code) {
    uni.showToast({ title: "没有识别到课时码", icon: "none" });
    return;
  }
  await redeemLesson(code);
  scan.value = "";
  token.value = "";
  qrDataUrl.value = "";
  clearTimer();
  rows.value = await myLessons();
  uni.showToast({ title: "已核销 1 节", icon: "none" });
}

function scanRedeem() {
  uni.scanCode({
    onlyFromCamera: false,
    scanType: ["qrCode"],
    success(res) {
      void doRedeem(res.result || "");
    },
    fail() {
      uni.showToast({ title: "扫码失败，可改用粘贴", icon: "none" });
    },
  });
}

async function redeemManual() {
  await doRedeem(scan.value);
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.section { display: block; margin: 28rpx 8rpx 12rpx; font-size: 28rpx; font-weight: 700; }
.card {
  margin-top: 16rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.card.coach { border-color: #fdba74; background: #fff7ed; }
.card.token { background: #f8fafc; text-align: center; }
.card.muted { background: #f5f6f8; }
.title { display: block; font-size: 30rpx; font-weight: 700; }
.meta, .hint, .empty { display: block; margin-top: 10rpx; color: #8a8f98; font-size: 24rpx; line-height: 1.6; text-align: left; }
.qr {
  width: 420rpx;
  height: 420rpx;
  margin: 24rpx auto 0;
  background: #fff;
  border-radius: 16rpx;
}
.code-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
  padding: 12rpx 12rpx 12rpx 16rpx;
  border-radius: 12rpx;
  background: #fff;
  text-align: left;
}
.code {
  flex: 1;
  min-width: 0;
  color: #ea580c;
  font-size: 22rpx;
  word-break: break-all;
}
.copy {
  flex-shrink: 0;
  margin: 0;
  background: #ea580c !important;
  color: #fff !important;
}
input { margin-top: 20rpx; background: #fff; }
.primary { margin-top: 16rpx; }
.ghost { margin-top: 16rpx; background: #fff4ed; color: #c2410c; }
</style>
