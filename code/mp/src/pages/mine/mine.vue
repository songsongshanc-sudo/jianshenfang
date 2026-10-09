<template>
  <view class="page">
    <view class="hero">
      <view class="hero-top">
        <view class="avatar">{{ initial }}</view>
        <view class="who">
          <text class="nick">{{ profile?.nickname || (profile ? "会员" : "还未登录") }}</text>
          <text class="sub">{{ profile ? `已陪伴您 ${profile.companionDays} 天` : "登录后查看会员编号" }}</text>
        </view>
        <view v-if="profile" class="gear-wrap" @click="settings = true">
          <image class="gear" src="/static/icon/sliders-light.png" mode="aspectFit" />
        </view>
      </view>
      <view v-if="profile" class="stats">
        <view class="stat">
          <text class="stat-label">会员编号</text>
          <text class="stat-value">{{ profile.memberNo || "—" }}</text>
        </view>
        <view class="stat">
          <text class="stat-label">当前门店</text>
          <text class="stat-value">{{ profile.storeMember ? "会员有效" : "未开通" }}</text>
        </view>
      </view>
      <text v-if="profile?.consecutiveRemain != null" class="remain">激活连续月卡还需 {{ profile.consecutiveRemain }} 天</text>
      <text v-if="profile?.faceSync === 'PENDING'" class="remain">人脸正在同步到门店闸机</text>
      <text v-if="profile?.faceSync === 'FAILED'" class="remain">人脸同步失败，请联系门店重试</text>
      <button v-if="!profile" class="hero-btn" @click="goLogin">手机号登录</button>
      <button v-else-if="!profile.storeMember" class="hero-btn" @click="buy">开通会员</button>
    </view>

    <view v-if="profile" class="panel">
      <text class="panel-title">常用</text>
      <view class="grid">
        <view class="cell" @click="buy"><view class="bubble"><image class="ico" src="/static/icon/card.png" mode="aspectFit" /></view><text>会员卡</text></view>
        <view class="cell" @click="orders"><view class="bubble"><image class="ico" src="/static/icon/order.png" mode="aspectFit" /></view><text>订单</text></view>
        <view class="cell" @click="lessons"><view class="bubble"><image class="ico" src="/static/icon/lesson.png" mode="aspectFit" /></view><text>课程</text></view>
        <view class="cell" @click="groupon = true"><view class="bubble"><image class="ico" src="/static/icon/ticket.png" mode="aspectFit" /></view><text>团购核销</text></view>
      </view>
    </view>
    <view v-if="profile" class="panel">
      <text class="panel-title">门店服务</text>
      <view class="grid">
        <view class="cell" @click="go('/package-service/pages/repair/repair')"><view class="bubble"><image class="ico" src="/static/icon/wrench.png" mode="aspectFit" /></view><text>器械报修</text></view>
        <view class="cell" @click="go('/package-service/pages/complaint/complaint')"><view class="bubble"><image class="ico" src="/static/icon/chat.png" mode="aspectFit" /></view><text>投诉建议</text></view>
        <view class="cell" @click="go('/package-service/pages/lost/lost')"><view class="bubble"><image class="ico" src="/static/icon/box.png" mode="aspectFit" /></view><text>失物招领</text></view>
        <view class="cell" @click="go('/package-service/pages/equipment/equipment')"><view class="bubble"><image class="ico" src="/static/icon/dumbbell.png" mode="aspectFit" /></view><text>器械指导</text></view>
        <view class="cell" @click="go('/package-service/pages/review/review')"><view class="bubble"><image class="ico" src="/static/icon/star.png" mode="aspectFit" /></view><text>好评赠卡</text></view>
        <view class="cell" @click="go('/package-service/pages/messages/messages')"><view class="bubble"><image class="ico" src="/static/icon/bell.png" mode="aspectFit" /></view><text>消息</text></view>
        <view class="cell" @click="student"><view class="bubble"><image class="ico" src="/static/icon/cap.png" mode="aspectFit" /></view><text>学生认证</text></view>
      </view>
    </view>

    <view v-if="groupon" class="mask" @click="groupon = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">团购核销</text>
        <text class="sheet-hint">没有匹配规则不会开通会员。</text>
        <picker :range="platformLabels" @change="onPlatform">
          <view class="field">平台：{{ platformLabels[platformIndex] }}</view>
        </picker>
        <input v-model="code" class="field" placeholder="券码" />
        <button class="sheet-btn" @click="submitGroupon">提交核销</button>
        <view class="cancel" @click="groupon = false">取消</view>
      </view>
    </view>
    <view v-if="settings" class="mask" @click="settings = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">设置</text>
        <view class="sheet-row" @click="agreement">会员协议</view>
        <view class="sheet-row danger" @click="logout">退出登录</view>
        <view class="cancel" @click="settings = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { me, type MeProfile } from "../../api/catalog";
