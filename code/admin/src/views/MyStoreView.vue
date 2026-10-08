<template>
  <div>
    <h1>本店档案</h1>
    <p class="lead">门店账号只能查看，不能修改电话、指引和营业信息。</p>
    <el-descriptions v-if="store" :column="1" border>
      <el-descriptions-item label="编号">{{ store.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ store.name }}</el-descriptions-item>
      <el-descriptions-item label="地址">{{ store.province }} {{ store.city }} {{ store.address }}</el-descriptions-item>
      <el-descriptions-item label="营业状态">{{ store.status === "OPEN" ? "营业" : "停业" }}</el-descriptions-item>
      <el-descriptions-item label="WiFi">{{ store.wifiSsid || "未配置" }} / {{ store.wifiPassword || "未配置" }}</el-descriptions-item>
    </el-descriptions>
    <h3>电话</h3>
    <el-table :data="phones">
      <el-table-column prop="phoneType" label="类型" />
      <el-table-column prop="phone" label="号码" />
      <el-table-column prop="timeStart" label="开始" />
      <el-table-column prop="timeEnd" label="结束" />
    </el-table>
    <h3>指引</h3>
    <el-empty v-if="guides.length === 0" description="还没有指引" />
    <el-table v-else :data="guides">
      <el-table-column prop="caption" label="说明" />
      <el-table-column prop="imageUrl" label="图片" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http, type ApiBody } from "../api/http";

interface StoreRow {
  id: string;
  code: string;
  name: string;
  province: string;
  city: string;
  address: string;
  status: string;
  wifiSsid: string | null;
  wifiPassword: string | null;
}

const store = ref<StoreRow | null>(null);
const phones = ref<unknown[]>([]);
const guides = ref<unknown[]>([]);

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  store.value = response.data.data[0] || null;
  if (!store.value) {
    return;
  }
  const phoneRes = await http.get<ApiBody<unknown[]>>(`/api/admin/stores/${store.value.id}/phones`);
  phones.value = phoneRes.data.data;
  const guideRes = await http.get<ApiBody<unknown[]>>(`/api/admin/stores/${store.value.id}/guides`);
  guides.value = guideRes.data.data;
});
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
