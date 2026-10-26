<template>
  <div>
    <h1>会员</h1>
    <p class="lead">手工录入的会员会记成已支付订单。会员用手机号在小程序登录。姓名和手机号可在编辑里修改，人脸不能改。</p>
    <el-form :inline="true" class="toolbar">
      <el-form-item v-if="session.role === 'MASTER'" label="门店">
        <el-select v-model="filter.storeId" clearable placeholder="全部" style="width: 180px" @change="load">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="姓名"><el-input v-model="filter.name" clearable @keyup.enter="load" /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="filter.phone" clearable @keyup.enter="load" /></el-form-item>
      <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      <el-form-item><el-button @click="openCreate">录入会员</el-button></el-form-item>
      <el-form-item><el-button @click="openImport">导入旧会员</el-button></el-form-item>
    </el-form>

    <el-table :data="rows">
      <el-table-column label="姓名" width="120">
        <template #default="{ row }">{{ row.name || "未填" }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="memberNo" label="会员编号" width="120" />
      <el-table-column label="微信" min-width="100">
        <template #default="{ row }">
          <span>{{ row.wechatBound ? "已绑定" : "未绑定" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="storeName" label="录入门店" min-width="140" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openGrant(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createOpen" title="录入会员" width="640px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="门店">
          <el-select v-model="form.storeId" style="width: 240px" :disabled="session.role === 'STORE'" @change="loadProducts">
            <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="人脸">
          <input type="file" accept="image/*" @change="onFile" />
          <img v-if="preview" class="face" :src="preview" alt="" />
        </el-form-item>
        <el-form-item label="会员卡">
          <el-select v-model="form.cardIds" multiple style="width: 100%" placeholder="可多选">
            <el-option v-for="card in cards" :key="card.id" :label="card.name" :value="card.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.packIds" multiple style="width: 100%" placeholder="可多选">
            <el-option v-for="pack in packs" :key="pack.id" :label="String(pack.name)" :value="String(pack.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" @click="create">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importOpen" title="导入旧会员" width="560px">
      <p class="lead">选择一台在线闸机。按闸机上的人员编号导入照片和到期时间，不重新下发人脸。姓名、手机号没有时，导入后在列表里补。</p>
      <el-select v-model="importDeviceId" placeholder="选择闸机" style="width: 100%">
        <el-option
          v-for="gate in gates"
          :key="gate.id"
          :label="`${gate.storeName} · ${gate.name}${gate.online ? '' : '（离线）'}`"
          :value="gate.id"
          :disabled="!gate.online || gate.status !== 'ENABLED'"
        />
      </el-select>
      <template #footer>
        <el-button @click="importOpen = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="importMembers">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantOpen" title="编辑会员" width="640px" destroy-on-close>
      <p class="lead">人脸不能改。姓名和手机号修改后会记入系统操作。</p>
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <el-input v-model="grantName" maxlength="32" style="width: 240px" />
          <el-button style="margin-left: 12px" @click="saveName">保存姓名</el-button>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="grantPhone" maxlength="11" style="width: 240px" />
          <el-button style="margin-left: 12px" @click="savePhone">保存手机号</el-button>
        </el-form-item>
        <el-form-item label="微信">
          <template v-if="grantBound">已绑定微信。</template>
          <template v-else>未绑定微信。</template>
          <div v-if="grantBound" style="margin-top: 8px">
            <el-button @click="unbind">解除微信绑定</el-button>
          </div>
        </el-form-item>
        <el-form-item label="门店">
          <el-select v-model="grant.storeId" style="width: 240px" :disabled="session.role === 'STORE'" @change="loadGrantProducts">
            <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员卡">
          <el-select v-model="grant.cardIds" multiple style="width: 100%">
            <el-option v-for="card in cards" :key="card.id" :label="card.name" :value="card.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="grant.packIds" multiple style="width: 100%">
            <el-option v-for="pack in packs" :key="pack.id" :label="String(pack.name)" :value="String(pack.id)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantOpen = false">取消</el-button>
        <el-button type="primary" @click="saveGrant">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const session = useSessionStore();
const stores = ref<{ id: string; name: string }[]>([]);
const rows = ref<Record<string, any>[]>([]);
const cards = ref<{ id: string; name: string }[]>([]);
const packs = ref<Record<string, unknown>[]>([]);
const filter = reactive({ storeId: "", name: "", phone: "" });
const createOpen = ref(false);
const grantOpen = ref(false);
const preview = ref("");
const form = reactive({ storeId: "", name: "", phone: "", faceBase64: "", cardIds: [] as string[], packIds: [] as string[] });
const grant = reactive({ userId: "", storeId: "", cardIds: [] as string[], packIds: [] as string[] });
const grantName = ref("");
const grantPhone = ref("");
const grantBound = ref(false);
const importOpen = ref(false);
const importing = ref(false);
const importDeviceId = ref("");
const gates = ref<{ id: string; name: string; storeName: string; online: boolean; status: string }[]>([]);

onMounted(async () => {
  const storeRes = await http.get<ApiBody<{ id: string; name: string }[]>>("/api/admin/stores");
  stores.value = storeRes.data.data;
  if (session.role === "STORE" && stores.value[0]) {
    filter.storeId = stores.value[0].id;
  }
  await load();
});

async function load() {
  const response = await http.get<ApiBody<Record<string, any>[]>>("/api/admin/members", {
    params: {
      storeId: filter.storeId || undefined,
      name: filter.name || undefined,
      phone: filter.phone || undefined,
    },
  });
  rows.value = response.data.data;
}

async function loadProducts(storeId: string) {
  cards.value = [];
  packs.value = [];
  if (!storeId) return;
  const [cardRes, packRes] = await Promise.all([
    http.get<ApiBody<{ id: string; name: string }[]>>("/api/admin/cards", { params: { storeId } }),
    http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/packs", { params: { storeId } }),
  ]);
  cards.value = cardRes.data.data;
  packs.value = packRes.data.data;
}

function openCreate() {
  form.storeId = filter.storeId || stores.value[0]?.id || "";
  form.name = "";
  form.phone = "";
  form.faceBase64 = "";
  form.cardIds = [];
  form.packIds = [];
  preview.value = "";
  createOpen.value = true;
  void loadProducts(form.storeId);
}

function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    const text = String(reader.result || "");
    form.faceBase64 = text;
    preview.value = text;
  };
  reader.readAsDataURL(file);
}

async function openImport() {
  const response = await http.get<ApiBody<{ id: string; name: string; storeName: string; online: boolean; status: string }[]>>("/api/admin/gates");
  gates.value = response.data.data;
  importDeviceId.value = gates.value.find((gate) => gate.online && gate.status === "ENABLED")?.id || "";
  importOpen.value = true;
}

async function importMembers() {
  if (!importDeviceId.value) {
    ElMessage.warning("请选择一台在线闸机");
    return;
  }
  importing.value = true;
  try {
    const response = await http.post<ApiBody<{ imported: number; updated: number; skipped: number; notes: string[] }>>(
      "/api/admin/members/import",
      { deviceId: importDeviceId.value },
      { timeout: 600000 },
    );
    const result = response.data.data;
    const extra = result.notes?.length ? ` ${result.notes.join("；")}` : "";
    ElMessage.success(`新增 ${result.imported} 人，补全 ${result.updated} 人，跳过 ${result.skipped} 人。${extra}`);
    importOpen.value = false;
    await load();
  } finally {
    importing.value = false;
  }
}

async function create() {
  await http.post<ApiBody<{ id: string }>>("/api/admin/members", form);
  createOpen.value = false;
  await load();
  ElMessage.success("已录入，会员可用手机号在小程序登录");
}

function openGrant(row: Record<string, any>) {
  grant.userId = row.id;
  grant.storeId = row.storeId || filter.storeId || stores.value[0]?.id || "";
  grant.cardIds = [];
  grant.packIds = [];
  grantName.value = row.name;
  grantPhone.value = row.phone;
  grantBound.value = !!row.wechatBound;
  grantOpen.value = true;
  void loadProducts(grant.storeId);
}

async function loadGrantProducts() {
  await loadProducts(grant.storeId);
}

async function unbind() {
  await ElMessageBox.confirm("解除后，会员需要用新微信在小程序重新登录。", "解除微信绑定");
  await http.post(`/api/admin/members/${grant.userId}/unbind`);
  grantBound.value = false;
  ElMessage.success("已解除微信绑定");
  await load();
}

async function saveName() {
  await http.put(`/api/admin/members/${grant.userId}/name`, { name: grantName.value });
  ElMessage.success("姓名已更新");
  await load();
}

async function savePhone() {
  await http.put(`/api/admin/members/${grant.userId}/phone`, { phone: grantPhone.value });
  ElMessage.success("手机号已更新");
  await load();
}

async function saveGrant() {
  await http.post(`/api/admin/members/${grant.userId}/grants`, grant);
  ElMessage.success("已补开");
  grantOpen.value = false;
}
</script>

<style scoped>
.lead { color: #78716c; margin: 0 0 16px; }
.toolbar { margin-bottom: 12px; }
.face { display: block; width: 96px; height: 96px; object-fit: cover; margin-top: 8px; border-radius: 8px; }
</style>
