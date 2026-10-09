<template>
  <view class="page">
    <text class="lead">先看教练，再选课时包。购买后到「我的课程」出示课时码。</text>
    <text class="label">教练</text>
    <view v-if="rows.length === 0" class="empty">这家店还没有教练</view>
    <view v-for="item in rows" :key="item.id" class="card" @click="open(item.id)">
      <view class="avatar">{{ item.name.slice(0, 1) }}</view>
      <view class="body">
        <text class="name">{{ item.name }}</text>
        <text class="meta">{{ item.intro || "私教" }}</text>
      </view>
    </view>
    <text class="label">课时包</text>
    <view v-if="courses.length === 0" class="empty">还没有在售课程</view>
    <view v-for="pack in courses" :key="pack.id" class="card" @click="course(pack.id)">
      <view class="avatar soft"><image class="ico" src="/static/icon/lesson.png" mode="aspectFit" /></view>
      <view class="body">
        <text class="name">{{ pack.name }}</text>
        <text class="meta">{{ pack.lesson_count }} 课时</text>
      </view>
      <text class="price">¥{{ (pack.price_fen / 100).toFixed(0) }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { coaches, packs } from "../../api/shop";
import { useCurrentStore } from "../../stores/currentStore";
const current = useCurrentStore();
const rows = ref<Array<{ id: string; name: string; intro: string }>>([]);
const courses = ref<Array<{ id: string; name: string; price_fen: number; lesson_count: number }>>([]);
onShow(async () => {
  if (!current.storeId) return;
  try {
    rows.value = await coaches(current.storeId);
    courses.value = await packs(current.storeId);
  } catch {
    rows.value = [];
    courses.value = [];
  }
});
function open(id: string) { uni.navigateTo({ url: `/package-coach/pages/detail/detail?id=${id}` }); }
function course(id: string) { uni.navigateTo({ url: `/package-coach/pages/course/course?id=${id}` }); }
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.lead { display: block; color: #78716c; font-size: 24rpx; line-height: 1.6; }
.label { display: block; margin: 28rpx 4rpx 12rpx; font-size: 30rpx; font-weight: 700; }
.card { display: flex; align-items: center; gap: 20rpx; margin-bottom: 16rpx; padding: 24rpx; background: #fff; border-radius: 24rpx; }
.avatar { width: 84rpx; height: 84rpx; border-radius: 28rpx; background: #1c1917; color: #fff; text-align: center; line-height: 84rpx; font-size: 32rpx; font-weight: 700; flex-shrink: 0; }
.avatar.soft { background: #f6f3ee; display: flex; align-items: center; justify-content: center; }
.ico { width: 40rpx; height: 40rpx; }
.body { flex: 1; min-width: 0; }
.name { display: block; font-size: 30rpx; font-weight: 600; }
.meta, .empty { display: block; margin-top: 6rpx; color: #78716c; font-size: 24rpx; }
.price { color: #c2410c; font-size: 34rpx; font-weight: 700; }
</style>
