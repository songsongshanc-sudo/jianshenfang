<template>
  <view class="page">
    <swiper v-if="bannerList.length" class="banner" indicator-dots indicator-color="rgba(255,255,255,.45)" indicator-active-color="#ffffff" circular autoplay>
      <swiper-item v-for="item in bannerList" :key="item.id">
        <image class="banner-image" :src="item.imageUrl" mode="aspectFill" />
      </swiper-item>
    </swiper>
    <view v-else class="banner banner-fallback">
      <text class="mark">24</text>
      <view>
        <text class="fallback-title">自助健身</text>
        <text class="fallback-sub">选一家店，开通会员后到店训练</text>
      </view>
    </view>

    <view v-if="noticeList.length" class="notice">
      <image class="notice-icon" src="/static/icon/bell.png" mode="aspectFit" />
      <text class="notice-text">{{ noticeList.map((item) => item.content).join("    ") }}</text>
    </view>

    <view class="store" @click="goSwitch">
      <image v-if="store?.coverUrl" class="cover" :src="store.coverUrl" mode="aspectFill" />
      <view v-else class="cover cover-empty"><text class="cover-mark">{{ (store?.name || "店").slice(0, 1) }}</text></view>
      <view class="store-body">
        <view class="store-row">
          <text class="name">{{ store?.name || "选择门店" }}</text>
          <text v-if="store" class="pill" :class="{ off: store.status !== 'OPEN' }">{{ statusText(store.status) }}</text>
        </view>
        <text class="meta">{{ store?.address || "查看附近门店" }}</text>
        <text v-if="distance || store?.onlineText" class="meta">{{ [distance ? `距你 ${distance}` : "", store?.onlineText || ""].filter(Boolean).join("  ·  ") }}</text>
      </view>
    </view>

    <view class="quick">
      <view class="quick-item" @click="goSwitch">
        <view class="bubble"><image class="ico" src="/static/icon/pin.png" mode="aspectFit" /></view>
        <text>切换门店</text>
      </view>
      <view class="quick-item" @click="goIntro">
        <view class="bubble"><image class="ico" src="/static/icon/info.png" mode="aspectFit" /></view>
        <text>门店介绍</text>
      </view>
      <view class="quick-item" @click="goGuide">
        <view class="bubble"><image class="ico" src="/static/icon/route.png" mode="aspectFit" /></view>
        <text>到店指引</text>
      </view>
      <view class="quick-item" @click="openContacts">
        <view class="bubble"><image class="ico" src="/static/icon/phone.png" mode="aspectFit" /></view>
        <text>联系客服</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">会员卡</text>
      <text class="section-link" @click="buy">全部</text>
    </view>
    <scroll-view v-if="cardList.length" class="cards" scroll-x enable-flex>
      <view v-for="item in cardList" :key="item.id" class="sku" :class="{ gray: !item.purchasable }" @click="openCard(item)">
        <text class="sku-name">{{ item.name }}</text>
        <text class="sku-price">{{ yuan(item.priceFen) }}</text>
        <text v-if="item.displayText" class="sku-meta">{{ item.displayText }}</text>
        <text v-if="item.remaining != null" class="sku-left">剩余 {{ item.remaining }} 张</text>
        <text v-if="item.lockText" class="sku-lock">{{ item.lockText }}</text>
      </view>
    </scroll-view>
    <view v-else class="empty-card">选择门店后，这里显示可购买的会员卡</view>

    <view class="dock">
      <button class="buy" @click="buy">开通会员</button>
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
  notices,
  storeContacts,
  storeDetail,
  yuan,
  type BannerItem,
  type CardItem,
  type Contacts,
  type NoticeItem,
  type PublicStore,
} from "../../api/catalog";
import { useCurrentStore } from "../../stores/currentStore";

const current = useCurrentStore();
const { storeId } = storeToRefs(current);
const bannerList = ref<BannerItem[]>([]);
const cardList = ref<CardItem[]>([]);
const noticeList = ref<NoticeItem[]>([]);
const store = ref<PublicStore | null>(null);
const contacts = ref<Contacts | null>(null);
const sheet = ref(false);
const location = ref<{ longitude: number; latitude: number } | null>(null);

const distance = computed(() => formatDistance(store.value?.distanceMeters ?? null));

