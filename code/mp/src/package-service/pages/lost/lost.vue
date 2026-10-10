<template>
  <view class="page">
    <view class="tabs">
      <text @click="tab = 'ALL'">本店</text>
      <text @click="tab = 'MINE_LOST'">我丢失</text>
      <text @click="tab = 'MINE_FOUND'">我捡到</text>
    </view>
    <input v-model="name" placeholder="名称" />
    <textarea v-model="content" placeholder="说明" />
    <button @click="submit('LOST')">我丢失了</button>
    <button @click="submit('FOUND')">我捡到了</button>
    <view v-for="item in rows" :key="item.id" class="card">
      <text>{{ item.name }}</text>
      <text class="meta">{{ item.content }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref, watch } from "vue";
import { onLoad, onShow } from "@dcloudio/uni-app";
import { createLost, lostFeed } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const queryStoreId = ref("");
const tab = ref("ALL");
const name = ref("");
const content = ref("");
const rows = ref<Array<{ id: string; name: string; content: string; kind: string }>>([]);
function storeKey() {
  return queryStoreId.value || current.storeId;
}
watch(tab, load);
onLoad((query) => {
  queryStoreId.value = String(query?.storeId || "");
});
onShow(load);
async function load() { rows.value = await lostFeed(storeKey(), tab.value); }
async function submit(kind: string) {
  await createLost(storeKey(), kind, name.value, content.value, true);
  name.value = "";
  content.value = "";
  await load();
}
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.tabs { display: flex; gap: 12rpx; }
.tabs text { padding: 12rpx 20rpx; border-radius: 999rpx; background: #fff; font-size: 24rpx; }
.card { margin-top: 16rpx; padding: 24rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.meta { display: block; margin-top: 8rpx; color: #78716c; }
input { width: 100%; height: 88rpx; margin-top: 16rpx; padding: 0 24rpx; line-height: 88rpx; background: #fff; }
textarea { width: 100%; min-height: 200rpx; margin-top: 16rpx; padding: 20rpx 24rpx; line-height: 44rpx; background: #fff; }
button { margin-top: 16rpx; }
</style>
