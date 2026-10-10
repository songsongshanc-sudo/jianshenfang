<template>
  <view class="page" v-if="coach">
    <view class="hero" :style="{ paddingTop: statusPad + 44 + 'px' }">
      <image class="hero-img" :src="heroImg" mode="aspectFill" />
      <view class="hero-mask" />
      <view class="nav" :style="{ paddingTop: statusPad + 'px' }">
        <view class="back" @click="goBack">‹</view>
        <text class="nav-title">教练详情</text>
        <view class="nav-space" />
      </view>
      <view class="profile">
        <image class="avatar" :src="heroImg" mode="aspectFill" />
        <view class="who">
          <text class="name">{{ coach.name }}</text>
          <text class="rating">★★★★★ {{ formatRating(coach.rating) }}</text>
          <text class="specialty">{{ coach.specialty || plainIntro(coach.intro) || "私教指导" }}</text>
        </view>
      </view>
      <view class="stats">
        <view class="stat">
          <text class="num">{{ coach.pack_count || coach.packs?.length || 0 }}</text>
          <text class="label">课程 (节)</text>
        </view>
        <view class="stat">
          <text class="num">{{ coach.lesson_taught || 0 }}</text>
          <text class="label">累计 (节)</text>
        </view>
        <view class="stat" @click="previewCerts">
          <text class="num">{{ certCount }} {{ certCount ? "›" : "" }}</text>
          <text class="label">证书 (张)</text>
        </view>
      </view>
    </view>

    <view class="sheet">
      <text class="sheet-title">课程</text>
      <view v-if="!coach.packs?.length" class="empty">这位教练还没有上架课程</view>
      <view v-for="pack in coach.packs" :key="pack.id" class="course">
        <image v-if="pack.cover_url" class="cover" :src="pack.cover_url" mode="aspectFill" />
        <view v-else class="cover soft">课</view>
        <view class="info">
          <text class="cname">{{ pack.name }}</text>
          <text class="cmeta">{{ pack.lesson_count }} 节 · 每节约 {{ pack.minutes_per_lesson || 60 }} 分钟</text>
          <text class="cprice">¥ {{ (pack.price_fen / 100).toFixed(0) }}</text>
        </view>
        <view class="btn" @click="openCourse(pack.id)">查看详情</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";
import { coachDetail, type CoachDetail } from "../../../api/shop";

const coach = ref<CoachDetail | null>(null);
const statusPad = uni.getSystemInfoSync().statusBarHeight || 20;

const heroImg = computed(() => coach.value?.photo_url || coach.value?.avatar_url || "");
const certs = computed(() => parseCerts(coach.value?.certificate_urls));
const certCount = computed(() => certs.value.length);

onLoad(async (query) => {
  const id = (query?.id as string) || "";
  if (!id) return;
  try {
    coach.value = await coachDetail(id);
  } catch {
    coach.value = null;
  }
});

function formatRating(value?: number | null) {
  const n = Number(value ?? 5);
  return Number.isFinite(n) ? n.toFixed(1) : "5.0";
}

function plainIntro(html?: string | null) {
  return (html || "").replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ").trim();
}

function parseCerts(raw?: string | null) {
  if (!raw) return [] as string[];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : [];
  } catch {
    return raw.split(/[\n,]/).map((item) => item.trim()).filter(Boolean);
  }
}

function previewCerts() {
  if (!certs.value.length) {
    uni.showToast({ title: "暂无证书", icon: "none" });
    return;
  }
  uni.previewImage({ urls: certs.value, current: certs.value[0] });
}

function openCourse(id: string) {
  uni.navigateTo({ url: `/package-coach/pages/course/course?id=${id}` });
}

function goBack() {
  uni.navigateBack({ fail: () => uni.switchTab({ url: "/pages/coach/coach" }) });
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #111827;
}
.hero {
  position: relative;
  flex-shrink: 0;
  min-height: 560rpx;
  padding: 28rpx 28rpx 56rpx;
  box-sizing: border-box;
  color: #fff;
  overflow: hidden;
}
.hero-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.hero-mask { position: absolute; inset: 0; background: linear-gradient(180deg, rgba(0,0,0,.25), rgba(0,0,0,.72)); }
.nav {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  height: auto;
  padding-left: 8rpx;
  padding-right: 8rpx;
}
.back {
  width: 72rpx;
  height: 72rpx;
  line-height: 64rpx;
  text-align: center;
  font-size: 56rpx;
  font-weight: 300;
  color: #fff;
}
.nav-title { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: #fff; }
.nav-space { width: 72rpx; }
.profile, .stats { position: relative; z-index: 1; }
.profile { display: flex; gap: 20rpx; align-items: center; }
.avatar { width: 120rpx; height: 120rpx; border-radius: 50%; border: 4rpx solid rgba(255,255,255,.7); background: #333; }
.who { flex: 1; min-width: 0; }
.name { display: block; font-size: 44rpx; font-weight: 800; }
.rating { display: block; margin-top: 8rpx; color: #fbbf24; font-size: 24rpx; }
.specialty { display: block; margin-top: 8rpx; font-size: 24rpx; opacity: 0.92; }
.stats { display: flex; margin-top: 36rpx; }
.stat { flex: 1; text-align: center; }
.num { display: block; font-size: 36rpx; font-weight: 700; }
.label { display: block; margin-top: 6rpx; font-size: 22rpx; opacity: 0.85; }
.sheet {
  flex: 1;
  margin-top: -32rpx;
  position: relative;
  z-index: 2;
  padding: 28rpx 24rpx calc(48rpx + env(safe-area-inset-bottom));
  border-radius: 32rpx 32rpx 0 0;
  background: #fff;
  box-sizing: border-box;
}
.sheet-title { display: block; font-size: 34rpx; font-weight: 800; margin-bottom: 16rpx; }
.course {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 20rpx 0;
  border-bottom: 1px solid #eceff3;
}
.cover { width: 120rpx; height: 120rpx; border-radius: 16rpx; background: #f3f4f6; flex-shrink: 0; }
.cover.soft { display: flex; align-items: center; justify-content: center; color: #9ca3af; font-weight: 700; }
.info { flex: 1; min-width: 0; }
.cname { display: block; font-size: 30rpx; font-weight: 700; }
.cmeta { display: block; margin-top: 8rpx; color: #ea580c; font-size: 22rpx; }
.cprice { display: block; margin-top: 10rpx; color: #ea580c; font-size: 30rpx; font-weight: 700; }
.btn {
  flex-shrink: 0;
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #ea580c;
  color: #fff;
  font-size: 24rpx;
  font-weight: 700;
}
.empty { color: #8a8f98; font-size: 24rpx; padding: 24rpx 0; }
</style>
