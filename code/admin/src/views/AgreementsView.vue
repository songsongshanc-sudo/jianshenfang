<template>
  <div>
    <h1>会员协议</h1>
    <p class="lead">发布之后这份内容不能再改。下一版要先新建草稿。点标题可以预览正文。</p>
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
        <el-form-item label="正文"><RichTextField v-model="draft.content" /></el-form-item>
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

interface AgreementRow {
  id: string;
  title: string;
  versionNo: number;
  content: string;
  status: string;
}

const rows = ref<AgreementRow[]>([]);
const open = ref(false);
const draft = reactive({ id: "", title: "", content: "" });
const preview = ref<InstanceType<typeof ContentPreview>>();
const hasDraft = computed(() => rows.value.some((row) => row.status === "DRAFT"));

async function load() {
  const response = await http.get<ApiBody<AgreementRow[]>>("/api/admin/agreements");
  rows.value = response.data.data;
}

function edit(row: AgreementRow) {
  draft.id = row.id;
  draft.title = row.title;
  draft.content = row.content;
  open.value = true;
}

async function createDraft() {
  const response = await http.post<ApiBody<AgreementRow>>("/api/admin/agreements");
  await load();
  edit(response.data.data);
}

async function save() {
  await http.put(`/api/admin/agreements/${draft.id}`, { title: draft.title, content: draft.content });
  ElMessage.success("草稿已保存");
  await load();
}

async function publish() {
  await http.put(`/api/admin/agreements/${draft.id}`, { title: draft.title, content: draft.content });
  await http.post(`/api/admin/agreements/${draft.id}/publish`);
  ElMessage.success("已发布，旧版本只读");
  open.value = false;
  await load();
}

async function remove(row: AgreementRow) {
  try {
    await ElMessageBox.confirm(`删除「${row.title}」版本 ${row.versionNo}。已有订单使用的协议不能删。`, "删除协议", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/agreements/${row.id}`);
  ElMessage.success("已删除");
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 { margin-top: 0; font-size: 22px; }
.toolbar { margin-bottom: 12px; }
.preview { padding: 0; border: 0; background: none; color: var(--el-color-primary); cursor: pointer; }
</style>
