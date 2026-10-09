<template>
  <view class="page">
    <view v-if="rows.length === 0" class="empty">暂无教练</view>
    <view v-for="item in rows" :key="item.id" class="card" @click="open(item.id)">
      <text class="name">{{ item.name }}</text>
      <text class="meta">{{ item.intro }}</text>
    </view>
    <view v-for="pack in courses" :key="pack.id" class="card" @click="course(pack.id)">
      <text class="name">{{ pack.name }}</text>
      <text class="meta">{{ pack.lesson_count }} 课时 · {{ (pack.price_fen / 100).toFixed(2) }} 元</text>
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
  rows.value = await coaches(current.storeId);
  courses.value = await packs(current.storeId);
});
function open(id: string) { uni.navigateTo({ url: `/package-coach/pages/detail/detail?id=${id}` }); }
function course(id: string) { uni.navigateTo({ url: `/package-coach/pages/course/course?id=${id}` }); }
</script>
<style scoped>
.page { padding: 24rpx; } .card { margin-top: 16rpx; padding: 20rpx; background: #fff; border-radius: 16rpx; }
.name { font-weight: 600; } .meta, .empty { display: block; margin-top: 8rpx; color: #78716c; }
</style>
