<template>
  <view class="page">
    <view class="card">
      <text class="label">昵称</text>
      <input v-model="nickname" maxlength="32" placeholder="填写称呼，方便客服联系你" />
      <button class="save" @click="save">保存昵称</button>
    </view>
    <view class="card">
      <text class="label">人脸照片</text>
      <text class="hint">闸机用这张正脸识别进店。更换后会重新同步到门店设备。</text>
      <button class="ghost" @click="replaceFace">拍摄新的正脸</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { me, updateProfile } from "../../api/catalog";
import { useCurrentStore } from "../../stores/currentStore";

const current = useCurrentStore();
const nickname = ref("");

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.redirectTo({ url: "/pages/login/login" });
    return;
  }
  try {
    const profile = await me(current.storeId || undefined);
    nickname.value = profile.nickname || "";
  } catch {
    nickname.value = "";
  }
});

async function save() {
  await updateProfile(nickname.value.trim());
  uni.showToast({ title: "已保存", icon: "none" });
}

function replaceFace() {
  uni.navigateTo({ url: "/pages/face/face?mode=replace" });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card {
  margin-bottom: 16rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.label { display: block; margin-bottom: 16rpx; font-size: 30rpx; font-weight: 700; }
.hint { display: block; margin-bottom: 20rpx; color: #8a8f98; font-size: 24rpx; line-height: 1.6; }
input { background: #f5f6f8; }
.save { margin-top: 20rpx; }
.ghost { margin: 0; background: #fff4ed; color: #c2410c; }
</style>
