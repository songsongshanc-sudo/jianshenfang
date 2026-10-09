<template>
  <view class="page">
    <input v-model="code" placeholder="器械编号，选填" />
    <textarea v-model="content" placeholder="故障说明" />
    <button @click="submit">提交报修</button>
    <view v-for="item in rows" :key="item.id" class="card">
      <text>{{ item.content }}</text>
      <text class="meta">{{ item.status }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { createRepair, myRepairs } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const code = ref("");
const content = ref("");
const rows = ref<Array<{ id: string; content: string; status: string }>>([]);
onShow(load);
async function load() { rows.value = await myRepairs(); }
async function submit() {
  await createRepair(current.storeId, content.value, code.value);
  content.value = "";
  await load();
}
</script>
<style scoped>
.page { padding: 24rpx; } .card { margin-top: 16rpx; padding: 16rpx; background: #fff; } .meta { display: block; color: #78716c; }
input, textarea { margin-top: 12rpx; padding: 12rpx; background: #fff; width: 100%; }
</style>
