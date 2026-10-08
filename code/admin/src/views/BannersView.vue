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
      <el-table-column prop="imageUrl" label="图片" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link @click="toggle(row)">{{ row.status === "ON" ? "下架" : "上架" }}</el-button>
          <el-button link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
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
  await http.delete(`/api/admin/banners/${id}`);
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
