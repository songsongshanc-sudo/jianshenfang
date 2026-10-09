<template>
  <view class="page">
    <view v-if="!storeId" class="empty">请先选择门店</view>
    <template v-else-if="store">
      <image v-if="store.coverUrl" class="cover" :src="store.coverUrl" mode="aspectFill" />
      <text class="name">{{ store.name }}</text>
      <text class="meta">{{ store.province }} {{ store.city }} {{ store.address }}</text>
      <text class="meta">{{ store.status === "OPEN" ? "营业中" : "暂停营业" }} · {{ store.businessHours }}</text>
      <text class="meta">在线人数：{{ store.onlineText }}</text>
      <view class="quick">
        <view class="quick-item" @click="goGuide"><view class="bubble"><image class="ico" src="/static/icon/route.png" mode="aspectFit" /></view><text>到店指引</text></view>
        <view class="quick-item" @click="goWifi"><view class="bubble"><image class="ico" src="/static/icon/wifi.png" mode="aspectFit" /></view><text>WiFi</text></view>
        <view class="quick-item" @click="openContacts"><view class="bubble"><image class="ico" src="/static/icon/phone.png" mode="aspectFit" /></view><text>客服</text></view>
        <view class="quick-item" @click="navigate"><view class="bubble"><image class="ico" src="/static/icon/pin.png" mode="aspectFit" /></view><text>导航</text></view>
      </view>
    </template>
    <contact-sheet :visible="sheet" :contacts="contacts" @close="sheet = false" />
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import ContactSheet from "../../../components/ContactSheet.vue";
import { storeContacts, storeDetail, type Contacts, type PublicStore } from "../../../api/catalog";
import { useCurrentStore } from "../../../stores/currentStore";

const current = useCurrentStore();
const storeId = ref("");
const store = ref<PublicStore | null>(null);
const contacts = ref<Contacts | null>(null);
const sheet = ref(false);

async function openContacts() {
  if (!storeId.value) return;
  contacts.value = await storeContacts(storeId.value);
  sheet.value = true;
}

function goGuide() {
  uni.navigateTo({ url: `/package-store/pages/guide/guide?storeId=${storeId.value}` });
}

function goWifi() {
  uni.navigateTo({ url: `/package-store/pages/wifi/wifi?storeId=${storeId.value}` });
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

onLoad(async (query) => {
  storeId.value = (query?.storeId as string) || current.storeId;
  if (!storeId.value) return;
  store.value = await storeDetail(storeId.value);
});
</script>

<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.cover { width: 100%; height: 360rpx; border-radius: 28rpx; }
.name { display: block; margin-top: 24rpx; font-size: 40rpx; font-weight: 700; }
.meta { display: block; margin-top: 10rpx; color: #57534e; font-size: 26rpx; line-height: 1.5; }
.quick { display: flex; margin-top: 32rpx; }
.quick-item { width: 25%; display: flex; flex-direction: column; align-items: center; gap: 12rpx; font-size: 24rpx; }
.bubble { width: 96rpx; height: 96rpx; border-radius: 32rpx; background: #fff; display: flex; align-items: center; justify-content: center; }
.ico { width: 44rpx; height: 44rpx; }
.empty { color: #888; text-align: center; margin-top: 80rpx; }
</style>
