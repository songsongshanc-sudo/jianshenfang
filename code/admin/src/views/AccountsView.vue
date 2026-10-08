<template>
  <div>
    <h1>门店账号</h1>
    <p class="lead">一个账号只能绑定一家店，创建后用这个账号登录，只能看到那一家店。</p>
    <el-form :inline="true" :model="form" @submit.prevent="create">
      <el-form-item label="账号"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" /></el-form-item>
      <el-form-item label="门店">
        <el-select v-model="form.storeId" placeholder="选择门店" style="width: 180px">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">创建</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";

interface StoreRow {
  id: string;
  name: string;
}

const stores = ref<StoreRow[]>([]);
const form = reactive({
  username: "",
  password: "",
  storeId: ""
});

async function create() {
  await http.post("/api/admin/accounts", form);
  ElMessage.success("门店账号已创建");
  form.username = "";
  form.password = "";
}

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  stores.value = response.data.data;
});
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
