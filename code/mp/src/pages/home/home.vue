<template>
  <view class="page">
    <view class="topbar">
      <view class="locate" @click="goSwitch">
        <image class="locate-ico" src="/static/icon/pin.png" mode="aspectFit" />
        <text class="locate-text">{{ store?.name || "选择门店" }}{{ store?.city ? `（${store.city}）` : "" }}</text>
        <text class="locate-more">切换</text>
      </view>
      <view class="user" @click="goMineOrLogin">
        <view class="avatar">{{ userInitial }}</view>
      </view>
    </view>

    <swiper v-if="bannerList.length" class="banner" indicator-dots indicator-color="rgba(255,255,255,.4)" indicator-active-color="#ffffff" circular autoplay>
      <swiper-item v-for="item in bannerList" :key="item.id">
        <view class="banner-hit" @click="openBanner(item)">
          <image class="banner-image" :src="item.imageUrl" mode="aspectFill" />
        </view>
      </swiper-item>
    </swiper>
    <view v-else class="banner banner-fallback">
      <view>
        <text class="fallback-title">24 小时自助健身</text>
        <text class="fallback-sub">选店开通会员，刷脸进店训练</text>
      </view>
    </view>

    <view v-if="noticeList.length" class="notice">
      <text class="notice-tag">公告</text>
      <swiper
        class="notice-swiper"
        vertical
        circular
        autoplay
        :interval="4000"
        :duration="450"
        :disable-touch="noticeList.length <= 1"
      >
        <swiper-item v-for="item in noticeList" :key="item.id">
          <view class="notice-line" @click="openNotice(item)">
            <view class="notice-track" :class="{ run: isNoticeLong(item) }">
              <text class="notice-text">{{ plainNotice(item.content) }}</text>
              <text v-if="isNoticeLong(item)" class="notice-text gap">{{ plainNotice(item.content) }}</text>
            </view>
          </view>
        </swiper-item>
      </swiper>
    </view>

    <view class="groupon" @click="openGroupon">
      <view class="groupon-left">
        <view class="groupon-logos">
          <text class="dot meituan">美</text>
          <text class="dot douyin">抖</text>
        </view>
        <view>
          <text class="groupon-title">团购核销</text>
          <text class="groupon-sub">美团 / 抖音券一键开卡</text>
        </view>
      </view>
      <text class="groupon-more">去核销 ›</text>
    </view>

    <view class="actions">
      <view class="action" @click="openContacts">
        <view class="bubble"><image class="ico" src="/static/icon/phone.png" mode="aspectFit" /></view>
        <text>联系客服</text>
      </view>
      <view class="action" @click="goGuide">
        <view class="bubble"><image class="ico" src="/static/icon/route.png" mode="aspectFit" /></view>
        <text>到店指引</text>
      </view>
      <view class="action" @click="goRepair">
        <view class="bubble"><image class="ico" src="/static/icon/wrench.png" mode="aspectFit" /></view>
        <text>器械报修</text>
      </view>
      <view class="action" @click="goEquipment">
        <view class="bubble"><image class="ico" src="/static/icon/dumbbell.png" mode="aspectFit" /></view>
        <text>器械指导</text>
      </view>
      <view class="action" @click="goJoin">
        <view class="bubble"><image class="ico" src="/static/icon/info.png" mode="aspectFit" /></view>
        <text>我要开店</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">购卡</text>
      <text class="section-link" @click="buy">全部 ›</text>
    </view>
    <scroll-view v-if="cardList.length" class="cards" scroll-x enable-flex>
      <view v-for="item in cardList" :key="item.id" class="sku" :class="{ gray: !item.purchasable }" @click="openCard(item)">
        <text class="sku-name">{{ item.name }}</text>
        <view class="sku-foot">
          <text class="sku-price">{{ yuan(item.priceFen) }}</text>
          <text class="sku-meta">{{ item.displayText || `有效期${item.validDays}天` }}</text>
          <text v-if="item.remaining != null" class="sku-left">剩余 {{ item.remaining }} 张</text>
          <text v-if="item.lockText" class="sku-lock">{{ item.lockText }}</text>
        </view>
      </view>
    </scroll-view>
    <view v-else class="empty-card">选择门店后，这里显示可购买的会员卡</view>

    <view class="section">
      <text class="section-title">附近门店</text>
    </view>
    <view v-if="storeList.length === 0" class="empty-card">暂时没有门店</view>
    <view v-for="item in storeList" :key="item.id" class="store-card" @click="pickStore(item)">
      <image v-if="item.coverUrl" class="store-cover" :src="item.coverUrl" mode="aspectFill" />
      <view v-else class="store-cover store-cover-empty">
        <text class="cover-mark">{{ item.name.slice(0, 1) }}</text>
      </view>
      <view class="store-mask">
        <text class="store-name">{{ item.name }}{{ item.city ? `（${item.city}）` : "" }}</text>
        <text class="store-dist">{{ formatDistance(item.distanceMeters) ? `距离你 ${formatDistance(item.distanceMeters)}` : "查看门店介绍" }}</text>
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
    <contact-sheet :visible="sheet" :contacts="contacts" @close="sheet = false" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { storeToRefs } from "pinia";
