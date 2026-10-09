<template>
  <div>
    <h1>系统配置</h1>
    <p class="lead">这里只调进店防抖、在线人数窗口和未支付订单超时。购买后不能退款。</p>
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
}

const loaded = ref(false);
const form = reactive({
  entryDebounceSeconds: "15",
  onlineWindowMinutes: "90",
  orderExpireMinutes: "15"
});

async function load() {
  const response = await http.get<ApiBody<ConfigBody>>("/api/admin/configs");
  form.entryDebounceSeconds = String(response.data.data.entryDebounceSeconds);
  form.onlineWindowMinutes = String(response.data.data.onlineWindowMinutes);
  form.orderExpireMinutes = String(response.data.data.orderExpireMinutes);
  loaded.value = true;
}

async function save() {
  await http.put("/api/admin/configs", {
    entryDebounceSeconds: Number(form.entryDebounceSeconds),
    onlineWindowMinutes: Number(form.onlineWindowMinutes),
    orderExpireMinutes: Number(form.orderExpireMinutes)
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
