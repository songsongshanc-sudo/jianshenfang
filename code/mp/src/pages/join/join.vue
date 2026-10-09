<template>
  <view class="page">
    <text class="title">我要开店</text>
    <text class="text">{{ page.intro }}</text>
    <text class="text">热线：{{ page.hotline }}</text>
    <text class="text">{{ page.points }}</text>
    <text class="text">{{ page.support }}</text>
    <text class="text">{{ page.steps }}</text>
    <input v-model="form.name" placeholder="姓名" />
    <input v-model="form.phone" placeholder="电话" />
    <input v-model="form.budget" placeholder="预计投入" />
    <input v-model="form.province" placeholder="省" />
    <input v-model="form.city" placeholder="市" />
    <button @click="submit">提交合作申请</button>
  </view>
</template>
<script setup lang="ts">
import { reactive, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { franchiseLead, franchisePage } from "../../api/shop";
const page = ref({ intro: "", hotline: "", points: "", support: "", steps: "" });
const form = reactive({ name: "", phone: "", budget: "", province: "", city: "" });
onShow(async () => { page.value = await franchisePage(); });
async function submit() {
  await franchiseLead(form);
  uni.showToast({ title: "已提交", icon: "none" });
}
</script>
<style scoped>
.page { padding: 24rpx; } .title { font-size: 36rpx; font-weight: 600; } .text { display: block; margin-top: 12rpx; color: #444; }
input { margin-top: 12rpx; background: #fff; padding: 12rpx; }
</style>