import ContactSheet from "../../components/ContactSheet.vue";
import {
  banners,
  cards,
  formatDistance,
  listStores,
  me,
  notices,
  storeContacts,
  storeDetail,
  yuan,
  type BannerItem,
  type CardItem,
  type Contacts,
  type MeProfile,
  type NoticeItem,
  type PublicStore,
} from "../../api/catalog";
import { redeemGroupon } from "../../api/shop";
import { useCurrentStore } from "../../stores/currentStore";

const current = useCurrentStore();
const { storeId } = storeToRefs(current);
const bannerList = ref<BannerItem[]>([]);
const cardList = ref<CardItem[]>([]);
const noticeList = ref<NoticeItem[]>([]);
const storeList = ref<PublicStore[]>([]);
const store = ref<PublicStore | null>(null);
const profile = ref<MeProfile | null>(null);
const contacts = ref<Contacts | null>(null);
const sheet = ref(false);
const groupon = ref(false);
const platforms = ["MEITUAN", "DOUYIN"];
const platformLabels = ["美团", "抖音"];
const platformIndex = ref(0);
const code = ref("");
const location = ref<{ longitude: number; latitude: number } | null>(null);

const userInitial = computed(() => (profile.value?.nickname || (profile.value ? "会" : "用")).slice(0, 1));

function statusNeedStore() {
  if (storeId.value) return true;
  uni.showToast({ title: "请先选择门店", icon: "none" });
  return false;
}

function plainNotice(html: string) {
  return html.replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ").trim();
}

function isNoticeLong(item: NoticeItem) {
  return plainNotice(item.content).length > 18;
}

function openNotice(item: NoticeItem) {
  uni.navigateTo({ url: `/pages/notice/notice?id=${item.id}` });
}

function locate() {
  return new Promise<void>((resolve) => {
    uni.getLocation({
      type: "gcj02",
      success(res) {
        location.value = { longitude: res.longitude, latitude: res.latitude };
        resolve();
      },
      fail() {
        resolve();
      },
    });
  });
}

async function load() {
  try {
    bannerList.value = await banners();
    noticeList.value = await notices();
  } catch {
    bannerList.value = [];
    noticeList.value = [];
  }
  try {
    const rows = await listStores({
      longitude: location.value?.longitude,
      latitude: location.value?.latitude,
    });
    storeList.value = [...rows].sort((a, b) => {
      if (a.distanceMeters == null && b.distanceMeters == null) return 0;
      if (a.distanceMeters == null) return 1;
      if (b.distanceMeters == null) return -1;
      return a.distanceMeters - b.distanceMeters;
    });
  } catch {
    storeList.value = [];
  }
  if (uni.getStorageSync("mpTokenType") === "ACCESS") {
    try {
      profile.value = await me(storeId.value || undefined);
    } catch {
      profile.value = null;
    }
  } else {
    profile.value = null;
  }
  if (!storeId.value) {
    store.value = null;
    cardList.value = [];
    return;
  }
  try {
    store.value = await storeDetail(storeId.value, location.value?.longitude, location.value?.latitude);
    cardList.value = await cards(storeId.value, "HOME");
  } catch {
    store.value = null;
    cardList.value = [];
  }
}

function goSwitch() {
  uni.navigateTo({ url: "/package-store/pages/switch/switch" });
}

function goMineOrLogin() {
  if (profile.value) {
    uni.switchTab({ url: "/pages/mine/mine" });
    return;
  }
  uni.navigateTo({ url: "/pages/login/login" });
}

function goGuide() {
  if (!statusNeedStore()) return;
  uni.navigateTo({ url: `/package-store/pages/guide/guide?storeId=${storeId.value}` });
}

