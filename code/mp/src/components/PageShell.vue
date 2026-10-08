<template>
  <view class="page">
    <text class="title">{{ title }}</text>
    <text class="hint">页面骨架，后续阶段接入数据。</text>
  </view>
</template>

<script setup lang="ts">
defineProps<{ title: string }>();
</script>

<style scoped>
.page {
  padding: 32rpx;
}
.title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}
.hint {
  display: block;
  margin-top: 16rpx;
  color: #888;
  font-size: 28rpx;
}
</style>
