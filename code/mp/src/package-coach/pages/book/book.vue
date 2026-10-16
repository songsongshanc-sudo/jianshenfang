<template>
  <view class="page">
    <view v-if="offer" class="card">
      <text class="title">{{ offer.packName }}</text>
      <text class="meta">教练 {{ offer.coachName }} · 剩余 {{ offer.remaining }} 节 · 单节 {{ offer.minutesPerLesson }} 分钟</text>
    </view>

    <view v-if="offer" class="card">
      <text class="label">上课日期</text>
      <scroll-view scroll-x class="dates">
        <view
          v-for="d in dates"
          :key="d.value"
          class="date"
          :class="{ on: classDate === d.value }"
          @click="pickDate(d.value)"
        >
          <text class="dw">{{ d.week }}</text>
          <text class="dd">{{ d.label }}</text>
        </view>
      </scroll-view>

      <text class="label">开始时间</text>
      <scroll-view scroll-x class="slots">
        <view
          v-for="hm in offer.startHms"
          :key="hm"
          class="slot"
          :class="{ on: startHm === hm }"
          @click="pickSlot(hm)"
        >{{ hm }}</view>
      </scroll-view>

      <text class="label">选择座位</text>
      <view class="seat-wrap">
        <view class="coach-zone">教练</view>
        <view class="grid" :style="{ gridTemplateColumns: `repeat(${offer.roomCols}, 1fr)` }">
          <view
            v-for="cell in seats"
            :key="cell.no"
            class="seat"
            :class="{ taken: cell.taken, on: seatNo === cell.no }"
            @click="pickSeat(cell)"
          >{{ cell.no }}</view>
        </view>
      </view>
      <view class="legend">
        <text><text class="dot free" />可选</text>
        <text><text class="dot taken" />已占</text>
        <text><text class="dot on" />已选</text>
      </view>
    </view>

    <button class="primary" :disabled="submitting" @click="submit">提交预约</button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { bookingOffer, createBooking, occupiedSeats, type BookingOffer } from "../../../api/lesson";

const packId = ref("");
const offer = ref<BookingOffer | null>(null);
const classDate = ref("");
const startHm = ref("");
const seatRow = ref(0);
const seatCol = ref(0);
const occupied = ref<string[]>([]);
const submitting = ref(false);

const seatNo = computed(() => (seatRow.value && seatCol.value ? `${seatRow.value}-${seatCol.value}` : ""));

const dates = computed(() => {
  if (!offer.value) return [];
  const out: Array<{ value: string; label: string; week: string }> = [];
  const weeks = ["日", "一", "二", "三", "四", "五", "六"];
  const min = new Date(offer.value.minDate + "T00:00:00");
  const max = new Date(offer.value.maxDate + "T00:00:00");
  for (let t = min.getTime(); t <= max.getTime(); t += 86400000) {
    const d = new Date(t);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    out.push({
      value: `${y}-${m}-${day}`,
      label: `${m}-${day}`,
      week: "周" + weeks[d.getDay()],
    });
  }
  return out;
});

const seats = computed(() => {
  if (!offer.value) return [];
  const rows = Number(offer.value.roomRows) || 5;
  const cols = Number(offer.value.roomCols) || 5;
  const list: Array<{ no: string; row: number; col: number; taken: boolean }> = [];
  for (let r = 1; r <= rows; r++) {
    for (let c = 1; c <= cols; c++) {
      const no = `${r}-${c}`;
      list.push({ no, row: r, col: c, taken: occupied.value.includes(no) });
    }
  }
  return list;
});

onLoad(async (q) => {
  packId.value = String(q?.packId || "");
  if (!packId.value) {
    uni.showToast({ title: "缺少课程", icon: "none" });
    return;
  }
  offer.value = await bookingOffer(packId.value);
  classDate.value = offer.value.minDate;
  if (offer.value.startHms.length) {
    startHm.value = offer.value.startHms[0];
    await reloadSeats();
  }
});

async function reloadSeats() {
  if (!packId.value || !classDate.value || !startHm.value) return;
  occupied.value = await occupiedSeats(packId.value, classDate.value, startHm.value);
  if (seatNo.value && occupied.value.includes(seatNo.value)) {
    seatRow.value = 0;
    seatCol.value = 0;
  }
}

async function pickDate(value: string) {
  classDate.value = value;
  await reloadSeats();
}

async function pickSlot(hm: string) {
  startHm.value = hm;
  await reloadSeats();
}

function pickSeat(cell: { no: string; row: number; col: number; taken: boolean }) {
  if (cell.taken) {
    uni.showToast({ title: "座位已占用", icon: "none" });
    return;
  }
  seatRow.value = cell.row;
  seatCol.value = cell.col;
}

async function submit() {
  if (!offer.value) return;
  if (offer.value.remaining < 1) {
    uni.showToast({ title: "剩余课时不足", icon: "none" });
    return;
  }
  if (!classDate.value || !startHm.value || !seatRow.value) {
    uni.showToast({ title: "请选日期、时间和座位", icon: "none" });
    return;
  }
  if (submitting.value) return;
  submitting.value = true;
  try {
    const row = await createBooking({
      packId: packId.value,
      classDate: classDate.value,
      startHm: startHm.value,
      seatRow: seatRow.value,
      seatCol: seatCol.value,
    });
    uni.redirectTo({ url: `/package-coach/pages/booking/booking?id=${row.id}` });
  } finally {
    submitting.value = false;
  }
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
.meta { display: block; margin-top: 10rpx; color: #8a8f98; font-size: 24rpx; }
.label { display: block; margin: 20rpx 0 12rpx; font-size: 26rpx; font-weight: 700; }
.dates, .slots { white-space: nowrap; }
.date {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  min-width: 120rpx;
  margin-right: 12rpx;
  padding: 16rpx 12rpx;
  border-radius: 16rpx;
  background: #f5f6f8;
}
.date.on { background: #fff7ed; color: #c2410c; }
.dw { font-size: 22rpx; color: #8a8f98; }
.dd { margin-top: 6rpx; font-size: 28rpx; font-weight: 700; }
.slot {
  display: inline-block;
  margin-right: 12rpx;
  padding: 14rpx 28rpx;
  border-radius: 999rpx;
  background: #f5f6f8;
  font-size: 26rpx;
}
.slot.on { background: #ea580c; color: #fff; }
.seat-wrap { margin-top: 8rpx; }
.coach-zone {
  margin-bottom: 12rpx;
  padding: 16rpx;
  text-align: center;
  border-radius: 12rpx;
  background: #f5f6f8;
  color: #8a8f98;
  font-size: 24rpx;
}
.grid { display: grid; gap: 10rpx; }
.seat {
  padding: 18rpx 0;
  text-align: center;
  border-radius: 12rpx;
  background: #fff7ed;
  color: #c2410c;
  font-size: 22rpx;
  font-weight: 700;
}
.seat.taken { background: #e5e7eb; color: #9ca3af; }
.seat.on { background: #ea580c; color: #fff; }
.legend {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
  color: #8a8f98;
  font-size: 22rpx;
}
.dot {
  display: inline-block;
  width: 16rpx;
  height: 16rpx;
  margin-right: 8rpx;
  border-radius: 4rpx;
  vertical-align: middle;
}
.dot.free { background: #fff7ed; border: 1px solid #fdba74; }
.dot.taken { background: #e5e7eb; }
.dot.on { background: #ea580c; }
.primary {
  margin-top: 24rpx;
  background: #ea580c !important;
  color: #fff !important;
}
.primary[disabled] { opacity: 0.5; }
</style>
