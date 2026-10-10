<template>
  <web-view v-if="src" :src="src" />
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";

const src = ref("");

onLoad((query) => {
  const url = decodeURIComponent(String(query?.url || ""));
  if (!/^https?:\/\//i.test(url)) {
    uni.showToast({ title: "链接无效", icon: "none" });
    return;
  }
  src.value = url;
});
</script>
