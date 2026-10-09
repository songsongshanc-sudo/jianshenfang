<template>
  <view class="page">
    <view v-if="!storeId" class="empty">请先选择门店</view>
    <template v-else-if="store">
      <image v-if="store.coverUrl" class="cover" :src="store.coverUrl" mode="aspectFill" />
      <text class="name">{{ store.name }}</text>
      <text class="meta">{{ store.province }} {{ store.city }} {{ store.address }}</text>
      <text class="meta">{{ store.status === "OPEN" ? "营业中" : "暂停营业" }} · {{ store.businessHours }}</text>
      <text class="meta">在线人数：{{ store.onlineText }}</text>
      <view class="actions">
        <button @click="goGuide">到店指引</button>
        <button @click="goWifi">WiFi</button>
        <button @click="openContacts">联系客服</button>
        <button @click="navigate">导航</button>
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
.page { padding: 24rpx; }
.cover { width: 100%; height: 320rpx; border-radius: 16rpx; }
.name { display: block; margin-top: 20rpx; font-size: 36rpx; font-weight: 600; }
.meta { display: block; margin-top: 10rpx; color: #555; font-size: 28rpx; }
.actions { display: flex; gap: 16rpx; margin-top: 24rpx; }
.actions button { margin: 0; }
.empty { color: #888; text-align: center; margin-top: 80rpx; }
</style>
