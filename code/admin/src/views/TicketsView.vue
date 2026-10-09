<template>
  <div>
    <h1>{{ title }}</h1>
    <el-table :data="rows">
      <el-table-column prop="id" label="编号" width="180" />
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">{{ text(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link @click="setStatus(row.id, 'PENDING')">待处理</el-button>
          <el-button link @click="setStatus(row.id, 'DOING')">处理中</el-button>
          <el-button link @click="setStatus(row.id, 'DONE')">已完成</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { http, type ApiBody } from "../api/http";

const route = useRoute();
const rows = ref<Array<{ id: string; content?: string; name?: string; status?: string }>>([]);
const title = computed(() => (route.path === "/complaints" ? "投诉" : route.path === "/lost" ? "失物" : "报修"));
const api = computed(() => route.path);

watch(() => route.path, load);
onMounted(load);

async function load() {
  const response = await http.get<ApiBody<typeof rows.value>>(`/api/admin${api.value}`);
  rows.value = response.data.data;
}

async function setStatus(id: string, status: string) {
  await http.post(`/api/admin${api.value}/${id}/status`, { status });
  await load();
}

function text(status?: string) {
  if (status === "DOING") return "处理中";
  if (status === "DONE") return "已完成";
  if (!status) return "待处理";
  return "待处理";
}
</script>
