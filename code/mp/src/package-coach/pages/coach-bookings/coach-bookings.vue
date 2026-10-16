<template>
  <view class="page">
    <view v-if="rows.length === 0" class="empty">暂无待处理预约</view>
    <view v-for="item in rows" :key="item.id" class="card" @click="go(item.id)">
      <view class="row">
        <image v-if="item.avatarUrl" class="avatar" :src="item.avatarUrl" mode="aspectFill" />
        <view v-else class="avatar soft">{{ (item.userName || "学").slice(0, 1) }}</view>
        <view class="info">
          <text class="name">{{ item.userName }}</text>
          <text class="meta">{{ item.packName }} · 座位 {{ item.seatNo }}</text>
          <text class="meta">{{ item.classDate }} {{ item.startHm }}</text>
        </view>
        <text class="status">{{ statusText(item.status) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { coachBookings, type BookingDetail } from "../../../api/lesson";

const rows = ref<BookingDetail[]>([]);

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.redirectTo({ url: "/pages/login/login" });
    return;
  }
  rows.value = await coachBookings();
});

function statusText(status: string) {
  const map: Record<string, string> = {
    PENDING_COACH: "待确认",
    CONFIRMED: "已确认",
    APPEALING: "申诉中",
  };
  return map[status] || status;
}

function go(id: string) {
  uni.navigateTo({ url: `/package-coach/pages/booking/booking?id=${id}&coach=1` });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.empty { padding: 48rpx 16rpx; color: #8a8f98; text-align: center; }
.card {
  margin-bottom: 16rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.row { display: flex; align-items: center; gap: 16rpx; }
.avatar {
  width: 88rpx; height: 88rpx; border-radius: 50%; background: #f3f4f6; flex-shrink: 0;
}
.avatar.soft {
  display: flex; align-items: center; justify-content: center;
  color: #fff; background: #ea580c; font-weight: 700;
}
.info { flex: 1; min-width: 0; }
.name { display: block; font-size: 30rpx; font-weight: 700; }
.meta { display: block; margin-top: 6rpx; color: #8a8f98; font-size: 22rpx; }
.status { color: #ea580c; font-size: 24rpx; font-weight: 700; }
</style>
