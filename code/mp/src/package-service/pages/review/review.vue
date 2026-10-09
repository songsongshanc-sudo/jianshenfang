<template>
  <view class="page">
    <text class="title">好评赠卡</text>
    <text class="hint">赠送天数还没有确定，当前不会开通或延长会员。</text>
    <view class="tabs"><text @click="load('MEITUAN')">美团</text><text @click="load('DOUYIN')">抖音</text></view>
    <view v-if="rows.length === 0" class="empty">暂无记录</view>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { reviews } from "../../../api/shop";
const rows = ref<unknown[]>([]);
async function load(platform: string) { rows.value = await reviews(platform); }
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.title { font-size: 40rpx; font-weight: 700; }
.hint, .empty { display: block; margin-top: 16rpx; color: #78716c; line-height: 1.6; }
.tabs { display: flex; gap: 12rpx; margin-top: 24rpx; }
.tabs text { padding: 12rpx 24rpx; border-radius: 999rpx; background: #fff; }
</style>
