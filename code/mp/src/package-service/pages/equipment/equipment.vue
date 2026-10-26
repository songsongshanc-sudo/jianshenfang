<template>
  <view class="page">
    <view class="search">
      <input v-model="draft" confirm-type="search" placeholder="搜索名称或编号，可跨店" @confirm="search" />
      <button class="go" @click="search">搜索</button>
    </view>
    <text class="hint">{{ searching ? "全部门店" : "本店" }} · {{ total }} 个视频</text>

    <view v-if="active" class="detail">
      <text class="back" @click="active = null">返回列表</text>
      <text class="name">{{ active.name }}</text>
      <view class="meta-row">
        <text class="code">{{ active.code }}</text>
        <text class="store">{{ active.store_name }}</text>
      </view>
      <rich-text v-if="introHtml" class="intro" :nodes="introHtml" />
      <button class="watch" @click="play(active)">观看视频</button>
    </view>

    <view v-if="!active" v-for="item in rows" :key="item.id" class="card" @click="play(item)">
      <view class="cover-wrap">
        <image v-if="item.cover_url" class="cover" :src="item.cover_url" mode="aspectFill" />
        <view v-else class="cover placeholder">
          <text class="mark">▶</text>
        </view>
        <view v-if="item.cover_url" class="badge">▶</view>
      </view>
      <view class="info">
        <text class="name">{{ item.name }}</text>
        <view class="meta-row">
          <text class="code">{{ item.code }}</text>
          <text v-if="searching" class="store">{{ item.store_name }}</text>
        </view>
        <text v-if="plainIntro(item.intro)" class="more" @click.stop="active = item">查看图文</text>
      </view>
    </view>

    <view v-if="!active && !loading && rows.length === 0" class="empty">没有找到器械视频</view>
    <view v-else-if="!active" class="foot">{{ loading ? "加载中" : hasMore ? "上拉加载更多" : "没有更多了" }}</view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad, onReachBottom } from "@dcloudio/uni-app";
import { equipmentVideos, type EquipmentVideo } from "../../../api/shop";
import { useCurrentStore } from "../../../stores/currentStore";
import { fitRichHtml } from "../../../utils/richHtml";

declare const wx: {
  openChannelsActivity: (options: { finderUserName: string; feedId: string; fail?: () => void }) => void;
};

const current = useCurrentStore();
const storeId = ref("");
const draft = ref("");
const keyword = ref("");
const rows = ref<EquipmentVideo[]>([]);
const page = ref(1);
const total = ref(0);
const hasMore = ref(false);
const loading = ref(false);
const active = ref<EquipmentVideo | null>(null);
const searching = computed(() => keyword.value.trim().length > 0);
const introHtml = computed(() => fitRichHtml(active.value?.intro));

onLoad((query) => {
  storeId.value = String(query?.storeId || current.storeId || "");
  if (query?.code) {
    draft.value = String(query.code);
    keyword.value = draft.value;
  }
  void load(true);
});

onReachBottom(() => {
  void load(false);
});

async function search() {
  keyword.value = draft.value.trim();
  active.value = null;
  await load(true);
}

async function load(reset: boolean) {
  if (loading.value) return;
  if (!reset && !hasMore.value) return;
  const next = reset ? 1 : page.value + 1;
  loading.value = true;
  try {
    const data = await equipmentVideos(storeId.value, keyword.value, next);
    rows.value = reset ? data.list : rows.value.concat(data.list);
    page.value = data.page;
    total.value = data.total;
    hasMore.value = data.hasMore;
  } catch {
    if (reset) {
      rows.value = [];
      total.value = 0;
      hasMore.value = false;
    }
  } finally {
    loading.value = false;
  }
}

function plainIntro(html: string) {
  return String(html || "").replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ").trim();
}

function play(item: EquipmentVideo) {
  if (!item.finder_user_name || !item.feed_id) {
    uni.showToast({ title: "视频号还没配置", icon: "none" });
    return;
  }
  // #ifdef MP-WEIXIN
  wx.openChannelsActivity({
    finderUserName: item.finder_user_name,
    feedId: item.feed_id,
    fail() {
      uni.showToast({ title: "打不开这条视频", icon: "none" });
    }
  });
  // #endif
  // #ifndef MP-WEIXIN
  uni.showToast({ title: "请在微信小程序中观看", icon: "none" });
  // #endif
}
</script>
<style scoped>
.page { min-height: 100vh; padding: 24rpx 24rpx 64rpx; background: #f6f3ef; box-sizing: border-box; }
.search { display: flex; gap: 16rpx; align-items: center; }
.search uni-input { flex: 1; min-width: 0; height: 80rpx; }
.search :deep(.uni-input-wrapper) {
  width: 100%; height: 80rpx; border-radius: 40rpx; background: #fff;
}
.search :deep(.uni-input-input) {
  width: 100%; height: 80rpx; padding: 0 28rpx; font-size: 28rpx; box-sizing: border-box;
}
.search uni-button.go {
  width: auto; flex: none; margin: 0; height: 80rpx; line-height: 80rpx; padding: 0 32rpx;
  border-radius: 40rpx; background: #ea580c; color: #fff; font-size: 28rpx;
}
button::after { border: none; }
.hint { display: block; margin: 20rpx 8rpx 8rpx; color: #a8a29e; font-size: 24rpx; }
.card, .detail {
  margin-top: 20rpx; background: #fff; border-radius: 28rpx; overflow: hidden;
  box-shadow: 0 8rpx 28rpx rgba(28, 25, 23, 0.06);
}
.cover-wrap { position: relative; }
.cover { width: 100%; height: 360rpx; display: block; background: #1c1917; }
.placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(160deg, #44403c, #1c1917); }
.mark, .badge { color: #fff; font-size: 44rpx; }
.badge {
  position: absolute; right: 24rpx; bottom: 24rpx; width: 72rpx; height: 72rpx;
  border-radius: 36rpx; background: rgba(0, 0, 0, 0.45); text-align: center; line-height: 72rpx;
}
.info { padding: 24rpx 28rpx 28rpx; }
.name { display: block; font-size: 32rpx; font-weight: 700; color: #1c1917; }
.meta-row { display: flex; gap: 16rpx; align-items: center; margin-top: 12rpx; }
.code { padding: 4rpx 14rpx; border-radius: 8rpx; background: #fff7ed; color: #c2410c; font-size: 24rpx; }
.store { color: #78716c; font-size: 24rpx; }
.more { display: block; margin-top: 16rpx; color: #ea580c; font-size: 24rpx; }
.detail { padding: 28rpx; }
.back { display: block; margin-bottom: 16rpx; color: #ea580c; font-size: 26rpx; }
.intro { display: block; margin-top: 20rpx; color: #44403c; font-size: 28rpx; }
.watch { margin-top: 28rpx; background: #ea580c; color: #fff; border-radius: 16rpx; }
.empty, .foot { padding: 48rpx 0; text-align: center; color: #a8a29e; font-size: 26rpx; }
</style>