import { redeemGroupon, studentStatus } from "../../api/shop";
import { useCurrentStore } from "../../stores/currentStore";

const profile = ref<MeProfile | null>(null);
const current = useCurrentStore();
const groupon = ref(false);
const settings = ref(false);
const platforms = ["MEITUAN", "DOUYIN"];
const platformLabels = ["美团", "抖音"];
const platformIndex = ref(0);
const code = ref("");
const initial = computed(() => (profile.value?.nickname || "会").slice(0, 1));

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

function goLogin() {
  uni.navigateTo({ url: "/pages/login/login" });
}

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
  platformIndex.value = Number(event.detail.value);
}

async function submitGroupon() {
  if (!current.storeId) {
    uni.showToast({ title: "请先选择门店", icon: "none" });
    return;
  }
  await redeemGroupon(current.storeId, platforms[platformIndex.value], code.value);
  uni.showToast({ title: "核销成功", icon: "none" });
  groupon.value = false;
}

async function student() {
  const status = await studentStatus();
  uni.showToast({ title: status.status === "UNVERIFIED" ? "未认证" : status.status, icon: "none" });
}
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.hero { padding: 36rpx 32rpx; border-radius: 32rpx; background: #1c1917; color: #fff; }
.hero-top { display: flex; align-items: center; gap: 20rpx; }
.avatar { width: 96rpx; height: 96rpx; border-radius: 32rpx; background: #c2410c; text-align: center; line-height: 96rpx; font-size: 40rpx; font-weight: 700; }
.who { flex: 1; min-width: 0; }
.nick { display: block; font-size: 36rpx; font-weight: 700; }
.sub { display: block; margin-top: 8rpx; color: #d6d3d1; font-size: 24rpx; }
.gear-wrap { width: 72rpx; height: 72rpx; border-radius: 24rpx; background: rgba(255,255,255,0.08); display: flex; align-items: center; justify-content: center; }
.gear { width: 36rpx; height: 36rpx; }
.stats { display: flex; gap: 16rpx; margin-top: 28rpx; }
.stat { flex: 1; padding: 20rpx; border-radius: 20rpx; background: rgba(255, 255, 255, 0.08); }
.stat-label { display: block; color: #a8a29e; font-size: 22rpx; }
.stat-value { display: block; margin-top: 8rpx; font-size: 30rpx; font-weight: 700; }
.remain { display: block; margin-top: 16rpx; color: #fdba74; font-size: 24rpx; }
.hero-btn { margin: 28rpx 0 0; background: #c2410c; color: #fff; }
.panel { margin-top: 24rpx; padding: 12rpx 8rpx 8rpx; border-radius: 28rpx; background: #fff; }
.panel-title { display: block; padding: 16rpx 20rpx 0; font-size: 28rpx; font-weight: 700; }
.grid { display: flex; flex-wrap: wrap; }
.cell { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 10rpx; padding: 20rpx 0; font-size: 22rpx; color: #44403c; }
.bubble { width: 88rpx; height: 88rpx; border-radius: 28rpx; background: #f6f3ee; display: flex; align-items: center; justify-content: center; }
.ico { width: 40rpx; height: 40rpx; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(28, 25, 23, 0.45); display: flex; align-items: flex-end; z-index: 20; }
.sheet { width: 100%; padding: 28rpx 28rpx 48rpx; border-radius: 28rpx 28rpx 0 0; background: #fff; box-sizing: border-box; }
.sheet-title { display: block; font-size: 32rpx; font-weight: 700; }
.sheet-hint { display: block; margin-top: 8rpx; color: #78716c; font-size: 24rpx; }
.field { margin-top: 16rpx; }
.sheet-btn { margin-top: 20rpx; }
.sheet-row { padding: 28rpx 4rpx; border-bottom: 1px solid #f5f5f4; font-size: 30rpx; }
.danger { color: #b91c1c; }
.cancel { margin-top: 12rpx; padding: 24rpx; text-align: center; color: #78716c; }
</style>
