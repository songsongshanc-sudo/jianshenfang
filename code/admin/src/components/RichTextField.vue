<template>
  <div class="rich">
    <div class="bar">
      <el-select :model-value="block" size="small" style="width: 108px" @change="setBlock">
        <el-option label="正文" value="p" />
        <el-option label="标题 1" value="h1" />
        <el-option label="标题 2" value="h2" />
        <el-option label="标题 3" value="h3" />
      </el-select>
      <button type="button" title="加粗" @click="exec('bold')"><b>B</b></button>
      <button type="button" title="斜体" @click="exec('italic')"><i>I</i></button>
      <button type="button" title="下划线" @click="exec('underline')"><u>U</u></button>
      <el-color-picker size="small" :model-value="color" @change="setColor" />
      <button type="button" title="左对齐" @click="exec('justifyLeft')">左</button>
      <button type="button" title="居中" @click="exec('justifyCenter')">中</button>
      <button type="button" title="右对齐" @click="exec('justifyRight')">右</button>
      <button type="button" title="无序列表" @click="exec('insertUnorderedList')">列表</button>
      <button type="button" title="有序列表" @click="exec('insertOrderedList')">编号</button>
      <button type="button" title="链接" @click="addLink">链接</button>
      <label class="upload" title="插入图片">
        图片
        <input class="picker" type="file" accept="image/jpeg,image/png,image/webp,image/gif" @change="onFile" />
      </label>
      <button type="button" title="清除格式" @click="exec('removeFormat')">清除</button>
    </div>
    <div
      ref="editor"
      class="body"
      contenteditable="true"
      :data-placeholder="placeholder || '在这里编写内容，可插入图片'"
      @input="sync"
      @keyup="rememberBlock"
      @mouseup="rememberBlock"
      @blur="saveSelection"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http, type ApiBody } from "../api/http";

const props = withDefaults(defineProps<{ modelValue?: string; biz?: string; placeholder?: string }>(), {
  biz: "RICH"
});
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const editor = ref<HTMLDivElement>();
const block = ref("p");
const color = ref("#1c1917");
let savedRange: Range | null = null;

watch(() => props.modelValue, (value) => {
  const next = value || "";
  if (editor.value && editor.value.innerHTML !== next) {
    editor.value.innerHTML = next;
  }
});

onMounted(() => {
  if (editor.value) {
    editor.value.innerHTML = props.modelValue || "";
  }
});

function sync() {
  emit("update:modelValue", editor.value?.innerHTML || "");
}

function saveSelection() {
  const selection = window.getSelection();
  if (!selection || selection.rangeCount === 0 || !editor.value) {
    return;
  }
  const range = selection.getRangeAt(0);
  if (editor.value.contains(range.commonAncestorContainer)) {
    savedRange = range.cloneRange();
  }
}

function restoreSelection() {
  editor.value?.focus();
  const selection = window.getSelection();
  if (!selection) return;
  selection.removeAllRanges();
  if (savedRange) {
    selection.addRange(savedRange);
    return;
  }
  if (!editor.value) return;
  const range = document.createRange();
  range.selectNodeContents(editor.value);
  range.collapse(false);
  selection.addRange(range);
}

function exec(command: string, value?: string) {
  restoreSelection();
  document.execCommand(command, false, value);
  sync();
  saveSelection();
  rememberBlock();
}

function setBlock(value: string) {
  block.value = value;
  exec("formatBlock", `<${value}>`);
}

function setColor(value: string | null) {
  if (!value) return;
  color.value = value;
  exec("foreColor", value);
}

function rememberBlock() {
  const node = document.getSelection()?.anchorNode?.parentElement;
  const tag = node?.closest("h1,h2,h3,p")?.tagName.toLowerCase();
  block.value = tag === "h1" || tag === "h2" || tag === "h3" ? tag : "p";
  saveSelection();
}

async function addLink() {
  saveSelection();
  try {
    const { value } = await ElMessageBox.prompt("填写链接地址", "插入链接", {
      inputPlaceholder: "https://",
      confirmButtonText: "插入",
      cancelButtonText: "取消"
    });
    if (value?.trim()) {
      exec("createLink", value.trim());
    }
  } catch {
    restoreSelection();
  }
}

async function onFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  saveSelection();
  try {
    const body = new FormData();
    body.append("file", file);
    body.append("biz", props.biz);
    const response = await http.post<ApiBody<{ url: string }>>("/api/admin/files/images", body);
    const url = response.data.data.url.replace(/"/g, "");
    restoreSelection();
    document.execCommand("insertHTML", false, `<img src="${url}" style="max-width:100%;">`);
    sync();
    saveSelection();
  } catch {
    ElMessage.error("图片上传失败");
  }
}
</script>

<style scoped>
.rich {
  width: 100%;
  max-width: 760px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  background: #fff;
}
.bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  border-radius: 10px 10px 0 0;
}
.bar button,
.upload {
  position: relative;
  height: 28px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  cursor: pointer;
  line-height: 26px;
  font-size: 13px;
}
.bar button:hover,
.upload:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}
.body {
  min-height: 220px;
  padding: 12px 14px;
  outline: none;
  line-height: 1.7;
  color: #1c1917;
}
.body:empty::before {
  content: attr(data-placeholder);
  color: #a8abb2;
}
.body :deep(h1) { font-size: 28px; margin: 8px 0; }
.body :deep(h2) { font-size: 22px; margin: 8px 0; }
.body :deep(h3) { font-size: 18px; margin: 8px 0; }
.body :deep(img) { max-width: 100%; height: auto; display: block; margin: 8px 0; }
.picker { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
</style>
