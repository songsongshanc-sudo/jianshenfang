<template>
  <div>
    <h1>滚动公告</h1>
    <p class="lead">上架后的公告会显示在小程序首页。</p>
    <el-form :inline="true" @submit.prevent="create">
      <el-form-item label="内容"><el-input v-model="form.content" /></el-form-item>
      <el-form-item label="排序"><el-input v-model="form.sortNo" /></el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">新增</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="content" label="内容" />
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
import { http, type ApiBody } from "../api/http";

interface NoticeRow {
  id: string;
  content: string;
  status: string;
  sortNo: number;
}

const rows = ref<NoticeRow[]>([]);
const form = reactive({ content: "", sortNo: "0" });

async function load() {
  const response = await http.get<ApiBody<NoticeRow[]>>("/api/admin/notices");
  rows.value = response.data.data;
}

async function create() {
  await http.post("/api/admin/notices", { content: form.content, sortNo: Number(form.sortNo), status: "ON" });
  form.content = "";
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
  await http.delete(`/api/admin/notices/${id}`);
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
