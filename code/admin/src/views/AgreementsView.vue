<template>
  <div>
    <h1>会员协议</h1>
    <p class="lead">发布之后这份内容不能再改。下一版要先新建草稿。</p>
    <div class="toolbar">
      <el-button :disabled="hasDraft" @click="createDraft">新建草稿</el-button>
    </div>
    <el-table :data="rows">
      <el-table-column prop="versionNo" label="版本" width="80" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">{{ row.status === "DRAFT" ? "草稿" : "已发布" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="edit(row)">编辑</el-button>
          <span v-else>只读</span>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="open" title="编辑草稿" width="640px">
      <el-form label-width="70px">
        <el-form-item label="标题"><el-input v-model="draft.title" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="draft.content" type="textarea" :rows="8" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="save">保存草稿</el-button>
        <el-button type="primary" @click="publish">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
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

onMounted(load);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
.toolbar {
  margin-bottom: 12px;
}
</style>
