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
import { onLoad, onShow } from "@dcloudio/uni-app";
import { createRepair, myRepairs } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const code = ref("");
const content = ref("");
const rows = ref<Array<{ id: string; content: string; status: string }>>([]);
const queryStoreId = ref("");
onLoad((query) => {
  queryStoreId.value = String(query?.storeId || "");
});
onShow(load);
function statusName(status: string) {
  if (status === "PENDING") return "待处理";
  if (status === "DOING") return "处理中";
  if (status === "DONE") return "已完成";
  return status;
}
async function load() { rows.value = await myRepairs(); }
async function submit() {
  await createRepair(queryStoreId.value || current.storeId, content.value, code.value);
  content.value = "";
  await load();
}
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card { margin-top: 16rpx; padding: 24rpx; background: #fff; border-radius: 20rpx; border: 1px solid #eceff3; }
.meta { display: block; margin-top: 8rpx; color: #ea580c; font-size: 24rpx; }
input { width: 100%; height: 88rpx; margin-top: 16rpx; padding: 0 24rpx; line-height: 88rpx; background: #fff; }
textarea { width: 100%; min-height: 200rpx; margin-top: 16rpx; padding: 20rpx 24rpx; line-height: 44rpx; background: #fff; }
button { margin-top: 20rpx; }
</style>
