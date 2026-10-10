<template>
  <view class="page">
    <rich-text v-if="html" class="content" :nodes="html" />
    <text v-else class="empty">公告不存在或已下线</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { notices } from "../../api/catalog";

const html = ref("");

onLoad(async (query) => {
  const id = (query?.id as string) || "";
  if (!id) return;
  try {
    const list = await notices();
    const hit = list.find((item) => item.id === id);
    html.value = hit?.content || "";
    if (html.value) {
      uni.setNavigationBarTitle({ title: "公告详情" });
    }
  } catch {
    html.value = "";
  }
});
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.content {
  display: block;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
  font-size: 28rpx;
  line-height: 1.7;
  color: #1a1a1a;
}
.empty { display: block; margin-top: 48rpx; text-align: center; color: #8a8f98; }
</style>