function goRepair() {
  if (!statusNeedStore()) return;
  uni.navigateTo({ url: "/package-service/pages/repair/repair" });
}

function goEquipment() {
  if (!statusNeedStore()) return;
  uni.navigateTo({ url: "/package-service/pages/equipment/equipment" });
}

function goJoin() {
  uni.switchTab({ url: "/pages/join/join" });
}

async function openContacts() {
  if (!statusNeedStore()) return;
  try {
    contacts.value = await storeContacts(storeId.value);
    sheet.value = true;
  } catch {
    sheet.value = false;
  }
}

function openCard(item: CardItem) {
  if (item.purchasable && storeId.value) {
    uni.navigateTo({ url: `/package-card/pages/confirm/confirm?storeId=${storeId.value}&cardId=${item.id}` });
    return;
  }
  uni.showModal({
    title: "暂不可购买",
    content: item.lockText || "暂时不能购买",
    showCancel: false,
  });
}

function buy() {
  if (!statusNeedStore()) return;
  uni.navigateTo({ url: `/package-card/pages/open/open?storeId=${storeId.value}` });
}

function pickStore(item: PublicStore) {
  uni.navigateTo({ url: `/package-store/pages/intro/intro?storeId=${item.id}` });
}

function openBanner(item: BannerItem) {
  const url = (item.linkUrl || "").trim();
  if (!url) return;
  const path = url.split("?")[0];
  const tabs = ["/pages/home/home", "/pages/coach/coach", "/pages/join/join", "/pages/mine/mine"];
  if (url.startsWith("/pages/") || url.startsWith("/package-")) {
    if (tabs.includes(path)) {
      uni.switchTab({ url: path });
      return;
    }
    uni.navigateTo({ url });
    return;
  }
  if (/^https?:\/\//i.test(url)) {
    uni.navigateTo({ url: `/pages/webview/webview?url=${encodeURIComponent(url)}` });
  }
}

function openGroupon() {
  if (!profile.value) {
    uni.navigateTo({ url: "/pages/login/login" });
    return;
  }
  if (!statusNeedStore()) return;
  groupon.value = true;
}

function onPlatform(event: { detail: { value: number } }) {
  platformIndex.value = Number(event.detail.value);
}

async function submitGroupon() {
  if (!storeId.value) {
    uni.showToast({ title: "请先选择门店", icon: "none" });
    return;
  }
  await redeemGroupon(storeId.value, platforms[platformIndex.value], code.value);
  uni.showToast({ title: "核销成功", icon: "none" });
  groupon.value = false;
  code.value = "";
}

onShow(async () => {
  await locate();
  await load();
});
</script>

