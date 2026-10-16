<template>
  <view class="page">
    <view v-for="item in rows" :key="item.id" class="card" :class="{ unread: !item.read_at }" @click="open(item)">
      <view class="head">
        <text class="title">{{ item.title }}</text>
        <text v-if="!item.read_at" class="dot">未读</text>
      </view>
      <text class="meta">{{ item.content }}</text>
      <text v-if="item.created_at" class="meta">{{ formatTime(item.created_at) }}</text>
    </view>
    <view v-if="rows.length === 0" class="empty">暂无消息</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { coachMe, messages, readMessage } from "../../../api/shop";

type Msg = {
  id: string;
  title: string;
  content: string;
  read_at: string | null;
  created_at?: string;
  biz_type?: string | null;
  biz_id?: string | null;
};

const rows = ref<Msg[]>([]);
const isCoach = ref(false);

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.redirectTo({ url: "/pages/login/login" });
    return;
  }
  try {
    isCoach.value = !!(await coachMe()).coach;
  } catch {
    isCoach.value = false;
  }
  rows.value = await messages();
});

function formatTime(v: string) {
  return String(v).replace("T", " ").slice(0, 19);
}

async function open(item: Msg) {
  if (!item.read_at) {
    await readMessage(item.id);
    item.read_at = new Date().toISOString();
  }
  const biz = item.biz_type || "";
  const bizId = item.biz_id;
  if ((biz === "LESSON_BOOKING" || biz === "LESSON_APPEAL") && bizId) {
    const coach = isCoach.value ? "&coach=1" : "";
    uni.navigateTo({ url: `/package-coach/pages/booking/booking?id=${bizId}${coach}` });
  }
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card {
  margin-top: 16rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.card.unread { border-color: #fdba74; background: #fff7ed; }
.head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.title { font-size: 28rpx; font-weight: 700; }
.dot {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #ea580c;
  color: #fff;
  font-size: 20rpx;
}
.meta, .empty { display: block; margin-top: 8rpx; color: #78716c; font-size: 24rpx; }
</style>
