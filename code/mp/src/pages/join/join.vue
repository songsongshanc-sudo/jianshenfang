<template>
  <view class="page">
    <view class="hero">
      <text class="title">我要开店</text>
      <text class="text">{{ page.intro || "留下联系方式，顾问会和你沟通选址与投入。" }}</text>
      <text v-if="page.hotline" class="hot">咨询热线 {{ page.hotline }}</text>
    </view>
    <view v-if="page.points || page.support || page.steps" class="brief">
      <text v-if="page.points" class="text">{{ page.points }}</text>
      <text v-if="page.support" class="text">{{ page.support }}</text>
      <text v-if="page.steps" class="text">{{ page.steps }}</text>
    </view>
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
.hero { padding: 36rpx 32rpx; border-radius: 32rpx; background: #1c1917; color: #fff; }
.title { display: block; font-size: 44rpx; font-weight: 700; }
.text { display: block; margin-top: 12rpx; color: #d6d3d1; font-size: 26rpx; line-height: 1.6; }
.hot { display: block; margin-top: 20rpx; color: #fdba74; font-size: 28rpx; }
.brief { margin-top: 20rpx; padding: 24rpx; border-radius: 24rpx; background: #fff; }
.brief .text { color: #44403c; }
.form { margin-top: 20rpx; padding: 28rpx; border-radius: 28rpx; background: #fff; }
.label { display: block; margin-bottom: 8rpx; font-size: 30rpx; font-weight: 700; }
input { margin-top: 16rpx; background: #f6f3ee; }
.pair { display: flex; gap: 16rpx; }
.pair input { flex: 1; width: 0; }
button { margin-top: 24rpx; }
</style>
