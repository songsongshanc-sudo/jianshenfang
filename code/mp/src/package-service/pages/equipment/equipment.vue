<template>
  <view class="page">
    <input v-model="code" placeholder="扫码或输入器械编号" />
    <button @click="lookup">查看教程</button>
    <view v-if="detail" class="card">
      <text class="name">{{ detail.name }}</text>
      <rich-text v-if="detail.intro" class="meta" :nodes="detail.intro" />
      <image v-if="showLegacyImage" class="photo" :src="detail.image_url" mode="widthFix" />
      <video v-if="detail.video_url" :src="detail.video_url" controls />
    </view>
    <view v-for="item in rows" :key="item.id" class="card" @click="open(item.code)">
      <text>{{ item.name }}</text>
      <text class="meta">{{ item.code }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { equipmentByCode, equipmentList } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
const current = useCurrentStore();
const code = ref("");
const rows = ref<Array<{ id: string; code: string; name: string; intro: string; image_url: string; video_url: string }>>([]);
const detail = ref<{ name: string; intro: string; image_url: string; video_url: string } | null>(null);
const queryStoreId = ref("");
onLoad(async (query) => {
  queryStoreId.value = String(query?.storeId || "");
  if (query?.code) code.value = String(query.code);
  rows.value = await equipmentList(queryStoreId.value || current.storeId);
  if (code.value) await lookup();
});
const showLegacyImage = computed(() => {
  const image = detail.value?.image_url;
  if (!image) return false;
  return !(detail.value?.intro || "").includes(image);
});
async function lookup() {
  detail.value = await equipmentByCode(queryStoreId.value || current.storeId, code.value);
}
function open(value: string) { code.value = value; lookup(); }
</script>
<style scoped>
.page { padding: 24rpx 24rpx 48rpx; }
.card { margin-top: 16rpx; padding: 24rpx; border-radius: 20rpx; background: #fff; border: 1px solid #eceff3; }
.name { font-weight: 700; }
.meta { display: block; margin-top: 8rpx; color: #78716c; }
input { height: 88rpx; padding: 0 24rpx; line-height: 88rpx; background: #fff; }
button { margin-top: 16rpx; }
.photo, video { width: 100%; margin-top: 16rpx; border-radius: 16rpx; }
</style>
