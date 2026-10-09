<template>
  <view class="page">
    <view v-for="item in rows" :key="item.id" class="card" @click="read(item.id)">
      <text>{{ item.title }}</text>
      <text class="meta">{{ item.content }}</text>
      <text class="meta">{{ item.read_at ? "已读" : "未读" }}</text>
    </view>
    <view v-if="rows.length === 0" class="empty">暂无消息</view>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { messages, readMessage } from "../../../api/shop";
const rows = ref<Array<{ id: string; title: string; content: string; read_at: string | null }>>([]);
onShow(async () => { rows.value = await messages(); });
async function read(id: string) { await readMessage(id); rows.value = await messages(); }
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card { margin-top: 16rpx; padding: 24rpx; border-radius: 24rpx; background: #fff; }
.meta, .empty { display: block; margin-top: 8rpx; color: #78716c; font-size: 24rpx; }
</style>
