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
  formatDistance,
  notices,
  storeContacts,
  storeDetail,
  type BannerItem,
  type Contacts,
  type NoticeItem,
  type PublicStore,
} from "../../api/catalog";
import { useCurrentStore } from "../../stores/currentStore";

const current = useCurrentStore();
const { storeId } = storeToRefs(current);
const bannerList = ref<BannerItem[]>([]);
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
    return;
  }
  try {
    store.value = await storeDetail(storeId.value, location.value?.longitude, location.value?.latitude);
  } catch {
    store.value = null;
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

function buy() {
  uni.showToast({ title: "请先登录", icon: "none" });
  uni.navigateTo({ url: "/pages/login/login" });
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
.buy { margin-top: 24rpx; background: #e85d04; }
</style>
