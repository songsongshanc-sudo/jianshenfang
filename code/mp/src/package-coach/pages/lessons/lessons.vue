<template>
  <view class="page">
    <view v-if="rows.length === 0" class="empty">当前 0 节课时</view>
    <view v-for="item in rows" :key="item.pack_id" class="card">
      <text>{{ item.name }}</text>
      <text class="meta">剩余 {{ item.remaining }} 节</text>
      <button @click="show(item.pack_id)">出示课时码</button>
    </view>
    <view v-if="token" class="card"><text>60 秒内有效</text><text class="meta">{{ token }}</text></view>
    <input v-model="scan" placeholder="教练粘贴课时码" />
    <button @click="redeem">核销一节</button>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { lessonQr, myLessons, redeemLesson } from "../../../api/shop";
const rows = ref<Array<{ pack_id: string; name: string; remaining: number }>>([]);
const token = ref("");
const scan = ref("");
onShow(async () => { rows.value = await myLessons(); });
async function show(packId: string) { token.value = (await lessonQr(packId)).token; }
async function redeem() { await redeemLesson(scan.value); rows.value = await myLessons(); }
</script>
<style scoped>
.page { padding: 24rpx; } .card { margin-top: 16rpx; padding: 16rpx; background: #fff; } .meta, .empty { display: block; margin-top: 8rpx; color: #78716c; }
input { margin-top: 16rpx; background: #fff; padding: 12rpx; }
</style>
