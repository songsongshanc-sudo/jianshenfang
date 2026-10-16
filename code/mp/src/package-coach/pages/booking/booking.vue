<template>
  <view class="page" v-if="row">
    <view class="card">
      <text class="title">{{ row.packName }}</text>
      <text class="status">{{ statusText(row.status) }}</text>
      <text v-if="row.actedByLabel" class="line">操作端：{{ row.actedByLabel }}</text>
      <text v-if="row.pastStart" class="warn">已过开课时间</text>
      <text class="line">教练：{{ row.coachName }}</text>
      <text class="line">学员：{{ row.userName }}（{{ row.memberNo || "—" }}）</text>
      <text class="line">时间：{{ row.classDate }} {{ row.startHm }}</text>
      <text class="line">座位：{{ row.seatNo }}</text>
      <text v-if="row.rejectReason" class="line">原因：{{ row.rejectReason }}</text>
      <text v-if="row.cancelReason" class="line">{{ cancelText(row.cancelReason) }}</text>
      <text v-if="row.pendingUntil && row.status === 'PENDING_COACH'" class="line">请在 {{ formatTime(row.pendingUntil) }} 前等待教练确认</text>
    </view>

    <view v-if="row.appeals?.length" class="card">
      <text class="label">申诉记录</text>
      <view v-for="a in row.appeals" :key="a.id" class="appeal">
        <text>{{ a.status }} · {{ a.reason }}</text>
        <text v-if="a.rejectReason" class="meta">驳回：{{ a.rejectReason }}</text>
      </view>
    </view>

    <view v-if="!coachMode" class="actions">
      <button v-if="canCancel" class="ghost" @click="doCancel">取消预约</button>
      <button v-if="canAppeal" class="primary" @click="openAppeal">发起申诉</button>
    </view>

    <view v-else class="actions">
      <button v-if="row.phone" class="ghost" @click="callPhone">拨打学员电话</button>
      <button v-if="row.status === 'PENDING_COACH'" class="primary" @click="doApprove">同意</button>
      <button v-if="row.status === 'PENDING_COACH'" class="ghost" @click="openReject">拒绝</button>
      <button v-if="row.status === 'APPEALING'" class="primary" @click="doRefund">退课</button>
      <button v-if="row.status === 'APPEALING'" class="ghost" @click="openRejectAppeal">驳回申诉</button>
    </view>

    <view v-if="reasonOpen" class="mask" @click="reasonOpen = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">{{ reasonTitle }}</text>
        <textarea v-model="reason" placeholder="请填写原因" />
        <button class="primary" @click="submitReason">提交</button>
        <view class="cancel" @click="reasonOpen = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import {
  bookingDetail,
  cancelBooking,
  coachApprove,
  coachBookingDetail,
  coachRefund,
  coachReject,
  coachRejectAppeal,
  createAppeal,
  type BookingDetail,
} from "../../../api/lesson";

const id = ref("");
const coachMode = ref(false);
const row = ref<BookingDetail | null>(null);
const reasonOpen = ref(false);
const reason = ref("");
const reasonMode = ref<"appeal" | "reject" | "rejectAppeal">("appeal");

const reasonTitle = computed(() => {
  if (reasonMode.value === "reject") return "拒绝原因";
  if (reasonMode.value === "rejectAppeal") return "驳回申诉原因";
  return "申诉原因";
});

const canCancel = computed(() => {
  const s = row.value?.status;
  return s === "PENDING_COACH" || s === "CONFIRMED";
});

const canAppeal = computed(() => row.value?.status === "CONFIRMED");

onLoad(async (q) => {
  id.value = String(q?.id || "");
  coachMode.value = String(q?.coach || "") === "1";
  await reload();
});

async function reload() {
  if (!id.value) return;
  row.value = coachMode.value ? await coachBookingDetail(id.value) : await bookingDetail(id.value);
}

