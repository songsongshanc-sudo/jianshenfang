<template>
  <view class="page">
    <input v-model="code" placeholder="扫码或输入器械编号" />
    <button @click="lookup">查看教程</button>
    <view v-if="detail" class="card">
      <text class="name">{{ detail.name }}</text>
      <text class="meta">{{ detail.intro }}</text>
      <video v-if="detail.video_url" :src="detail.video_url" controls />
    </view>
    <view v-for="item in rows" :key="item.id" class="card" @click="open(item.code)">
      <text>{{ item.name }}</text>
      <text class="meta">{{ item.code }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { equipmentByCode, equipmentList } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const code = ref("");
const rows = ref<Array<{ id: string; code: string; name: string; intro: string; video_url: string }>>([]);
const detail = ref<{ name: string; intro: string; video_url: string } | null>(null);
onLoad(async (query) => {
  if (query?.code) code.value = String(query.code);
  rows.value = await equipmentList(current.storeId);
  if (code.value) await lookup();
});
async function lookup() {
  detail.value = await equipmentByCode(current.storeId, code.value);
}
function open(value: string) { code.value = value; lookup(); }
</script>
<style scoped>
.page { padding: 24rpx; } .card { margin-top: 16rpx; padding: 16rpx; background: #fff; }
.name { font-weight: 600; } .meta { display: block; margin-top: 8rpx; color: #555; }
input { background: #fff; padding: 12rpx; }
</style>
