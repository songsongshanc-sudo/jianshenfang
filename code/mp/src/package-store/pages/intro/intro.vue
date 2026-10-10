<template>
  <view class="page">
    <view v-if="!storeId" class="empty">请先选择门店</view>
    <template v-else-if="store">
      <image v-if="store.coverUrl" class="cover" :src="store.coverUrl" mode="aspectFill" />
      <view v-else class="cover cover-empty">
        <text class="cover-mark">{{ store.name.slice(0, 1) }}</text>
      </view>

      <view class="card">
        <view class="tags">
          <text v-if="isCurrent" class="tag current">当前门店</text>
          <text v-if="distance" class="tag">{{ distance }}</text>
          <text class="tag ghost">{{ store.status === "OPEN" ? (store.businessHours || "营业中") : "暂停营业" }}</text>
        </view>
        <text class="name">{{ store.name }}{{ store.city ? `（${store.city}）` : "" }}</text>
        <view class="addr" @click="navigate">
          <image class="addr-ico" src="/static/icon/pin.png" mode="aspectFit" />
          <text class="addr-text">{{ store.province }}{{ store.city }}{{ store.address }}</text>
          <text class="addr-more">›</text>
        </view>
        <view class="row" @click="onOnlineClick">
          <text class="row-label">在线人数</text>
          <text class="row-value" :class="{ link: store.onlineLocked }">
            {{ store.onlineLocked ? "开通会员后查看" : store.onlineText }}
          </text>
        </view>
        <view v-if="wifi.ssid" class="row wifi">
          <view class="wifi-body">
            <text class="row-label">账号：{{ wifi.ssid }}</text>
            <text class="row-value">密码：{{ wifi.password || "无密码" }}</text>
          </view>
          <text class="copy" @click="copyWifi">复制</text>
        </view>
        <view v-else-if="loggedIn === false" class="row muted">登录后可查看本店 WiFi</view>
        <button v-if="!isCurrent" class="set-btn" @click="setCurrent">设为当前门店</button>
      </view>

      <view class="panel">
        <view class="bigs">
          <view class="big" @click="goGuide">
            <image class="big-ico" src="/static/icon/route.png" mode="aspectFit" />
            <text>门店指引</text>
          </view>
          <view class="big" @click="goService('/package-service/pages/equipment/equipment')">
            <image class="big-ico" src="/static/icon/dumbbell.png" mode="aspectFit" />
            <text>器械指导</text>
          </view>
          <view class="big" @click="goService('/package-service/pages/review/review')">
            <image class="big-ico" src="/static/icon/star.png" mode="aspectFit" />
            <text>好评赠卡</text>
          </view>
          <view class="big" @click="goVisits">
            <image class="big-ico" src="/static/icon/card.png" mode="aspectFit" />
            <text>健身记录</text>
          </view>
        </view>
        <view class="smalls">
          <view class="small" @click="goService('/package-service/pages/lost/lost')">失物招领</view>
          <view class="small" @click="goService('/package-service/pages/repair/repair')">器械报修</view>
          <view class="small" @click="goService('/package-service/pages/complaint/complaint')">投诉建议</view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { formatDistance, storeDetail, type PublicStore } from "../../../api/catalog";
import { wifi as loadWifi } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";

const current = useCurrentStore();
const storeId = ref("");
const store = ref<PublicStore | null>(null);
const loggedIn = ref(false);
const wifi = ref<{ ssid: string | null; password: string | null }>({ ssid: null, password: null });

const isCurrent = computed(() => !!storeId.value && storeId.value === current.storeId);
const distance = computed(() => {
  const text = formatDistance(store.value?.distanceMeters ?? null);
  return text ? `距你 ${text}` : "";
});

function locate() {
  return new Promise<{ longitude: number; latitude: number } | null>((resolve) => {
    uni.getLocation({
      type: "gcj02",
      success(res) {
        resolve({ longitude: res.longitude, latitude: res.latitude });
      },
      fail() {
        resolve(null);
      },
    });
  });
}

