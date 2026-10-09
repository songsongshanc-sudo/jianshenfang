<template>
  <div>
    <h1>首页 Banner</h1>
    <p class="lead">图片是轮播上显示的图，可以填网址，也可以上传本地图片。链接是点这张图之后要去的页面，可以不填。</p>
    <el-form :inline="true" @submit.prevent="create">
      <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="图片">
        <ImageField v-model="form.imageUrl" biz="BANNER" />
      </el-form-item>
      <el-form-item label="链接"><el-input v-model="form.linkUrl" class="link-input" placeholder="点击图片跳转链接，选填" /></el-form-item>
      <el-form-item label="排序"><el-input v-model="form.sortNo" /></el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">新增</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="title" label="标题" />
      <el-table-column label="图片">
        <template #default="{ row }">
          <button v-if="row.imageUrl" type="button" class="preview" @click="preview?.show({ title: row.title, image: row.imageUrl })">预览图片</button>
        </template>
      </el-table-column>
      <el-table-column label="链接">
        <template #default="{ row }">
          <button v-if="row.linkUrl" type="button" class="preview" @click="openLink(row.linkUrl)">{{ row.linkUrl }}</button>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link @click="toggle(row)">{{ row.status === "ON" ? "下架" : "上架" }}</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="editing" title="编辑 Banner" width="640px">
      <el-form label-width="70px">
        <el-form-item label="标题"><el-input v-model="draft.title" /></el-form-item>
        <el-form-item label="图片"><ImageField v-model="draft.imageUrl" biz="BANNER" /></el-form-item>
        <el-form-item label="链接"><el-input v-model="draft.linkUrl" /></el-form-item>
        <el-form-item label="排序"><el-input v-model="draft.sortNo" /></el-form-item>
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
import ImageField from "../components/ImageField.vue";
import { http, type ApiBody } from "../api/http";

interface BannerRow {
  id: string;
  title: string;
  imageUrl: string;
  linkUrl: string | null;
  sortNo: number;
  status: string;
}

const rows = ref<BannerRow[]>([]);
const form = reactive({ title: "", imageUrl: "", linkUrl: "", sortNo: "0" });
const editing = ref(false);
const draft = reactive({ id: "", title: "", imageUrl: "", linkUrl: "", sortNo: "0", status: "ON" });
const preview = ref<InstanceType<typeof ContentPreview>>();

async function load() {
  const response = await http.get<ApiBody<BannerRow[]>>("/api/admin/banners");
  rows.value = response.data.data;
}

async function create() {
  await http.post("/api/admin/banners", {
    title: form.title,
    imageUrl: form.imageUrl,
    linkUrl: form.linkUrl,
    sortNo: Number(form.sortNo),
    status: "ON"
  });
  form.title = "";
  form.imageUrl = "";
  await load();
}

function openLink(url: string) {
  if (/^https?:\/\//i.test(url)) {
    window.open(url, "_blank", "noopener");
    return;
  }
  preview.value?.show({ title: "链接", html: `<p>${url.replace(/</g, "")}</p>` });
}

function openEdit(row: BannerRow) {
  draft.id = row.id;
  draft.title = row.title;
  draft.imageUrl = row.imageUrl;
  draft.linkUrl = row.linkUrl || "";
  draft.sortNo = String(row.sortNo);
  draft.status = row.status;
  editing.value = true;
}

async function save() {
  await http.put(`/api/admin/banners/${draft.id}`, {
    title: draft.title,
    imageUrl: draft.imageUrl,
    linkUrl: draft.linkUrl,
    sortNo: Number(draft.sortNo),
    status: draft.status
  });
  editing.value = false;
  ElMessage.success("已保存");
  await load();
}

async function toggle(row: BannerRow) {
  await http.put(`/api/admin/banners/${row.id}`, {
    title: row.title,
    imageUrl: row.imageUrl,
    linkUrl: row.linkUrl,
    sortNo: row.sortNo,
    status: row.status === "ON" ? "OFF" : "ON"
  });
  await load();
}

async function remove(id: string) {
  try {
    await ElMessageBox.confirm("删除后首页不再显示这张图。", "删除 Banner", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/banners/${id}`);
  ElMessage.success("已删除");
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 { margin-top: 0; font-size: 22px; }
.preview { padding: 0; border: 0; background: none; color: var(--el-color-primary); cursor: pointer; text-align: left; }
</style>
