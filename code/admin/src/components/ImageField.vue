<template>
  <div class="image-field">
    <el-input
      :model-value="modelValue"
      placeholder="图片链接"
      clearable
      @update:model-value="emit('update:modelValue', $event ?? '')"
    />
    <el-button native-type="button" @click="pick">上传</el-button>
  </div>
</template>

<script setup lang="ts">
import { http, type ApiBody } from "../api/http";

const props = defineProps<{ modelValue: string; biz: "BANNER" | "COVER" | "GUIDE" }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

function pick() {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/jpeg,image/png,image/webp,image/gif";
  input.onchange = () => {
    const file = input.files?.[0];
    if (file) {
      void upload(file);
    }
  };
  input.click();
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
</style>
