<template>
  <div>
    <h1>进店记录</h1>
    <p class="lead">列表只显示人脸是否已采集。总账号查看原始采集记录会写入审计，当前本机没有保存照片原图。</p>
    <el-table :data="rows">
      <el-table-column prop="deviceSn" label="闸机" />
      <el-table-column prop="memberNo" label="会员编号" />
      <el-table-column prop="face" label="人脸" width="90" />
      <el-table-column prop="result" label="结果" width="110" />
      <el-table-column prop="createdAt" label="时间" />
      <el-table-column v-if="session.role === 'MASTER'" label="操作" width="120">
        <template #default="{ row }">
          <el-button link @click="viewFace(row.userId)">查看采集</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const session = useSessionStore();
const rows = ref<Array<{ userId: string; memberNo: string; deviceSn: string; face: string; result: string; createdAt: string }>>([]);

onMounted(async () => {
  const response = await http.get<ApiBody<typeof rows.value>>("/api/admin/doors");
  rows.value = response.data.data;
});

async function viewFace(userId: string) {
  const response = await http.get<ApiBody<{ photo: string }>>(`/api/admin/users/${userId}/face`);
  ElMessage.info(response.data.data.photo + "，本机没有照片原图");
}
</script>
