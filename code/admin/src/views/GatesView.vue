<template>
  <div>
    <h1>闸机</h1>
    <p class="lead">总账号登记闸机，设备号填机身上的编号。闸机长连接地址是 /ws/gate。人脸在购卡后下发到本店闸机，离线会自动补发。模拟验码仍用旧密钥，方便没有闸机时验收开门规则。</p>
    <el-form v-if="session.role === 'MASTER'" :inline="true" @submit.prevent="create">
      <el-form-item label="门店">
        <el-select v-model="form.storeId" style="width: 180px">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="设备号"><el-input v-model="form.deviceSn" placeholder="机身设备号" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">登记</el-button></el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="storeName" label="门店" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="deviceSn" label="设备号" />
      <el-table-column prop="firmware" label="固件" width="120" />
      <el-table-column label="在线" width="80">
        <template #default="{ row }">{{ row.online ? "在线" : "离线" }}</template>
      </el-table-column>
      <el-table-column prop="pendingCount" label="待同步" width="90" />
      <el-table-column prop="failedCount" label="失败" width="70" />
      <el-table-column prop="lastError" label="失败原因" />
      <el-table-column label="签名" width="80">
        <template #default="{ row }">{{ row.signed ? "已启用" : "未启用" }}</template>
      </el-table-column>
      <el-table-column v-if="session.role === 'MASTER'" prop="secret" label="模拟密钥" />
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button link @click="retry(row.id)">重试失败</el-button>
          <el-button v-if="session.role === 'MASTER'" link @click="reset(row.id)">重置密钥</el-button>
          <el-button v-if="session.role === 'MASTER'" link @click="token(row.id)">启用签名</el-button>
          <el-button v-if="session.role === 'MASTER'" link @click="toggle(row)">{{ row.status === "ENABLED" ? "停用" : "启用" }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <h2 v-if="session.role === 'MASTER'">模拟验码</h2>
    <el-form v-if="session.role === 'MASTER'" :inline="true" @submit.prevent="simulate">
      <el-form-item label="设备">
        <el-select v-model="sim.sn" style="width: 180px">
          <el-option v-for="row in rows" :key="row.deviceSn" :label="row.name" :value="row.deviceSn" />
        </el-select>
      </el-form-item>
      <el-form-item label="会员编号"><el-input v-model="sim.memberNo" /></el-form-item>
      <el-form-item><el-button native-type="submit">验码</el-button></el-form-item>
    </el-form>
    <p v-if="simResult">{{ simResult }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

interface Device {
  id: string;
  storeId: string;
  storeName: string;
  deviceSn: string;
  name: string;
  status: string;
  online: boolean;
  secret: string | null;
  firmware: string;
  pendingCount: number;
  failedCount: number;
  lastError: string;
  signed: boolean;
}

const session = useSessionStore();
const stores = ref<{ id: string; name: string }[]>([]);
const rows = ref<Device[]>([]);
const form = reactive({ storeId: "", name: "", deviceSn: "" });
const sim = reactive({ sn: "", memberNo: "" });
const simResult = ref("");

onMounted(load);

async function load() {
  if (session.role === "MASTER") {
    const storeRes = await http.get<ApiBody<{ id: string; name: string }[]>>("/api/admin/stores");
    stores.value = storeRes.data.data;
  }
  const response = await http.get<ApiBody<Device[]>>("/api/admin/gates");
  rows.value = response.data.data;
}

async function create() {
  if (!form.deviceSn.trim()) {
    ElMessage.warning("请填写机身设备号");
    return;
  }
  await http.post("/api/admin/gates", form);
  form.name = "";
  form.deviceSn = "";
  ElMessage.success("已登记");
  await load();
}

async function retry(id: string) {
  await http.post(`/api/admin/gates/${id}/retry`);
  ElMessage.success("已重新排队");
  await load();
}

async function token(id: string) {
  const response = await http.post<ApiBody<{ token: string }>>(`/api/admin/gates/${id}/token`);
  ElMessage.success("签名口令：" + response.data.data.token);
  await load();
}

async function reset(id: string) {
  await http.post(`/api/admin/gates/${id}/secret`);
  ElMessage.success("已重置");
  await load();
}

async function toggle(row: Device) {
  await http.post(`/api/admin/gates/${row.id}/status`, { status: row.status === "ENABLED" ? "DISABLED" : "ENABLED" });
  await load();
}

async function simulate() {
  const device = rows.value.find((row) => row.deviceSn === sim.sn);
  if (!device?.secret) return;
  const body = JSON.stringify({ memberNo: sim.memberNo });
  const timestamp = String(Date.now());
  const nonce = crypto.randomUUID();
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(device.secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const payload = `${device.deviceSn}\n${timestamp}\n${nonce}\n${body}`;
  const signed = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload));
  const signature = [...new Uint8Array(signed)].map((item) => item.toString(16).padStart(2, "0")).join("");
  const response = await http.post<ApiBody<{ open: boolean; reason: string }>>("/api/gate/v1/verify", body, {
    headers: {
      "Content-Type": "application/json",
      "X-Device-Sn": device.deviceSn,
      "X-Timestamp": timestamp,
      "X-Nonce": nonce,
      "X-Signature": signature
    },
    transformRequest: [(data) => data]
  });
  simResult.value = response.data.data.open ? "开门" : response.data.data.reason;
}
</script>
