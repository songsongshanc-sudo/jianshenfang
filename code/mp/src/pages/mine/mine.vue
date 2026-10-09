<template>
  <view class="page">
    <view class="head">
      <view>
        <text class="title">我的</text>
        <text v-if="profile" class="sub">{{ profile.nickname || "会员" }}</text>
      </view>
      <text class="gear" @click="settings = true">设置</text>
    </view>
    <view v-if="!profile" class="empty">登录后可以查看会员编号和连续天数</view>
    <view v-else class="card">
      <text class="line">已陪伴您 {{ profile.companionDays }} 天</text>
      <text v-if="profile.consecutiveRemain != null" class="line">激活连续月卡还需 {{ profile.consecutiveRemain }} 天</text>
      <text v-if="profile.memberNo" class="line">会员编号：{{ profile.memberNo }}</text>
      <text class="line">{{ profile.storeMember ? "当前门店会员有效" : "尚未开通会员" }}</text>
      <button v-if="!profile.storeMember" class="inline" @click="buy">去购买</button>
    </view>
    <button v-if="profile" @click="buy">会员卡购买</button>
    <button v-if="profile" @click="orders">我的订单</button>
    <button v-if="profile" @click="lessons">我的课程</button>
    <button v-if="profile" @click="groupon = true">团购核销</button>
    <button v-if="profile" @click="go('/package-service/pages/repair/repair')">器械报修</button>
    <button v-if="profile" @click="go('/package-service/pages/complaint/complaint')">投诉建议</button>
    <button v-if="profile" @click="go('/package-service/pages/lost/lost')">失物招领</button>
    <button v-if="profile" @click="go('/package-service/pages/equipment/equipment')">器械指导</button>
    <button v-if="profile" @click="go('/package-service/pages/review/review')">好评赠卡</button>
    <button v-if="profile" @click="go('/package-service/pages/messages/messages')">消息</button>
    <button v-if="profile" @click="student">学生认证</button>
    <view v-if="groupon" class="sheet">
      <text class="line">选择平台并填写券码。没有匹配规则不会开通会员。</text>
      <picker :range="platforms" @change="onPlatform">
        <view class="line">平台：{{ platform }}</view>
      </picker>
      <input v-model="code" placeholder="券码" />
      <button @click="submitGroupon">提交核销</button>
      <button @click="groupon = false">关闭</button>
    </view>
    <view v-if="settings" class="sheet">
      <button @click="agreement">会员协议</button>
      <button @click="logout">退出登录</button>
      <button @click="settings = false">关闭</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { me, type MeProfile } from "../../api/catalog";
import { redeemGroupon, studentStatus } from "../../api/shop";
import { useCurrentStore } from "../../stores/currentStore";

const profile = ref<MeProfile | null>(null);
const current = useCurrentStore();
const groupon = ref(false);
const settings = ref(false);
const platforms = ["MEITUAN", "DOUYIN"];
const platform = ref("MEITUAN");
const code = ref("");

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    profile.value = null;
    return;
  }
  try {
    profile.value = await me(current.storeId || undefined);
  } catch {
    profile.value = null;
  }
});

function buy() {
  if (!current.storeId) {
    uni.showToast({ title: "请先选择门店", icon: "none" });
    return;
  }
  uni.navigateTo({ url: `/package-card/pages/open/open?storeId=${current.storeId}` });
}

function orders() {
  uni.navigateTo({ url: "/package-card/pages/orders/orders" });
}

function lessons() {
  uni.navigateTo({ url: "/package-coach/pages/lessons/lessons" });
}

function go(url: string) {
  uni.navigateTo({ url });
}

function agreement() {
  uni.navigateTo({ url: "/package-card/pages/agreement/agreement" });
}

function logout() {
  uni.removeStorageSync("mpToken");
  uni.removeStorageSync("mpTokenType");
  profile.value = null;
  settings.value = false;
}

function onPlatform(event: { detail: { value: number } }) {
  platform.value = platforms[event.detail.value];
}

async function submitGroupon() {
  if (!current.storeId) {
    uni.showToast({ title: "请先选择门店", icon: "none" });
    return;
  }
  await redeemGroupon(current.storeId, platform.value, code.value);
  uni.showToast({ title: "核销成功", icon: "none" });
  groupon.value = false;
}

async function student() {
  const status = await studentStatus();
  uni.showToast({ title: status.status === "UNVERIFIED" ? "未认证" : status.status, icon: "none" });
}
</script>

<style scoped>
.page { padding: 24rpx; }
.head { display: flex; justify-content: space-between; align-items: center; }
.title { display: block; font-size: 40rpx; font-weight: 700; }
.sub, .line, .gear { display: block; margin-top: 10rpx; font-size: 28rpx; }
.empty { margin-top: 24rpx; color: #78716c; }
.card, .sheet { margin-top: 24rpx; padding: 24rpx; border-radius: 16rpx; background: #fff; }
button { margin-top: 16rpx; }
.inline { margin-top: 16rpx; }
input { margin-top: 16rpx; padding: 16rpx; background: #f4f0ea; }
</style>
