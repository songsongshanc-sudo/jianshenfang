<template>
  <view class="page">
    <view class="filters">
      <input v-model="province" class="input" placeholder="省" />
      <input v-model="city" class="input" placeholder="市" />
      <button size="mini" @click="load">查询</button>
    </view>
    <map
      class="map"
      :latitude="center.latitude"
      :longitude="center.longitude"
      :markers="markers"
      :scale="11"
      @markertap="onMarker"
    />
    <view v-if="stores.length === 0" class="empty">没有找到门店</view>
    <view v-for="item in stores" :key="item.id" class="item" @click="choose(item)">
      <view>
        <text class="name">{{ item.name }}</text>
        <text class="meta">{{ item.province }} {{ item.city }} {{ item.address }}</text>
        <text v-if="formatDistance(item.distanceMeters)" class="meta">{{ formatDistance(item.distanceMeters) }}</text>
      </view>
      <button size="mini" @click.stop="navigate(item)">导航</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { formatDistance, listStores, type PublicStore } from "../../../api/catalog";
import { useCurrentStore } from "../../../stores/currentStore";

const current = useCurrentStore();
const province = ref("");
const city = ref("");
const stores = ref<PublicStore[]>([]);
const location = ref<{ longitude: number; latitude: number } | null>(null);

const center = computed(() => {
  if (location.value) return location.value;
  const first = stores.value[0];
  if (first) return { longitude: Number(first.longitude), latitude: Number(first.latitude) };
  return { longitude: 104.0, latitude: 35.0 };
});

const markers = computed(() =>
  stores.value.map((item, index) => ({
    id: index + 1,
    latitude: Number(item.latitude),
    longitude: Number(item.longitude),
    title: item.name,
    width: 28,
    height: 28,
  }))
);

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
    stores.value = await listStores({
      province: province.value.trim(),
      city: city.value.trim(),
      longitude: location.value?.longitude,
      latitude: location.value?.latitude,
    });
  } catch {
    stores.value = [];
  }
}

function choose(item: PublicStore) {
  current.setStoreId(item.id);
  uni.navigateBack();
}

function navigate(item: PublicStore) {
  uni.openLocation({
    latitude: Number(item.latitude),
    longitude: Number(item.longitude),
    name: item.name,
    address: item.address,
  });
}

function onMarker(event: { detail: { markerId: number } }) {
  const item = stores.value[event.detail.markerId - 1];
  if (item) choose(item);
}

onShow(async () => {
  await locate();
  await load();
});
</script>

<style scoped>
.page { padding: 24rpx; }
.filters { display: flex; gap: 12rpx; align-items: center; }
.input { flex: 1; background: #fff; padding: 12rpx 16rpx; border-radius: 8rpx; }
.map { width: 100%; height: 420rpx; margin: 16rpx 0; }
.item { display: flex; justify-content: space-between; align-items: center; background: #fff; padding: 20rpx; border-radius: 12rpx; margin-bottom: 12rpx; }
.name { display: block; font-size: 30rpx; font-weight: 600; }
.meta { display: block; margin-top: 6rpx; color: #666; font-size: 24rpx; }
.empty { color: #888; text-align: center; margin-top: 40rpx; }
</style>
