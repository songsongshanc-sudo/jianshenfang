<template>
  <div>
    <h1>滚动公告</h1>
    <p class="lead">上架后的公告会显示在小程序首页。正文用富文本编写，列表里点内容可以预览。</p>
    <el-form label-width="70px" class="editor" @submit.prevent="create">
      <el-form-item label="内容"><RichTextField v-model="form.content" /></el-form-item>
      <el-form-item label="排序"><el-input v-model="form.sortNo" style="width: 120px" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">新增</el-button></el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column label="内容">
        <template #default="{ row }">
          <button type="button" class="preview" @click="preview?.show({ title: '公告', html: row.content })">
            {{ plainText(row.content) || "查看图文" }}
          </button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ row.status === "ON" ? "上架" : "下架" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link @click="toggle(row)">{{ row.status === "ON" ? "下架" : "上架" }}</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="editing" title="编辑公告" width="820px">
      <el-form label-width="70px">
        <el-form-item label="内容"><RichTextField v-model="draft.content" /></el-form-item>
        <el-form-item label="排序"><el-input v-model="draft.sortNo" style="width: 120px" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <ContentPreview ref="preview" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import ContentPreview from "../components/ContentPreview.vue";
import RichTextField from "../components/RichTextField.vue";
import { http, type ApiBody } from "../api/http";
import { plainText } from "../plainText";

interface NoticeRow {
  id: string;
  content: string;
  status: string;
  sortNo: number;
}

const rows = ref<NoticeRow[]>([]);
const form = reactive({ content: "", sortNo: "0" });
const editing = ref(false);
const draft = reactive({ id: "", content: "", sortNo: "0", status: "ON" });
const preview = ref<InstanceType<typeof ContentPreview>>();

async function load() {
  const response = await http.get<ApiBody<NoticeRow[]>>("/api/admin/notices");
  rows.value = response.data.data;
}

async function create() {
  await http.post("/api/admin/notices", { content: form.content, sortNo: Number(form.sortNo), status: "ON" });
  form.content = "";
  ElMessage.success("已新增");
  await load();
}

function openEdit(row: NoticeRow) {
  draft.id = row.id;
  draft.content = row.content;
  draft.sortNo = String(row.sortNo);
  draft.status = row.status;
  editing.value = true;
}

async function save() {
  await http.put(`/api/admin/notices/${draft.id}`, {
    content: draft.content,
    sortNo: Number(draft.sortNo),
    status: draft.status
  });
  editing.value = false;
  ElMessage.success("已保存");
  await load();
}

async function toggle(row: NoticeRow) {
  await http.put(`/api/admin/notices/${row.id}`, {
    content: row.content,
    sortNo: row.sortNo,
    status: row.status === "ON" ? "OFF" : "ON"
  });
  await load();
}

async function remove(id: string) {
  try {
    await ElMessageBox.confirm("删除后首页不再显示这条公告。", "删除公告", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/notices/${id}`);
  ElMessage.success("已删除");
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 { margin-top: 0; font-size: 22px; }
.editor { max-width: 860px; }
.preview { padding: 0; border: 0; background: none; color: var(--el-color-primary); cursor: pointer; text-align: left; }
</style>
