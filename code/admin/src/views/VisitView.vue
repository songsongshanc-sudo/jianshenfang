<template>
  <div>
    <h1>参观卡</h1>
    <p class="lead">押金/时长等全局配置。用户购卡时绑定选中门店，只能去该店刷脸参观一次；同店再次刷脸视为离店。按时离店退回，超时扣除。</p>

    <el-form v-if="loaded && isMaster" label-width="140px" style="max-width: 640px" @submit.prevent="save">
      <el-form-item label="开启参观">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="押金（元）">
        <el-input v-model="form.depositYuan" />
      </el-form-item>
      <el-form-item label="可参观时长（分钟）">
        <el-input v-model="form.durationMinutes" />
      </el-form-item>
      <el-form-item label="有效期（天）">
        <el-input v-model="form.validDays" />
      </el-form-item>
      <el-form-item label="提醒提前（分钟）">
        <el-input v-model="form.warnMinutes" />
      </el-form-item>
      <el-form-item label="说明文案">
        <el-input v-model="form.notice" type="textarea" :rows="5" />
      </el-form-item>
      <el-button type="primary" native-type="submit">保存配置</el-button>
    </el-form>

    <h3>参观记录</h3>
    <el-table :data="rows">
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="storeName" label="进店门店" />
      <el-table-column label="押金" width="90">
        <template #default="{ row }">¥{{ (row.depositFen / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="140">
        <template #default="{ row }">{{ statusText(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="validUntil" label="有效期至" width="170" />
      <el-table-column prop="enteredAt" label="进店" width="170" />
      <el-table-column prop="exitedAt" label="离店" width="170" />
      <el-table-column prop="refundedAt" label="退款时间" width="170" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const session = useSessionStore();
const isMaster = session.role === "MASTER";

interface ConfigBody {
  enabled: boolean;
  depositFen: number;
  durationMinutes: number;
  validDays: number;
  warnMinutes: number;
  notice: string;
}

interface VisitRow {
  id: string;
  phone: string;
  storeName: string;
  depositFen: number;
  status: string;
  validUntil: string | null;
  enteredAt: string | null;
  exitedAt: string | null;
  refundedAt: string | null;
}

const loaded = ref(false);
const rows = ref<VisitRow[]>([]);
const form = reactive({
  enabled: false,
  depositYuan: "9.90",
  durationMinutes: "60",
  validDays: "30",
  warnMinutes: "5",
  notice: ""
});

function statusText(status: string) {
  const map: Record<string, string> = {
    UNUSED: "未进店",
    IN_STORE: "参观中",
    EXITED_REFUNDED: "已离店已退",
    TIMEOUT_FORFEIT: "已超时扣款",
    TIMEOUT_CLOSED: "超时已结束",
    REFUNDED_UNUSED: "未用退款",
    EXPIRED_REFUNDED: "到期退款",
    REFUND_PENDING: "退款处理中"
  };
  return map[status] || status;
}

async function load() {
  if (isMaster) {
    const config = await http.get<ApiBody<ConfigBody>>("/api/admin/visit/config");
    const data = config.data.data;
    form.enabled = data.enabled;
    form.depositYuan = (data.depositFen / 100).toFixed(2);
    form.durationMinutes = String(data.durationMinutes);
    form.validDays = String(data.validDays);
    form.warnMinutes = String(data.warnMinutes);
    form.notice = data.notice || "";
  }
  const list = await http.get<ApiBody<VisitRow[]>>("/api/admin/visit/passes");
  rows.value = list.data.data;
  loaded.value = true;
}

async function save() {
  const depositFen = Math.round(Number(form.depositYuan) * 100);
  await http.put("/api/admin/visit/config", {
    enabled: form.enabled,
    depositFen,
    durationMinutes: Number(form.durationMinutes),
    validDays: Number(form.validDays),
    warnMinutes: Number(form.warnMinutes),
    notice: form.notice
  });
  ElMessage.success("已保存");
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 { margin-top: 0; font-size: 22px; }
h3 { margin: 28px 0 12px; }
.lead { color: #606266; font-size: 13px; }
</style>
