<template>
  <view class="page">
    <view class="hero">
      <image v-if="cover" class="hero-img" :src="cover" mode="aspectFill" />
      <view v-else class="hero-img hero-fallback" />
      <view class="hero-mask" />
      <view class="locate" :style="{ top: statusPad + 'px' }" @click="goSwitch">
        <image class="pin" src="/static/icon/pin.png" mode="aspectFit" />
        <text class="locate-text">当前：{{ storeName || "选择门店" }}</text>
        <text class="swap">⇄</text>
      </view>
    </view>

    <view class="sheet">
      <view v-if="!current.storeId" class="empty">请先选择门店</view>
      <view v-else-if="rows.length === 0" class="empty">这家店还没有教练</view>
      <view v-for="item in rows" :key="item.id" class="card" @click="open(item.id)">
        <image v-if="item.avatar_url || item.photo_url" class="avatar" :src="item.avatar_url || item.photo_url || ''" mode="aspectFill" />
        <view v-else class="avatar fallback">{{ item.name.slice(0, 1) }}</view>
        <view class="body">
          <view class="top">
            <text class="name">{{ item.name }}</text>
            <text class="rating">★ {{ formatRating(item.rating) }}</text>
          </view>
          <text class="price">¥ {{ formatPrice(item.min_price_fen) }}/节起</text>
          <text class="meta">累计上课 {{ item.lesson_taught || 0 }} 节</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { coaches, type CoachListItem } from "../../api/shop";
import { storeDetail } from "../../api/catalog";
import { useCurrentStore } from "../../stores/currentStore";

const current = useCurrentStore();
const rows = ref<CoachListItem[]>([]);
const storeName = ref("");
const storeCover = ref("");
const statusPad = uni.getSystemInfoSync().statusBarHeight || 20;

const cover = computed(() => storeCover.value || rows.value[0]?.store_cover_url || "");

onShow(async () => {
  if (!current.storeId) {
    rows.value = [];
    storeName.value = "";
    storeCover.value = "";
    return;
  }
  try {
    const store = await storeDetail(current.storeId);
    storeName.value = store.name + (store.city ? `（${store.city}）` : "");
    storeCover.value = store.coverUrl || "";
  } catch {
    storeName.value = "";
    storeCover.value = "";
  }
  try {
    rows.value = await coaches(current.storeId);
  } catch {
    rows.value = [];
  }
});

function formatRating(value?: number | null) {
  const n = Number(value ?? 5);
  return Number.isFinite(n) ? n.toFixed(n % 1 ? 1 : 0) : "5";
}

function formatPrice(fen?: number | null) {
  if (fen == null) return "--";
  return (Number(fen) / 100).toFixed(2);
}

function open(id: string) {
  uni.navigateTo({ url: `/package-coach/pages/detail/detail?id=${id}` });
}

function goSwitch() {
  uni.navigateTo({ url: "/package-store/pages/switch/switch" });
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #111827;
}
.hero {
  position: relative;
  height: 520rpx;
  flex-shrink: 0;
  overflow: hidden;
}
.hero-img { width: 100%; height: 100%; display: block; background: #1f2937; }
.hero-fallback { background: linear-gradient(135deg, #1f2937, #111827); }
.hero-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.15) 0%, rgba(0, 0, 0, 0.45) 100%);
}
.locate {
  position: absolute;
  left: 24rpx;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8rpx;
  max-width: 70%;
  margin-top: 12rpx;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
}
.pin { width: 24rpx; height: 24rpx; }
.locate-text { flex: 1; min-width: 0; font-size: 24rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.swap { font-size: 24rpx; opacity: 0.9; }
.sheet {
  flex: 1;
  position: relative;
  z-index: 2;
  margin-top: -48rpx;
  padding: 28rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
  border-radius: 32rpx 32rpx 0 0;
  background: #f5f6f8;
  box-sizing: border-box;
}
.card {
  display: flex;
  gap: 20rpx;
  margin-bottom: 20rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 10rpx 28rpx rgba(15, 23, 42, 0.08);
}
.avatar { width: 140rpx; height: 140rpx; border-radius: 18rpx; flex-shrink: 0; background: #e5e7eb; }
.avatar.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1f2937;
  color: #fff;
  font-size: 48rpx;
  font-weight: 700;
}
.body { flex: 1; min-width: 0; }
.top { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.name { font-size: 32rpx; font-weight: 700; }
.rating { color: #f59e0b; font-size: 24rpx; font-weight: 600; }
.price { display: block; margin-top: 14rpx; color: #ea580c; font-size: 30rpx; font-weight: 700; }
.meta, .empty { display: block; margin-top: 10rpx; color: #8a8f98; font-size: 24rpx; }
.empty { padding: 64rpx 0; text-align: center; }
</style>