<style scoped>
.page { padding: 20rpx 24rpx 48rpx; }
.topbar { display: flex; align-items: center; gap: 16rpx; margin-bottom: 20rpx; }
.locate {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.locate-ico { width: 28rpx; height: 28rpx; }
.locate-text { flex: 1; min-width: 0; font-size: 26rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.locate-more { color: #ea580c; font-size: 24rpx; font-weight: 600; }
.user { flex-shrink: 0; }
.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: #ea580c;
  color: #fff;
  text-align: center;
  line-height: 72rpx;
  font-size: 28rpx;
  font-weight: 700;
}
.banner {
  height: 300rpx;
  border-radius: 20rpx;
  overflow: hidden;
  background: #fff;
  border: 1px solid #eceff3;
}
.banner-hit, .banner-image { width: 100%; height: 300rpx; }
.banner-fallback {
  display: flex;
  align-items: center;
  padding: 0 36rpx;
  background: linear-gradient(135deg, #1f2937 0%, #111827 100%);
  box-sizing: border-box;
}
.fallback-title { display: block; color: #fff; font-size: 38rpx; font-weight: 700; }
.fallback-sub { display: block; margin-top: 10rpx; color: #d1d5db; font-size: 24rpx; }
.notice {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 16rpx;
  padding: 14rpx 18rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.notice-tag {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  background: #fff4ed;
  color: #ea580c;
  font-size: 20rpx;
  font-weight: 700;
}
.notice-swiper {
  flex: 1;
  min-width: 0;
  height: 40rpx;
}
.notice-line {
  height: 40rpx;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.notice-track {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
  max-width: 100%;
}
.notice-track.run {
  max-width: none;
  animation: notice-marquee 10s linear infinite;
}
.notice-text {
  flex-shrink: 0;
  color: #525252;
  font-size: 24rpx;
  line-height: 40rpx;
  white-space: nowrap;
}
.notice-text.gap { margin-left: 48rpx; }
@keyframes notice-marquee {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
.groupon {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
  border-left: 8rpx solid #ea580c;
}
.groupon-left { display: flex; align-items: center; gap: 16rpx; min-width: 0; }
.groupon-logos { display: flex; }
.dot {
  width: 40rpx;
  height: 40rpx;
  margin-right: -8rpx;
  border-radius: 50%;
  border: 2rpx solid #fff;
  text-align: center;
  line-height: 36rpx;
  font-size: 18rpx;
  font-weight: 700;
  color: #fff;
}
.dot.meituan { background: #22c55e; }
.dot.douyin { background: #111827; }
.groupon-title { display: block; font-size: 30rpx; font-weight: 700; }
.groupon-sub { display: block; margin-top: 4rpx; color: #8a8f98; font-size: 22rpx; }
.groupon-more { color: #ea580c; font-size: 24rpx; font-weight: 600; flex-shrink: 0; }
.actions {
  display: flex;
  margin-top: 16rpx;
  padding: 28rpx 8rpx 20rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
}
.action { width: 20%; display: flex; flex-direction: column; align-items: center; gap: 10rpx; font-size: 22rpx; color: #404040; }
.bubble {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ico { width: 38rpx; height: 38rpx; }
.section { display: flex; justify-content: space-between; align-items: baseline; margin: 36rpx 4rpx 16rpx; }
.section-title { font-size: 32rpx; font-weight: 700; }
.section-link { color: #ea580c; font-size: 24rpx; font-weight: 600; }
.cards { white-space: nowrap; }
.sku {
  display: inline-flex;
  flex-direction: column;
  justify-content: space-between;
  width: 300rpx;
  height: 280rpx;
  margin-right: 16rpx;
  padding: 28rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  border: 1px solid #eceff3;
  vertical-align: top;
  white-space: normal;
  box-sizing: border-box;
}
.sku.gray { background: #fafafa; color: #8a8f98; }
.sku-name { font-size: 30rpx; font-weight: 700; line-height: 1.4; }
.sku-foot { margin-top: auto; }
.sku-price { display: block; font-size: 42rpx; font-weight: 800; color: #ea580c; line-height: 1.2; }
.sku.gray .sku-price { color: #8a8f98; }
.sku-meta, .sku-lock, .sku-left { display: block; margin-top: 8rpx; font-size: 22rpx; color: #8a8f98; }
.sku-left { color: #ea580c; }
.empty-card {
  padding: 40rpx 24rpx;
  border-radius: 20rpx;
  background: #fff;
  color: #8a8f98;
  text-align: center;
  border: 1px solid #eceff3;
}
.store-card {
  position: relative;
  height: 300rpx;
  margin-bottom: 16rpx;
  border-radius: 20rpx;
  overflow: hidden;
  border: 1px solid #eceff3;
}
.store-cover { width: 100%; height: 300rpx; }
.store-cover-empty { display: flex; align-items: center; justify-content: center; background: #1f2937; }
.cover-mark { color: #fff; font-size: 64rpx; font-weight: 700; }
.store-mask {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 48rpx 24rpx 22rpx;
  background: linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.68) 100%);
  color: #fff;
}
.store-name { display: block; font-size: 30rpx; font-weight: 700; }
.store-dist { display: block; margin-top: 6rpx; font-size: 24rpx; color: #e5e7eb; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(17, 24, 39, 0.45); display: flex; align-items: flex-end; z-index: 20; }
.sheet { width: 100%; padding: 28rpx 28rpx 48rpx; border-radius: 24rpx 24rpx 0 0; background: #fff; box-sizing: border-box; }
.sheet-title { display: block; font-size: 32rpx; font-weight: 700; }
.sheet-hint { display: block; margin: 8rpx 0 16rpx; color: #8a8f98; font-size: 24rpx; }
.field-line { padding: 0 24rpx; height: 88rpx; line-height: 88rpx; border-radius: 16rpx; background: #f5f6f8; border: 1px solid #eceff3; }
input { margin-top: 16rpx; background: #f5f6f8; }
.sheet-btn { margin-top: 20rpx; }
.cancel { margin-top: 12rpx; padding: 24rpx; text-align: center; color: #8a8f98; }
</style>