function statusText(status: string) {
  const map: Record<string, string> = {
    PENDING_COACH: "待教练确认",
    CONFIRMED: "已确认",
    REJECTED: "已拒绝",
    CANCELLED: "已取消",
    APPEALING: "申诉中",
    REFUNDED: "已退课",
  };
  return map[status] || status;
}

function cancelText(code: string) {
  const map: Record<string, string> = {
    USER_PENDING: "学员已取消",
    USER_CONFIRMED: "学员已取消，课时已退回",
    PAST_START: "已过开课时间，预约已取消",
    NO_LESSON: "课时不足，预约已取消",
    TIMEOUT: "超时未确认，预约已取消",
  };
  return map[code] || "预约已取消";
}

function formatTime(v: string) {
  return String(v).replace("T", " ").slice(0, 19);
}

async function doCancel() {
  await cancelBooking(id.value);
  uni.showToast({ title: "已取消", icon: "none" });
  await reload();
}

function openAppeal() {
  reasonMode.value = "appeal";
  reason.value = "";
  reasonOpen.value = true;
}

function openReject() {
  reasonMode.value = "reject";
  reason.value = "";
  reasonOpen.value = true;
}

function openRejectAppeal() {
  reasonMode.value = "rejectAppeal";
  reason.value = "";
  reasonOpen.value = true;
}

async function submitReason() {
  if (!reason.value.trim()) {
    uni.showToast({ title: "请填写原因", icon: "none" });
    return;
  }
  if (reasonMode.value === "appeal") {
    await createAppeal(id.value, reason.value.trim());
  } else if (reasonMode.value === "reject") {
    await coachReject(id.value, reason.value.trim());
  } else {
    await coachRejectAppeal(id.value, reason.value.trim());
  }
  reasonOpen.value = false;
  uni.showToast({ title: "已提交", icon: "none" });
  await reload();
}

async function doApprove() {
  await coachApprove(id.value);
  uni.showToast({ title: "已同意并扣课", icon: "none" });
  await reload();
}

async function doRefund() {
  if (row.value?.pastStart) {
    const ok = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: "确认退课",
        content: "已过开课时间，确认仍要退回 1 节课时？",
        success: (r) => resolve(!!r.confirm),
      });
    });
    if (!ok) return;
  }
  await coachRefund(id.value);
  uni.showToast({ title: "已退课", icon: "none" });
  await reload();
}

function callPhone() {
  if (!row.value?.phone) return;
  uni.makePhoneCall({ phoneNumber: String(row.value.phone) });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card {
  margin-bottom: 16rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.title { display: block; font-size: 32rpx; font-weight: 700; }
.status { display: block; margin-top: 8rpx; color: #ea580c; font-size: 26rpx; font-weight: 700; }
.warn { display: block; margin-top: 8rpx; color: #dc2626; font-size: 24rpx; }
.line, .meta { display: block; margin-top: 10rpx; color: #57534e; font-size: 26rpx; }
.label { display: block; font-size: 28rpx; font-weight: 700; margin-bottom: 8rpx; }
.appeal { margin-top: 12rpx; padding-top: 12rpx; border-top: 1px solid #f3f4f6; font-size: 24rpx; }
.actions { display: flex; flex-direction: column; gap: 12rpx; }
.primary { background: #ea580c !important; color: #fff !important; }
.ghost { background: #fff7ed !important; color: #c2410c !important; }
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(17, 24, 39, 0.45); display: flex; align-items: flex-end; z-index: 20;
}
.sheet { width: 100%; padding: 28rpx 28rpx 48rpx; border-radius: 24rpx 24rpx 0 0; background: #fff; box-sizing: border-box; }
.sheet-title { display: block; font-size: 32rpx; font-weight: 700; margin-bottom: 16rpx; }
textarea { width: 100%; min-height: 160rpx; padding: 16rpx; background: #f5f6f8; border-radius: 12rpx; box-sizing: border-box; }
.cancel { margin-top: 12rpx; padding: 24rpx; text-align: center; color: #8a8f98; }
</style>
