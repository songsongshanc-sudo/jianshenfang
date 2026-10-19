<template>
  <view class="page">
    <view class="hero" :class="{ guest: !profile }">
      <view class="hero-top">
        <view class="avatar">{{ initial }}</view>
        <view class="who" @click="goProfileOrLogin">
          <text class="nick">{{ profile ? (profile.nickname || "会员") : "欢迎来到健身房" }} <text v-if="profile" class="arrow">›</text></text>
          <text class="sub">{{ profile ? `已陪伴您 ${profile.companionDays} 天` : "新用户注册，或用门店给的绑定码登录" }}</text>
          <text v-if="profile" class="sub">会员编号：{{ profile.memberNo || "—" }}</text>
        </view>
        <view v-if="profile" class="gear-wrap" @click="goProfile">
          <image class="gear" src="/static/icon/sliders.png" mode="aspectFit" />
        </view>
      </view>
      <text v-if="profile?.consecutiveRemain != null" class="remain">激活连续月卡还需 {{ profile.consecutiveRemain }} 天</text>
      <text v-if="profile?.faceSync === 'PENDING'" class="remain">人脸正在同步到门店闸机</text>
      <text v-if="profile?.faceSync === 'FAILED'" class="remain">人脸同步失败，请联系门店重试</text>
      <view v-if="!profile" class="hero-actions">
        <button class="hero-btn" @click="goLogin">立即注册</button>
        <button class="hero-btn ghost" @click="goInvite">绑定码登录</button>
      </view>
    </view>

    <view class="buy-card" @click="requireMember(buy)">
      <view>
        <text class="buy-title">会员卡购买</text>
        <text class="buy-sub">{{ profile?.storeMember ? "续费后可继续进店训练" : "开通即可进店训练、购买课程" }}</text>
      </view>
      <view class="buy-btn">{{ profile ? "去购买" : "登录后购买" }}</view>
    </view>

    <view v-if="profile && isCoach" class="buy-card coach-card" @click="lessons">
      <view>
        <text class="buy-title">教练工作台</text>
        <text class="buy-sub">扫码核销 · 预约同意/拒绝 · 申诉退课</text>
      </view>
      <view class="buy-btn">去处理</view>
    </view>

    <view class="pair">
      <view class="pair-item" @click="requireMember(lessons)">
        <view class="pair-ico"><image class="ico" src="/static/icon/lesson.png" mode="aspectFit" /></view>
        <view>
          <text class="pair-title">我的课程</text>
          <text class="pair-sub">{{ isCoach ? "核销 / 课时码" : "查看课时码" }}</text>
        </view>
      </view>
      <view class="pair-item" @click="requireMember(openGroupon)">
        <view class="pair-ico"><image class="ico" src="/static/icon/ticket.png" mode="aspectFit" /></view>
        <view>
          <text class="pair-title">团购核销</text>
          <text class="pair-sub">美团 / 抖音</text>
        </view>
      </view>
    </view>

    <view class="panel">
      <text class="panel-title">会员中心</text>
      <view class="grid">
        <view class="cell" @click="requireMember(() => go('/package-service/pages/messages/messages'))">
          <view class="bubble">
            <image class="ico" src="/static/icon/bell.png" mode="aspectFit" />
            <view v-if="profile && unread > 0" class="badge">{{ unread > 99 ? "99+" : unread }}</view>
          </view>
          <text>系统消息</text>
        </view>
        <view class="cell" @click="requireMember(orders)"><view class="bubble"><image class="ico" src="/static/icon/order.png" mode="aspectFit" /></view><text>会员订单</text></view>
        <view class="cell" @click="requireMember(agreement)"><view class="bubble"><image class="ico" src="/static/icon/info.png" mode="aspectFit" /></view><text>会员协议</text></view>
        <view class="cell" @click="requireMember(goProfile)"><view class="bubble"><image class="ico" src="/static/icon/camera.png" mode="aspectFit" /></view><text>会员资料</text></view>
      </view>
    </view>
    <view class="panel">
      <text class="panel-title">门店服务</text>
      <view class="grid">
        <view class="cell" @click="requireMember(() => go('/package-service/pages/repair/repair'))"><view class="bubble"><image class="ico" src="/static/icon/wrench.png" mode="aspectFit" /></view><text>器械报修</text></view>
        <view class="cell" @click="requireMember(() => go('/package-service/pages/complaint/complaint'))"><view class="bubble"><image class="ico" src="/static/icon/chat.png" mode="aspectFit" /></view><text>投诉建议</text></view>
        <view class="cell" @click="requireMember(() => go('/package-service/pages/lost/lost'))"><view class="bubble"><image class="ico" src="/static/icon/box.png" mode="aspectFit" /></view><text>失物招领</text></view>
        <view class="cell" @click="requireMember(() => go('/package-service/pages/equipment/equipment'))"><view class="bubble"><image class="ico" src="/static/icon/dumbbell.png" mode="aspectFit" /></view><text>器械指导</text></view>
      </view>
    </view>

    <view v-if="groupon" class="mask" @click="groupon = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">团购核销</text>
        <text class="sheet-hint">没有匹配规则不会开通会员。</text>
        <picker :range="platformLabels" @change="onPlatform">
          <view class="field-line">平台：{{ platformLabels[platformIndex] }}</view>
        </picker>
        <input v-model="code" placeholder="券码" />
        <button class="sheet-btn" @click="submitGroupon">提交核销</button>
        <view class="cancel" @click="groupon = false">取消</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { me, type MeProfile } from "../../api/catalog";
import { coachMe, redeemGroupon, unreadMessageCount } from "../../api/shop";
import { useCurrentStore } from "../../stores/currentStore";

