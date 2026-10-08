<template>
  <view class="page">
    <swiper v-if="bannerList.length" class="banner" indicator-dots circular>
      <swiper-item v-for="item in bannerList" :key="item.id">
        <image class="banner-image" :src="item.imageUrl" mode="aspectFill" />
      </swiper-item>
    </swiper>
    <view v-if="noticeList.length" class="notice">{{ noticeList.map((item) => item.content).join("  ·  ") }}</view>

    <view class="card" @click="goSwitch">
      <image v-if="store?.coverUrl" class="cover" :src="store.coverUrl" mode="aspectFill" />
      <view class="card-body">
        <text class="name">{{ store?.name || "请选择门店" }}</text>
        <text class="meta">{{ store ? statusText(store.status) : "点这里切换门店" }}</text>
        <text v-if="distance" class="meta">距离 {{ distance }}</text>
      </view>
    </view>

    <view class="actions">
      <button @click="goSwitch">切换门店</button>
      <button @click="goIntro">门店介绍</button>
      <button @click="goGuide">到店指引</button>
      <button @click="openContacts">联系客服</button>
    </view>
    <scroll-view v-if="cardList.length" class="cards" scroll-x>
      <view
        v-for="item in cardList"
        :key="item.id"
        class="sku"
        :class="{ gray: !item.purchasable }"
        @click="openCard(item)"
      >
        <text class="sku-name">{{ item.name }}</text>
        <text class="sku-price">{{ yuan(item.priceFen) }}</text>
        <text v-if="item.displayText" class="sku-meta">{{ item.displayText }}</text>
        <text v-if="item.remaining != null" class="sku-left">剩余 {{ item.remaining }} 张</text>
        <text v-if="item.lockText" class="sku-lock">{{ item.lockText }}</text>
      </view>
    </scroll-view>
    <button class="buy" type="primary" @click="buy">开通会员</button>
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
.page { padding: 24rpx; }
.banner { height: 280rpx; border-radius: 16rpx; overflow: hidden; }
.banner-image { width: 100%; height: 280rpx; }
.notice { margin-top: 16rpx; color: #9a3412; background: #fff7ed; padding: 16rpx; border-radius: 12rpx; font-size: 26rpx; }
.card { margin-top: 20rpx; background: #fff; border-radius: 16rpx; overflow: hidden; }
.cover { width: 100%; height: 280rpx; }
.card-body { padding: 20rpx; }
.name { display: block; font-size: 34rpx; font-weight: 600; }
.meta { display: block; margin-top: 8rpx; color: #666; font-size: 26rpx; }
.actions { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 20rpx; }
.actions button { margin: 0; }
.cards { margin-top: 20rpx; white-space: nowrap; }
.sku {
  display: inline-flex;
  flex-direction: column;
  width: 240rpx;
  margin-right: 16rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #fff7ed;
  vertical-align: top;
  white-space: normal;
}
.sku.gray { background: #f5f5f4; color: #78716c; }
.sku-name { font-size: 28rpx; font-weight: 600; }
.sku-price { margin-top: 8rpx; font-size: 32rpx; color: #c2410c; }
.sku.gray .sku-price { color: #78716c; }
.sku-meta, .sku-lock, .sku-left { margin-top: 8rpx; font-size: 22rpx; }
.sku-left { color: #c2410c; }
.buy { margin-top: 24rpx; background: #e85d04; }
</style>
