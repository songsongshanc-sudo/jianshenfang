<template>
  <div>
    <h1>账号管理</h1>
    <p class="lead">这里列出总账号和全部门店账号。修改密码后，该账号需要用新密码重新登录。</p>
    <el-form :inline="true" :model="form" @submit.prevent="create">
      <el-form-item label="账号"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
      <el-form-item label="门店">
        <el-select v-model="form.storeId" placeholder="选择门店" style="width: 180px">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">创建门店账号</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="accounts">
      <el-table-column prop="username" label="账号" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ row.role === "MASTER" ? "总账号" : "门店账号" }}</template>
      </el-table-column>
      <el-table-column label="门店">
        <template #default="{ row }">{{ row.storeName || "—" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ row.status === "ACTIVE" ? "正常" : "已停用" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPassword(row)">修改密码</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editing" title="修改密码" width="420px">
      <el-form label-width="80px">
        <el-form-item label="账号"><el-input :model-value="current?.username" disabled /></el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="nextPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" @click="savePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

interface StoreRow {
  id: string;
  name: string;
}

interface AccountRow {
  id: string;
  username: string;
  role: string;
  storeId: string | null;
  storeName: string;
  status: string;
}

const router = useRouter();
const session = useSessionStore();
const stores = ref<StoreRow[]>([]);
const accounts = ref<AccountRow[]>([]);
const form = reactive({
  username: "",
  password: "",
  storeId: ""
});
const editing = ref(false);
const current = ref<AccountRow | null>(null);
const nextPassword = ref("");

async function loadAccounts() {
  const response = await http.get<ApiBody<AccountRow[]>>("/api/admin/accounts");
  accounts.value = response.data.data;
}

async function create() {
  await http.post("/api/admin/accounts", form);
  ElMessage.success("门店账号已创建");
  form.username = "";
  form.password = "";
  await loadAccounts();
}

async function remove(row: AccountRow) {
  try {
    await ElMessageBox.confirm(`删除账号 ${row.username} 后不能再登录。`, "删除账号", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  await http.delete(`/api/admin/accounts/${row.id}`);
  ElMessage.success("已删除");
  await loadAccounts();
}

function openPassword(row: AccountRow) {
  current.value = row;
  nextPassword.value = "";
  editing.value = true;
}

async function savePassword() {
  if (!current.value) return;
  if (nextPassword.value.trim().length < 6) {
    ElMessage.warning("密码至少 6 位");
    return;
  }
  const response = await http.post<ApiBody<{ self: boolean }>>(`/api/admin/accounts/${current.value.id}/password`, {
    password: nextPassword.value.trim()
  });
  editing.value = false;
  if (response.data.data.self) {
    session.clear();
    ElMessage.success("密码已修改，请重新登录");
    await router.push("/login");
    return;
  }
  ElMessage.success("密码已修改");
  await loadAccounts();
}

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  stores.value = response.data.data;
  await loadAccounts();
});
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
