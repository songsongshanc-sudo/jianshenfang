<template>
  <div>
    <h1>会员服务说明</h1>
    <p class="lead">购卡确认页展示的图文说明。发布后不可改，下一版先新建草稿。支持插入图片。</p>
    <div class="toolbar">
      <el-button :disabled="hasDraft" @click="createDraft">新建草稿</el-button>
    </div>
    <el-table :data="rows">
      <el-table-column prop="versionNo" label="版本" width="80" />
      <el-table-column label="标题">
        <template #default="{ row }">
          <button type="button" class="preview" @click="preview?.show({ title: row.title, html: row.content })">{{ row.title }}</button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">{{ row.status === "DRAFT" ? "草稿" : "已发布" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="open" title="编辑草稿" width="860px">
      <el-form label-width="70px">
        <el-form-item label="标题"><el-input v-model="draft.title" /></el-form-item>
        <el-form-item label="正文"><RichTextField v-model="draft.content" biz="SERVICE_GUIDE" placeholder="介绍会员权益、到店规则等，可插图" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="save">保存草稿</el-button>
        <el-button type="primary" @click="publish">发布</el-button>
      </template>
    </el-dialog>
    <ContentPreview ref="preview" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ContentPreview from "../components/ContentPreview.vue";
import RichTextField from "../components/RichTextField.vue";
import { http, type ApiBody } from "../api/http";

interface GuideRow {
  id: string;
  title: string;
  versionNo: number;
  content: string;
  status: string;
}

const rows = ref<GuideRow[]>([]);
const open = ref(false);
const draft = reactive({ id: "", title: "", content: "" });
const preview = ref<InstanceType<typeof ContentPreview>>();
const hasDraft = computed(() => rows.value.some((row) => row.status === "DRAFT"));

async function load() {
  const response = await http.get<ApiBody<GuideRow[]>>("/api/admin/service-guides");
  rows.value = response.data.data;
}

function edit(row: GuideRow) {
  draft.id = row.id;
  draft.title = row.title;
  draft.content = row.content;
  open.value = true;
}

async function createDraft() {
  const response = await http.post<ApiBody<GuideRow>>("/api/admin/service-guides");
  await load();
  edit(response.data.data);
}

async function save() {
  await http.put(`/api/admin/service-guides/${draft.id}`, { title: draft.title, content: draft.content });
  ElMessage.success("草稿已保存");
  await load();
}

async function publish() {
  await http.put(`/api/admin/service-guides/${draft.id}`, { title: draft.title, content: draft.content });
  await http.post(`/api/admin/service-guides/${draft.id}/publish`);
  ElMessage.success("已发布");
  open.value = false;
  await load();
}

async function remove(row: GuideRow) {
  try {
    await ElMessageBox.confirm(`删除「${row.title}」版本 ${row.versionNo}？`, "删除服务说明", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/service-guides/${row.id}`);
  ElMessage.success("已删除");
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 { margin-top: 0; font-size: 22px; }
.lead { color: #78716c; margin-bottom: 16px; }
.toolbar { margin-bottom: 12px; }
.preview { padding: 0; border: 0; background: none; color: var(--el-color-primary); cursor: pointer; }
</style>