onLoad(async (query) => {
  storeId.value = (query?.storeId as string) || current.storeId;
  if (!storeId.value) return;
  const point = await locate();
  store.value = await storeDetail(storeId.value, point?.longitude, point?.latitude);
  loggedIn.value = uni.getStorageSync("mpTokenType") === "ACCESS";
  if (!loggedIn.value) return;
  try {
    wifi.value = await loadWifi(storeId.value);
  } catch {
    wifi.value = { ssid: null, password: null };
  }
});

function setCurrent() {
  current.setStoreId(storeId.value);
  uni.showToast({ title: "已设为当前门店", icon: "none" });
}

function onOnlineClick() {
  if (!store.value?.onlineLocked) return;
  uni.navigateTo({ url: `/package-card/pages/open/open?storeId=${storeId.value}` });
}

function navigate() {
  if (!store.value) return;
  uni.openLocation({
    latitude: Number(store.value.latitude),
    longitude: Number(store.value.longitude),
    name: store.value.name,
    address: store.value.address,
  });
}

function copyWifi() {
  uni.setClipboardData({ data: wifi.value.password || wifi.value.ssid || "" });
}

function goGuide() {
  uni.navigateTo({ url: `/package-store/pages/guide/guide?storeId=${storeId.value}` });
}

function goVisits() {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.navigateTo({ url: "/pages/login/login" });
    return;
  }
  uni.navigateTo({ url: `/package-store/pages/visits/visits?storeId=${storeId.value}` });
}

function goService(url: string) {
  if (uni.getStorageSync("mpTokenType") !== "ACCESS") {
    uni.navigateTo({ url: "/pages/login/login" });
    return;
  }
  uni.navigateTo({ url: `${url}?storeId=${storeId.value}` });
}
</script>

<style scoped>
.page { padding: 0 24rpx 48rpx; }
.cover { width: 100vw; margin-left: -24rpx; height: 420rpx; display: block; }
.cover-empty { background: #1f2937; display: flex; align-items: center; justify-content: center; }
.cover-mark { color: #fff; font-size: 72rpx; font-weight: 700; }
.card {
  margin-top: -48rpx;
  position: relative;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.tags { display: flex; flex-wrap: wrap; gap: 12rpx; }
.tag {
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  background: #fff4ed;
  color: #ea580c;
  font-size: 22rpx;
  font-weight: 600;
}
.tag.current { background: #fff4ed; }
.tag.ghost { background: #f5f6f8; color: #525252; }
.name { display: block; margin-top: 16rpx; font-size: 40rpx; font-weight: 800; line-height: 1.35; }
.addr {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 20rpx;
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: #f5f6f8;
}
.addr-ico { width: 28rpx; height: 28rpx; }
.addr-text { flex: 1; min-width: 0; font-size: 24rpx; color: #525252; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.addr-more { color: #8a8f98; }
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 16rpx;
  border: 1px solid #eceff3;
}
.row.wifi { align-items: flex-start; }
.wifi-body { flex: 1; min-width: 0; }
.row-label { display: block; color: #8a8f98; font-size: 24rpx; }
.row-value { display: block; margin-top: 6rpx; font-size: 28rpx; }
.row-value.link { color: #ea580c; font-weight: 600; }
.row.muted { color: #8a8f98; font-size: 24rpx; }
.copy {
  padding: 8rpx 18rpx;
  border-radius: 10rpx;
  background: #fff4ed;
  color: #ea580c;
  font-size: 24rpx;
  font-weight: 600;
}
.set-btn { margin: 24rpx 0 0; }
.panel {
  margin-top: 16rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.bigs { display: flex; flex-wrap: wrap; gap: 16rpx; }
.big {
  width: calc(50% - 8rpx);
  height: 120rpx;
  border-radius: 16rpx;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  font-size: 28rpx;
  font-weight: 600;
  box-sizing: border-box;
}
.big-ico { width: 40rpx; height: 40rpx; }
.smalls { display: flex; gap: 12rpx; margin-top: 16rpx; }
.small {
  flex: 1;
  height: 72rpx;
  border-radius: 12rpx;
  background: #f8fafc;
  text-align: center;
  line-height: 72rpx;
  font-size: 22rpx;
  color: #404040;
}
.empty { color: #8a8f98; text-align: center; margin-top: 80rpx; }
</style>