function statusText(status: string) {
  return status === "OPEN" ? "营业中" : "暂停营业";
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

function requireStore() {
  if (storeId.value) return true;
  uni.showToast({ title: "请先选择门店", icon: "none" });
  return false;
}

function goSwitch() {
  uni.navigateTo({ url: "/package-store/pages/switch/switch" });
}

function goIntro() {
  if (!requireStore()) return;
  uni.navigateTo({ url: `/package-store/pages/intro/intro?storeId=${storeId.value}` });
}

function goGuide() {
  if (!requireStore()) return;
  uni.navigateTo({ url: `/package-store/pages/guide/guide?storeId=${storeId.value}` });
}

async function openContacts() {
  if (!requireStore()) return;
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
  if (!requireStore()) return;
  uni.navigateTo({ url: `/package-card/pages/open/open?storeId=${storeId.value}` });
}

onShow(async () => {
  await locate();
  await load();
});
</script>

<style scoped>
.page { padding: 24rpx 24rpx 180rpx; }
.banner { height: 320rpx; border-radius: 28rpx; overflow: hidden; }
.banner-image { width: 100%; height: 320rpx; }
.banner-fallback {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 0 36rpx;
  background: #1c1917;
  box-sizing: border-box;
}
.mark {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: #c2410c;
  color: #fff;
  text-align: center;
  line-height: 88rpx;
  font-size: 36rpx;
  font-weight: 800;
}
.fallback-title { display: block; color: #fff; font-size: 40rpx; font-weight: 700; }
.fallback-sub { display: block; margin-top: 8rpx; color: #d6d3d1; font-size: 24rpx; }
.notice {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  background: #fff7ed;
}
.notice-icon { width: 32rpx; height: 32rpx; flex-shrink: 0; }
.notice-text { color: #9a3412; font-size: 24rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.store {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
  padding: 20rpx;
  background: #fff;
  border-radius: 28rpx;
}
.cover { width: 160rpx; height: 160rpx; border-radius: 20rpx; flex-shrink: 0; }
.cover-empty { display: flex; align-items: center; justify-content: center; background: #1c1917; }
.cover-mark { color: #fff; font-size: 48rpx; font-weight: 700; }
.store-body { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }
.store-row { display: flex; align-items: center; gap: 12rpx; }
.name { font-size: 34rpx; font-weight: 700; }
.pill { padding: 4rpx 12rpx; border-radius: 999rpx; background: #ecfdf3; color: #15803d; font-size: 20rpx; }
.pill.off { background: #f5f5f4; color: #78716c; }
.meta { display: block; margin-top: 8rpx; color: #78716c; font-size: 24rpx; }
.quick { display: flex; margin-top: 28rpx; }
.quick-item { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 12rpx; font-size: 24rpx; }
.bubble { width: 96rpx; height: 96rpx; border-radius: 32rpx; background: #fff; display: flex; align-items: center; justify-content: center; }
.ico { width: 44rpx; height: 44rpx; }
.section { display: flex; justify-content: space-between; align-items: baseline; margin: 36rpx 4rpx 16rpx; }
.section-title { font-size: 32rpx; font-weight: 700; }
.section-link { color: #c2410c; font-size: 26rpx; }
.cards { white-space: nowrap; }
.sku {
  display: inline-flex;
  flex-direction: column;
  width: 280rpx;
  min-height: 180rpx;
  margin-right: 16rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #fff;
  vertical-align: top;
  white-space: normal;
  box-sizing: border-box;
}
.sku.gray { background: #f5f5f4; color: #78716c; }
.sku-name { font-size: 28rpx; font-weight: 600; }
.sku-price { margin-top: 16rpx; font-size: 40rpx; font-weight: 700; color: #c2410c; }
.sku.gray .sku-price { color: #78716c; }
.sku-meta, .sku-lock, .sku-left { margin-top: 8rpx; font-size: 22rpx; color: #78716c; }
.sku-left { color: #c2410c; }
.empty-card { padding: 40rpx 24rpx; border-radius: 24rpx; background: #fff; color: #78716c; text-align: center; }
.dock {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx 24rpx;
  background: rgba(246, 243, 238, 0.96);
}
.buy { margin: 0; background: #c2410c; color: #fff; }
</style>
