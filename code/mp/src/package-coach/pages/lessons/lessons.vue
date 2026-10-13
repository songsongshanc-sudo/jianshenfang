<template>
  <view class="page">
    <view v-if="coach.coach" class="card coach">
      <text class="title">教练核销</text>
      <text class="hint">扫学员出示的课时二维码即可扣 1 节。微信里点扫码；网页调试可粘贴码。</text>
      <button class="primary" @click="scanRedeem">扫码核销</button>
      <input v-model="scan" placeholder="网页调试：粘贴课时码" />
      <button class="ghost" @click="redeemManual">粘贴核销</button>
    </view>

    <template v-if="!coach.coach">
      <text class="section">我购买的课程</text>
      <view v-if="rows.length === 0" class="empty">当前 0 节课时。学员买课后，才会在这里出现「出示课时码」。</view>
      <view v-for="item in rows" :key="item.pack_id" class="course">
        <image v-if="item.cover_url" class="cover" :src="item.cover_url" mode="aspectFill" />
        <view v-else class="cover soft">课</view>
        <view class="info">
          <text class="cname">{{ item.name }}</text>
          <text class="cmeta">剩余 {{ item.remaining }} 节</text>
          <view v-if="item.content" class="cdesc">{{ item.content }}</view>
        </view>
        <view class="btn" @click="show(item)">出示课时二维码</view>
      </view>

      <view class="card muted">
        <text class="title">教练怎么核销？</text>
        <text class="hint">后台把手机号登记为教练后，用该手机号登录，本页顶部会出现「扫码核销」。</text>
      </view>
    </template>

    <view v-if="qrOpen" class="mask" @click="closeQr">
      <view class="sheet" @click.stop>
        <text class="sheet-title">{{ activeName }}</text>
        <text class="sheet-hint">{{ token ? `请让教练扫码（${remainSec} 秒内有效）` : "二维码生成中…" }}</text>
        <view v-if="modules.length" class="qr">
          <view v-for="(row, rowIndex) in modules" :key="rowIndex" class="qr-row">
            <view
              v-for="(cell, colIndex) in row"
              :key="colIndex"
              class="qr-cell"
              :class="{ dark: cell.isBlack }"
            />
          </view>
        </view>
        <view class="code-row">
          <text class="code" selectable>{{ token }}</text>
          <button class="copy" size="mini" @click="copyToken">复制</button>
        </view>
        <text class="sheet-hint">扫码不行就复制发给教练，教练粘贴核销。不能用来进店。</text>
        <button class="sheet-btn" @click="refreshQr">刷新二维码</button>
        <view class="cancel" @click="closeQr">关闭</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onUnmounted, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import UQRCode from "uqrcodejs";
import { coachMe, lessonQr, myLessons, redeemLesson } from "../../../api/shop";

interface LessonRow {
  pack_id: string;
  name: string;
  remaining: number;
  content: string | null;
  cover_url: string | null;
}

const rows = ref<LessonRow[]>([]);
const coach = ref<{ coach: boolean; name?: string }>({ coach: false });
const token = ref("");
const modules = ref<Array<Array<{ isBlack: boolean }>>>([]);
const scan = ref("");
const remainSec = ref(0);
const activePackId = ref("");
const activeName = ref("");
const qrOpen = ref(false);
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
      closeQr();
      uni.showToast({ title: "课时码已过期，请重新出示", icon: "none" });
    }
  }, 1000) as unknown as number;
}

function renderQr(value: string) {
  const qr = new UQRCode();
  qr.data = value;
  qr.make();
  modules.value = qr.modules;
}

async function loadQr(packId: string) {
  try {
    const data = await lessonQr(packId);
    token.value = data.token;
    renderQr(data.token);
    startCountdown(120);
  } catch {
    closeQr();
  }
}

function show(item: LessonRow) {
  activePackId.value = String(item.pack_id);
  activeName.value = item.name;
  qrOpen.value = true;
  token.value = "";
  modules.value = [];
  void loadQr(String(item.pack_id));
}

function closeQr() {
  qrOpen.value = false;
  token.value = "";
  modules.value = [];
  clearTimer();
}

async function refreshQr() {
  if (!activePackId.value) return;
  await loadQr(activePackId.value);
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
  closeQr();
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
.card.muted { background: #f5f6f8; }
.title { display: block; font-size: 30rpx; font-weight: 700; }
.hint, .empty { display: block; margin-top: 10rpx; color: #8a8f98; font-size: 24rpx; line-height: 1.6; text-align: left; }

.course {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 16rpx;
  padding: 20rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.cover { width: 120rpx; height: 120rpx; border-radius: 16rpx; background: #f3f4f6; flex-shrink: 0; }
.cover.soft { display: flex; align-items: center; justify-content: center; color: #9ca3af; font-size: 40rpx; font-weight: 700; }
.info { flex: 1; min-width: 0; }
.cname { display: block; font-size: 30rpx; font-weight: 700; }
.cmeta { display: block; margin-top: 8rpx; color: #ea580c; font-size: 22rpx; }
.cdesc {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  margin-top: 8rpx;
  color: #8a8f98;
  font-size: 22rpx;
  line-height: 1.5;
}
.btn {
  flex-shrink: 0;
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #ea580c;
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
}

.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(17, 24, 39, 0.45); display: flex; align-items: flex-end; z-index: 20; }
.sheet { width: 100%; padding: 28rpx 28rpx 48rpx; border-radius: 24rpx 24rpx 0 0; background: #fff; box-sizing: border-box; }
.sheet-title { display: block; font-size: 32rpx; font-weight: 700; }
.sheet-hint { display: block; margin: 8rpx 0 16rpx; color: #8a8f98; font-size: 24rpx; }

.qr {
  display: flex;
  flex-direction: column;
  width: 420rpx;
  height: 420rpx;
  margin: 24rpx auto 0;
  padding: 16rpx;
  box-sizing: border-box;
  background: #fff;
  border-radius: 16rpx;
}
.qr-row { display: flex; flex: 1; }
.qr-cell { flex: 1; background: #fff; }
.qr-cell.dark { background: #000; }
.code-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
  padding: 12rpx 12rpx 12rpx 16rpx;
  border-radius: 12rpx;
  background: #f5f6f8;
  text-align: left;
}
.code { flex: 1; min-width: 0; color: #ea580c; font-size: 22rpx; word-break: break-all; }
.copy { flex-shrink: 0; margin: 0; background: #ea580c !important; color: #fff !important; }
.sheet-btn { margin-top: 20rpx; }
.cancel { margin-top: 12rpx; padding: 24rpx; text-align: center; color: #8a8f98; }

input { margin-top: 20rpx; background: #fff; }
.primary { margin-top: 16rpx; }
.ghost { margin-top: 16rpx; background: #fff4ed; color: #c2410c; }
</style>
