<template>
  <view class="page">
    <text class="hint">这里是小程序自己的投诉渠道。</text>
    <picker :range="labels" @change="onPick"><view class="line">类型：{{ labels[index] }}</view></picker>
    <textarea v-model="content" placeholder="投诉内容" />
    <button @click="submit">提交</button>
    <view v-for="item in rows" :key="item.id" class="card">
      <text>{{ item.content }}</text>
      <text class="meta">{{ statusName(item.status) }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { createComplaint, myComplaints } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const labels = ["器械", "卫生", "教练", "其他"];
const values = ["EQUIPMENT", "CLEAN", "COACH", "OTHER"];
const index = ref(0);
const content = ref("");
const rows = ref<Array<{ id: string; content: string; status: string }>>([]);
onShow(async () => { rows.value = await myComplaints(); });
function statusName(status: string) {
  if (status === "PENDING") return "待处理";
  if (status === "DOING") return "处理中";
  if (status === "DONE") return "已完成";
  return status;
}
function onPick(event: { detail: { value: number } }) { index.value = Number(event.detail.value); }
async function submit() {
  await createComplaint(current.storeId, values[index.value], content.value);
  content.value = "";
  rows.value = await myComplaints();
}
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.hint { display: block; color: #78716c; font-size: 24rpx; }
.line { margin-top: 16rpx; padding: 22rpx 24rpx; border-radius: 20rpx; background: #fff; }
.card { margin-top: 16rpx; padding: 24rpx; border-radius: 24rpx; background: #fff; }
.meta { display: block; margin-top: 8rpx; color: #c2410c; font-size: 24rpx; }
textarea { width: 100%; margin-top: 16rpx; min-height: 180rpx; background: #fff; }
button { margin-top: 20rpx; }
</style>
