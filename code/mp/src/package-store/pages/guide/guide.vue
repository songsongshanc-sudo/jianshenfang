<template>
  <view class="page">
    <view v-if="!storeId" class="empty">请先选择门店</view>
    <view v-else-if="loaded && steps.length === 0" class="empty">这家店还没有配置到店指引</view>
    <view v-for="(step, index) in steps" :key="step.id" class="step">
      <text class="order">{{ index + 1 }}</text>
      <image class="image" :src="step.imageUrl" mode="widthFix" />
      <text class="caption">{{ step.caption }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { storeGuides, type GuideStep } from "../../../api/catalog";
import { useCurrentStore } from "../../../stores/currentStore";

const current = useCurrentStore();
const storeId = ref("");
const steps = ref<GuideStep[]>([]);
const loaded = ref(false);

onLoad(async (query) => {
  storeId.value = (query?.storeId as string) || current.storeId;
  if (!storeId.value) return;
  try {
    steps.value = await storeGuides(storeId.value);
  } catch {
    steps.value = [];
  } finally {
    loaded.value = true;
  }
});
</script>

<style scoped>
.page { padding: 24rpx; }
.step { background: #fff; border-radius: 24rpx; padding: 24rpx; margin-bottom: 16rpx; }
.order { display: inline-block; min-width: 40rpx; height: 40rpx; padding: 0 12rpx; border-radius: 999rpx; background: #1c1917; color: #fff; text-align: center; line-height: 40rpx; font-size: 22rpx; }
.image { width: 100%; margin-top: 12rpx; border-radius: 12rpx; }
.caption { display: block; margin-top: 12rpx; font-size: 28rpx; }
.empty { color: #888; text-align: center; margin-top: 80rpx; }
</style>