const profile = ref<MeProfile | null>(null);
const isCoach = ref(false);
const unread = ref(0);
const current = useCurrentStore();
const groupon = ref(false);
const platforms = ["MEITUAN", "DOUYIN"];
const platformLabels = ["美团", "抖音"];
const platformIndex = ref(0);
const code = ref("");
const initial = computed(() => (profile.value?.nickname || "会").slice(0, 1));

onShow(async () => {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    profile.value = null;
    isCoach.value = false;
    unread.value = 0;
    return;
  }
  try {
    profile.value = await me(current.storeId || undefined);
  } catch {
    profile.value = null;
  }
  try {
    isCoach.value = !!(await coachMe()).coach;
  } catch {
    isCoach.value = false;
  }
  try {
    unread.value = Number((await unreadMessageCount()).count || 0);
  } catch {
    unread.value = 0;
  }
});

function goLogin() {
  uni.navigateTo({ url: "/pages/login/login" });
}

function goInvite() {
  uni.navigateTo({ url: "/pages/login/login?mode=invite" });
}

function requireMember(action: () => void) {
  if (!profile.value) {
    goLogin();
    return;
  }
  action();
}

function openGroupon() {
  groupon.value = true;
}

function goProfile() {
  uni.navigateTo({ url: "/pages/profile/profile" });
}

function goProfileOrLogin() {
  if (profile.value) {
    goProfile();
    return;
  }
  goLogin();
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
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.hero {
  padding: 32rpx 28rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.hero.guest {
  background: linear-gradient(160deg, #1f2937 0%, #111827 70%);
  border: none;
  color: #fff;
}
.hero.guest .sub { color: rgba(255, 255, 255, 0.72); }
.hero-actions { display: flex; flex-direction: column; gap: 16rpx; margin-top: 28rpx; }
.hero.guest .hero-btn { margin: 0; background: #ea580c; color: #fff; }
.hero.guest .hero-btn.ghost { background: transparent; color: #fff; border: 1px solid rgba(255, 255, 255, 0.45); }
.hero-top { display: flex; align-items: center; gap: 20rpx; }
.avatar { width: 96rpx; height: 96rpx; border-radius: 50%; background: #ea580c; color: #fff; text-align: center; line-height: 96rpx; font-size: 40rpx; font-weight: 700; }
.who { flex: 1; min-width: 0; }
.nick { display: block; font-size: 36rpx; font-weight: 700; }
.arrow { font-weight: 400; color: #8a8f98; }
.sub { display: block; margin-top: 6rpx; color: #8a8f98; font-size: 24rpx; }
.gear-wrap { width: 72rpx; height: 72rpx; border-radius: 16rpx; background: #f5f6f8; display: flex; align-items: center; justify-content: center; }
.gear { width: 36rpx; height: 36rpx; }
.remain { display: block; margin-top: 16rpx; color: #ea580c; font-size: 24rpx; }
.hero-btn { margin: 24rpx 0 0; }
.buy-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 16rpx;
  padding: 32rpx 28rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
  border-left: 8rpx solid #ea580c;
}
.coach-card { background: #fff7ed; border-color: #fdba74; }
.buy-title { display: block; font-size: 34rpx; font-weight: 800; color: #1a1a1a; }
.buy-sub { display: block; margin-top: 8rpx; font-size: 24rpx; color: #8a8f98; }
.buy-btn { padding: 12rpx 28rpx; border-radius: 12rpx; background: #ea580c; color: #fff; font-size: 26rpx; font-weight: 700; flex-shrink: 0; }
.pair { display: flex; gap: 16rpx; margin-top: 16rpx; }
.pair-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 20rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.pair-ico { width: 72rpx; height: 72rpx; border-radius: 16rpx; background: #fff4ed; display: flex; align-items: center; justify-content: center; }
.pair-title { display: block; font-size: 28rpx; font-weight: 700; }
.pair-sub { display: block; margin-top: 6rpx; color: #8a8f98; font-size: 22rpx; }
.panel {
  margin-top: 16rpx;
  padding: 12rpx 8rpx 8rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.panel-title { display: block; padding: 16rpx 20rpx 0; font-size: 28rpx; font-weight: 700; }
.grid { display: flex; flex-wrap: wrap; }
.cell { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 10rpx; padding: 20rpx 0; font-size: 22rpx; color: #404040; }
.bubble { position: relative; width: 80rpx; height: 80rpx; border-radius: 20rpx; background: #f8fafc; display: flex; align-items: center; justify-content: center; }
.badge {
  position: absolute;
  top: -8rpx;
  right: -12rpx;
  min-width: 32rpx;
  padding: 0 8rpx;
  height: 32rpx;
  line-height: 32rpx;
  border-radius: 999rpx;
  background: #ea580c;
  color: #fff;
  font-size: 18rpx;
  text-align: center;
}
.ico { width: 38rpx; height: 38rpx; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(17, 24, 39, 0.45); display: flex; align-items: flex-end; z-index: 20; }
.sheet { width: 100%; padding: 28rpx 28rpx 48rpx; border-radius: 24rpx 24rpx 0 0; background: #fff; box-sizing: border-box; }
.sheet-title { display: block; font-size: 32rpx; font-weight: 700; }
.sheet-hint { display: block; margin: 8rpx 0 16rpx; color: #8a8f98; font-size: 24rpx; }
.field-line { padding: 0 24rpx; height: 88rpx; line-height: 88rpx; border-radius: 16rpx; background: #f5f6f8; border: 1px solid #eceff3; }
input { margin-top: 16rpx; background: #f5f6f8; }
.sheet-btn { margin-top: 20rpx; }
.sheet-row { padding: 28rpx 4rpx; border-bottom: 1px solid #eceff3; font-size: 30rpx; }
.cancel { margin-top: 12rpx; padding: 24rpx; text-align: center; color: #8a8f98; }
</style>
