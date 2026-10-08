<template>
  <view class="page">
    <text class="title">我的</text>
    <view v-if="!profile" class="empty">登录后可以查看会员编号和连续天数</view>
    <view v-else class="card">
      <text class="line">{{ profile.nickname || "会员" }}</text>
      <text class="line">已陪伴您 {{ profile.companionDays }} 天</text>
      <text v-if="profile.consecutiveRemain != null" class="line">激活连续月卡还需 {{ profile.consecutiveRemain }} 天</text>
      <text v-if="profile.memberNo" class="line">会员编号：{{ profile.memberNo }}</text>
      <text class="line">{{ profile.storeMember ? "当前门店会员有效" : "当前门店还没有会员" }}</text>
    </view>
    <button v-if="profile" @click="buy">会员卡购买</button>
    <button v-if="profile" @click="orders">我的订单</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { me, type MeProfile } from "../../api/catalog";
import { useCurrentStore } from "../../stores/currentStore";

const profile = ref<MeProfile | null>(null);
const current = useCurrentStore();

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    profile.value = null;
    return;
  }
  try {
    profile.value = await me(current.storeId || undefined);
  } catch {
    profile.value = null;
  }
});

function buy() {
  if (!current.storeId) {
    uni.showToast({ title: "请先选择门店", icon: "none" });
    return;
  }
  uni.navigateTo({ url: `/package-card/pages/open/open?storeId=${current.storeId}` });
}

function orders() {
  uni.navigateTo({ url: "/package-card/pages/orders/orders" });
}
</script>

<style scoped>
.page { padding: 24rpx; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.empty { margin-top: 24rpx; color: #78716c; }
.card { margin-top: 24rpx; padding: 24rpx; border-radius: 16rpx; background: #fff; }
.line { display: block; margin-top: 10rpx; font-size: 28rpx; }
button { margin-top: 24rpx; }
</style>
