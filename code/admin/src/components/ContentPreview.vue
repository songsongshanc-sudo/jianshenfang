<template>
  <el-dialog v-model="visible" :title="title" width="760px" destroy-on-close>
    <div v-if="html" class="html" v-html="html" />
    <img v-if="image" class="media" :src="image" alt="" />
    <video v-if="video" class="media" :src="video" controls />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";

const visible = ref(false);
const title = ref("预览");
const html = ref("");
const image = ref("");
const video = ref("");

function show(payload: { title?: string; html?: string; image?: string; video?: string }) {
  title.value = payload.title || "预览";
  html.value = payload.html || "";
  image.value = payload.image || "";
  video.value = payload.video || "";
  visible.value = true;
}

defineExpose({ show });
</script>

<style scoped>
.html { line-height: 1.7; word-break: break-word; }
.html :deep(img),
.media { max-width: 100%; height: auto; display: block; }
.media { margin-top: 8px; }
</style>
