<template>
  <view class="page">
    <text class="title">{{ doc?.title || "会员协议" }}</text>
    <rich-text v-if="doc" class="content" :nodes="contentHtml" />
    <text v-else class="empty">还没有发布会员协议</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { currentAgreement, type AgreementDoc } from "../../../api/order";
import { fitRichHtml } from "../../../utils/richHtml";

const doc = ref<AgreementDoc | null>(null);
const contentHtml = computed(() => fitRichHtml(doc.value?.content));

onShow(async () => {
  try {
    doc.value = await currentAgreement();
  } catch {
    doc.value = null;
  }
});
</script>

<style scoped>
.page { padding: 24rpx; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.content {
  display: block;
  width: 100%;
  overflow: hidden;
  margin-top: 20rpx;
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
  font-size: 28rpx;
  line-height: 1.7;
  box-sizing: border-box;
}
.empty { display: block; margin-top: 20rpx; color: #78716c; }
</style>
