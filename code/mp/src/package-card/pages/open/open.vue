<template>
  <view class="page">
    <text class="title">开通会员</text>
    <text class="sub">请选择您购买的会员卡</text>
    <view v-if="!cardList.length" class="empty">这家店还没有上架会员卡</view>
    <view
      v-for="item in cardList"
      :key="item.id"
      class="sku"
      :class="{ gray: !item.purchasable }"
      @click="openCard(item)"
    >
      <view class="row">
        <text class="name">{{ item.name }}</text>
        <text class="price">{{ yuan(item.priceFen) }}</text>
      </view>
      <text class="meta">有效 {{ item.validDays }} 天</text>
      <text v-if="item.displayText" class="meta">{{ item.displayText }}</text>
      <text v-if="item.remaining != null" class="left">剩余 {{ item.remaining }} 张</text>
      <text v-if="item.lockText" class="lock">{{ item.lockText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { cards, yuan, type CardItem } from "../../../api/catalog";
import { useCurrentStore } from "../../../stores/currentStore";

const cardList = ref<CardItem[]>([]);
const current = useCurrentStore();

onLoad(async (query) => {
  const storeId = (query?.storeId as string) || current.storeId;
  if (!storeId) {
    return;
  }
  try {
    cardList.value = await cards(storeId, "ALL");
  } catch {
    cardList.value = [];
  }
});

function openCard(item: CardItem) {
  if (item.purchasable) {
    const storeId = current.storeId;
    if (!storeId) {
      uni.showToast({ title: "请先选择门店", icon: "none" });
      return;
    }
    uni.navigateTo({ url: `/package-card/pages/confirm/confirm?storeId=${storeId}&cardId=${item.id}` });
    return;
  }
  uni.showModal({
    title: "暂不可购买",
    content: item.lockText || "暂时不能购买",
    showCancel: false,
  });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.sub { display: block; margin: 8rpx 0 20rpx; color: #78716c; font-size: 26rpx; }
.empty { padding: 48rpx 24rpx; border-radius: 24rpx; background: #fff; color: #78716c; text-align: center; }
.sku { margin-bottom: 16rpx; padding: 28rpx; border-radius: 24rpx; background: #fff; }
.sku.gray { background: #f5f5f4; color: #78716c; }
.row { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 32rpx; font-weight: 700; }
.price { font-size: 36rpx; font-weight: 700; color: #c2410c; }
.sku.gray .price { color: #78716c; }
.meta, .lock, .left { display: block; margin-top: 8rpx; color: #78716c; font-size: 24rpx; }
.left { color: #c2410c; }
</style>
