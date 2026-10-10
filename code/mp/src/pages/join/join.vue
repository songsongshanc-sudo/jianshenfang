<template>
  <view class="page">
    <view class="hero">
      <text class="title">我要开店</text>
      <text v-if="page.hotline" class="hot">咨询热线 {{ page.hotline }}</text>
    </view>
    <view class="brief">
      <rich-text v-if="page.intro" :nodes="page.intro" />
      <text v-else class="text">留下联系方式，顾问会和你沟通选址与投入。</text>
    </view>
    <view v-if="page.points" class="brief"><rich-text :nodes="page.points" /></view>
    <view v-if="page.support" class="brief"><rich-text :nodes="page.support" /></view>
    <view v-if="page.steps" class="brief"><rich-text :nodes="page.steps" /></view>
    <view class="form">
      <text class="label">合作申请</text>
      <input v-model="form.name" placeholder="姓名" />
      <input v-model="form.phone" type="number" maxlength="11" placeholder="电话" />
      <input v-model="form.budget" placeholder="预计投入" />
      <view class="pair">
        <input v-model="form.province" placeholder="省" />
        <input v-model="form.city" placeholder="市" />
      </view>
      <button @click="submit">提交申请</button>
    </view>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { franchiseLead, franchisePage } from "../../api/shop";
const page = ref({ intro: "", hotline: "", points: "", support: "", steps: "" });
const form = reactive({ name: "", phone: "", budget: "", province: "", city: "" });
onShow(async () => {
  try {
    page.value = await franchisePage();
  } catch {
    page.value = { intro: "", hotline: "", points: "", support: "", steps: "" };
  }
});
async function submit() {
  await franchiseLead(form);
  uni.showToast({ title: "已提交", icon: "none" });
}
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.hero { padding: 36rpx 32rpx; border-radius: 20rpx; background: #1f2937; color: #fff; }
.title { display: block; font-size: 44rpx; font-weight: 700; }
.text { display: block; margin-top: 12rpx; color: #d1d5db; font-size: 26rpx; line-height: 1.6; }
.hot { display: block; margin-top: 20rpx; color: #fdba74; font-size: 28rpx; }
.brief { margin-top: 16rpx; padding: 24rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.brief .text { color: #404040; }
.form { margin-top: 16rpx; padding: 28rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.label { display: block; margin-bottom: 8rpx; font-size: 30rpx; font-weight: 700; }
input { margin-top: 16rpx; height: 88rpx; padding: 0 24rpx; line-height: 88rpx; background: #f5f6f8; }
.pair { display: flex; gap: 16rpx; }
.pair input { flex: 1; width: 0; }
button { margin-top: 24rpx; }
</style>
