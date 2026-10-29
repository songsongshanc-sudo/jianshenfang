<template>
  <div>
    <h1>账号管理</h1>
    <p class="lead">这里改总账号密码，并维护可以刷脸进门的管理员。左侧菜单里的账号管理仍是门店登录账号。</p>

    <h2>总账号密码</h2>
    <el-form :inline="true" @submit.prevent="changePassword">
      <el-form-item label="新密码">
        <el-input v-model="password" type="password" show-password placeholder="至少 6 位" style="width: 220px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="savingPassword" @click="changePassword">保存密码</el-button>
      </el-form-item>
    </el-form>
    <p class="hint">保存后当前登录会退出，需要用新密码重新登录。</p>

    <h2>进门管理员</h2>
    <el-button type="primary" style="margin-bottom: 12px" @click="open()">添加管理员</el-button>
    <el-table :data="rows">
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="storeName" label="门店" />
      <el-table-column prop="gateUntil" label="闸机到期" min-width="170" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="openDialog" :title="form.id ? '编辑管理员' : '添加管理员'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item v-if="!form.id" label="门店">
          <el-select v-model="form.storeId" style="width: 240px">
            <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" placeholder="11 位手机号" /></el-form-item>
        <el-form-item label="人脸">
          <input type="file" accept="image/*" @change="onFace" />
          <span class="hint">{{ form.id ? "不重新选择则保持原人脸。" : "请上传正脸照片。" }}</span>
        </el-form-item>
        <el-form-item label="闸机到期">
          <el-date-picker v-model="form.gateUntil" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="到期后不能进门" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
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

interface StoreRow { id: string; name: string }
interface AdminRow {
  id: string;
  name: string;
  phone: string;
  storeId: string;
  storeName: string;
  gateUntil: string;
}

const router = useRouter();
const session = useSessionStore();
const stores = ref<StoreRow[]>([]);
const rows = ref<AdminRow[]>([]);
const password = ref("");
const savingPassword = ref(false);
const openDialog = ref(false);
const saving = ref(false);
const form = reactive({
  id: "",
  storeId: "",
  name: "",
  phone: "",
  faceBase64: "",
  gateUntil: ""
});

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  stores.value = response.data.data;
  form.storeId = stores.value[0]?.id || "";
  await reload();
});

async function reload() {
  const response = await http.get<ApiBody<AdminRow[]>>("/api/admin/entry-admins");
  rows.value = response.data.data;
}

function open(row?: AdminRow) {
  form.id = row?.id || "";
  form.storeId = row?.storeId || stores.value[0]?.id || "";
  form.name = row?.name || "";
  form.phone = row?.phone || "";
  form.faceBase64 = "";
  form.gateUntil = row?.gateUntil || "";
  openDialog.value = true;
}

function onFace(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    form.faceBase64 = "";
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    form.faceBase64 = typeof reader.result === "string" ? reader.result : "";
  };
  reader.readAsDataURL(file);
}

async function changePassword() {
  if (password.value.trim().length < 6) {
    ElMessage.warning("密码至少 6 位");
    return;
  }
  savingPassword.value = true;
  try {
    await http.post("/api/admin/auth/password", { password: password.value.trim() });
    ElMessage.success("密码已更新，请重新登录");
    session.clear();
    router.push("/login");
  } finally {
    savingPassword.value = false;
  }
}

async function save() {
  if (!form.name.trim() || !form.phone.trim() || !form.gateUntil) {
    ElMessage.warning("请填写姓名、手机号和闸机到期时间");
    return;
  }
  if (!form.id && !form.faceBase64) {
    ElMessage.warning("请上传人脸照片");
    return;
  }
  saving.value = true;
  try {
    if (form.id) {
      await http.put(`/api/admin/entry-admins/${form.id}`, {
        name: form.name,
        phone: form.phone,
        faceBase64: form.faceBase64,
        gateUntil: form.gateUntil
      });
    } else {
      await http.post("/api/admin/entry-admins", {
        storeId: form.storeId,
        name: form.name,
        phone: form.phone,
        faceBase64: form.faceBase64,
        gateUntil: form.gateUntil
      });
    }
    ElMessage.success("已保存");
    openDialog.value = false;
    await reload();
  } finally {
    saving.value = false;
  }
}

async function remove(row: AdminRow) {
  try {
    await ElMessageBox.confirm(`删除管理员「${row.name}」后，闸机将不再放行。`, "删除管理员", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  await http.delete(`/api/admin/entry-admins/${row.id}`);
  ElMessage.success("已删除");
  await reload();
}
</script>

<style scoped>
h2 {
  margin: 22px 0 12px;
  font-size: 16px;
}
.hint {
  color: #78716c;
  font-size: 13px;
}
</style>
