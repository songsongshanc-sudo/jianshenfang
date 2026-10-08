<template>
  <div>
    <h1>系统配置</h1>
    <p class="lead">退款金额 = 实付 −（申请日 − 购卡日 + 1）× 每日扣除。留空时不能做特殊退款。</p>
    <el-form v-if="loaded" label-width="140px" style="max-width: 480px" @submit.prevent="save">
      <el-form-item label="进店防抖（秒）">
        <el-input v-model="form.entryDebounceSeconds" />
      </el-form-item>
      <el-form-item label="在线窗口（分钟）">
        <el-input v-model="form.onlineWindowMinutes" />
      </el-form-item>
      <el-form-item label="未支付超时（分钟）">
        <el-input v-model="form.orderExpireMinutes" />
      </el-form-item>
      <el-form-item label="退款日扣（分）">
        <el-input v-model="form.refundDailyDeductFen" placeholder="留空表示先不退款" />
      </el-form-item>
      <el-button type="primary" native-type="submit">保存</el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";

interface ConfigBody {
  entryDebounceSeconds: number;
  onlineWindowMinutes: number;
  orderExpireMinutes: number;
  refundDailyDeductFen: string;
}

const loaded = ref(false);
const form = reactive({
  entryDebounceSeconds: "15",
  onlineWindowMinutes: "90",
  orderExpireMinutes: "15",
  refundDailyDeductFen: ""
});

async function load() {
  const response = await http.get<ApiBody<ConfigBody>>("/api/admin/configs");
  form.entryDebounceSeconds = String(response.data.data.entryDebounceSeconds);
  form.onlineWindowMinutes = String(response.data.data.onlineWindowMinutes);
  form.orderExpireMinutes = String(response.data.data.orderExpireMinutes);
  form.refundDailyDeductFen = response.data.data.refundDailyDeductFen || "";
  loaded.value = true;
}

async function save() {
  await http.put("/api/admin/configs", {
    entryDebounceSeconds: Number(form.entryDebounceSeconds),
    onlineWindowMinutes: Number(form.onlineWindowMinutes),
    orderExpireMinutes: Number(form.orderExpireMinutes),
    refundDailyDeductFen: form.refundDailyDeductFen
  });
  ElMessage.success("已保存");
}

onMounted(load);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
