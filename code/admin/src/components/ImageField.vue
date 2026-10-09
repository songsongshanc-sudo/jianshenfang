<template>
  <div class="image-field">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder || (video ? '视频链接' : '图片链接')"
      clearable
      @update:model-value="emit('update:modelValue', $event ?? '')"
    />
    <label class="upload">
      上传
      <input class="picker" type="file" :accept="accept" @change="onFile" />
    </label>
  </div>
</template>

<script setup lang="ts">
import { http, type ApiBody } from "../api/http";

const props = defineProps<{
  modelValue: string;
  biz: "BANNER" | "COVER" | "GUIDE" | "EQUIPMENT" | "EQUIPMENT_VIDEO";
  accept?: string;
  placeholder?: string;
}>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const video = props.biz === "EQUIPMENT_VIDEO";
const accept = props.accept || (video ? "video/mp4,video/webm,video/quicktime" : "image/jpeg,image/png,image/webp,image/gif");

function onFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (file) {
    void upload(file);
  }
}

async function upload(file: File) {
  const body = new FormData();
  body.append("file", file);
  body.append("biz", props.biz);
  const response = await http.post<ApiBody<{ url: string }>>("/api/admin/files/images", body);
  emit("update:modelValue", response.data.data.url);
}
</script>

<style scoped>
.image-field {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 200px;
}
.image-field :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.upload {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  height: 32px;
  padding: 0 15px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  background: #fff;
  color: #606266;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
}
.upload:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}
.picker {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}
</style>
