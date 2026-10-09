<template>
  <view class="page">
    <input v-model="code" placeholder="器械编号，选填" />
    <textarea v-model="content" placeholder="故障说明" />
    <button @click="submit">提交报修</button>
    <view v-for="item in rows" :key="item.id" class="card">
      <text>{{ item.content }}</text>
      <text class="meta">{{ statusName(item.status) }}</text>
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
function statusName(status: string) {
  if (status === "PENDING") return "待处理";
  if (status === "DOING") return "处理中";
  if (status === "DONE") return "已完成";
  return status;
}
async function load() { rows.value = await myRepairs(); }
async function submit() {
  await createRepair(current.storeId, content.value, code.value);
  content.value = "";
  await load();
}
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card { margin-top: 16rpx; padding: 24rpx; background: #fff; border-radius: 24rpx; }
.meta { display: block; margin-top: 8rpx; color: #c2410c; font-size: 24rpx; }
input, textarea { width: 100%; margin-top: 16rpx; background: #fff; }
button { margin-top: 20rpx; }
</style>
