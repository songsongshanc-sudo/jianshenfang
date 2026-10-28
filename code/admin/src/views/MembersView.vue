<template>
  <div>
    <h1>会员</h1>
    <p class="lead">手工录入的会员会记成已支付订单。会员用手机号在小程序登录。姓名和手机号可在编辑里修改，人脸不能改。停用后资料保留，闸机不再识别，可以恢复。</p>
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
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="memberTag(row)">{{ memberLabel(row) }}</el-tag>
        </template>
      </el-table-column>
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
      <p class="lead">人脸不能改。姓名、手机号、微信、会员卡、课程和停用状态都先改在这里，点击保存并在弹框里确认后才生效。</p>
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <el-input v-model="grantName" maxlength="32" style="width: 240px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="grantPhone" maxlength="11" style="width: 240px" />
        </el-form-item>
        <el-form-item label="微信">
          <template v-if="grantBound">已绑定微信。</template>
          <template v-else-if="baselineBound">保存后解除绑定。</template>
          <template v-else>未绑定微信。</template>
          <div v-if="baselineBound" style="margin-top: 8px">
            <el-button v-if="grantBound" @click="grantBound = false">解除微信绑定</el-button>
            <el-button v-else @click="grantBound = true">撤销解除</el-button>
          </div>
        </el-form-item>
        <el-form-item label="门店">
          <el-select v-model="grant.storeId" style="width: 240px" :disabled="session.role === 'STORE'" @change="loadGrantProducts">
            <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员卡">
          <div class="grant-box">
            <div v-if="showCardRemain" class="remain">剩余 {{ previewRemain }} 天</div>
            <el-tag v-for="c in currentCards" :key="c.id" closable class="grant-tag" @close="dropCard(c)">{{ cardLabel(c) }}</el-tag>
            <el-tag v-for="id in grant.cardIds" :key="id" type="success" closable class="grant-tag" @close="dropNewCard(id)">新增 {{ cardName(id) }}</el-tag>
            <el-button v-if="!addingCard" link type="primary" @click="addingCard = true">添加会员卡</el-button>
            <el-select v-else v-model="grant.cardIds" multiple placeholder="选择要新增的会员卡" style="width: 100%">
              <el-option v-for="card in cards" :key="card.id" :label="`${card.name}（${card.validDays}天）`" :value="card.id" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="课程">
          <div class="grant-box">
            <el-tag v-for="p in currentPacks" :key="p.id" closable class="grant-tag" @close="dropPack(p)">{{ packLabel(p) }}</el-tag>
            <el-tag v-for="id in grant.packIds" :key="id" type="success" closable class="grant-tag" @close="dropNewPack(id)">新增 {{ packName(id) }}</el-tag>
            <el-button v-if="!addingPack" link type="primary" @click="addingPack = true">添加课程</el-button>
            <el-select v-else v-model="grant.packIds" multiple placeholder="选择要新增的课程" style="width: 100%">
              <el-option v-for="pack in packs" :key="pack.id" :label="String(pack.name)" :value="String(pack.id)" />
            </el-select>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <div>
            <el-button v-if="grantStatus === 'DISABLED'" type="primary" @click="grantStatus = 'ACTIVE'">恢复会员</el-button>
            <el-button v-else type="warning" @click="grantStatus = 'DISABLED'">停用会员</el-button>
            <div v-if="grantStatus !== baselineStatus" class="pending">保存并确认后生效</div>
          </div>
          <div>
            <el-button @click="grantOpen = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveGrant">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const session = useSessionStore();
const stores = ref<{ id: string; name: string }[]>([]);
const rows = ref<Record<string, any>[]>([]);
const cards = ref<{ id: string; name: string; validDays: number }[]>([]);
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
const grantStatus = ref("ACTIVE");
type CardRow = { id: string; name: string; storeName: string; days: number };
type PackRow = { id: string; name: string; remaining: number; storeName: string };
const baselineName = ref("");
const baselinePhone = ref("");
const baselineBound = ref(false);
const baselineStatus = ref("ACTIVE");
const baselineRemain = ref(0);
const baselineCards = ref<CardRow[]>([]);
const baselinePacks = ref<PackRow[]>([]);
const currentCards = ref<CardRow[]>([]);
const currentPacks = ref<PackRow[]>([]);
const saving = ref(false);
const addingCard = ref(false);
const addingPack = ref(false);
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

function memberLabel(row: Record<string, any>) {
  if (row.status === "DISABLED") return "停用";
  if (row.registerStatus && row.registerStatus !== "ACTIVE") return "未完成";
  return "在线";
}

function memberTag(row: Record<string, any>) {
  if (row.status === "DISABLED") return "info";
  if (row.registerStatus && row.registerStatus !== "ACTIVE") return "warning";
  return "success";
}

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
    http.get<ApiBody<{ id: string; name: string; validDays: number }[]>>("/api/admin/cards", { params: { storeId } }),
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

const removedCards = computed(() => baselineCards.value.filter((card) => !currentCards.value.some((item) => item.id === card.id)));
const removedPacks = computed(() => baselinePacks.value.filter((pack) => !currentPacks.value.some((item) => item.id === pack.id)));
const previewRemain = computed(() => {
  const removedDays = removedCards.value.reduce((sum, card) => sum + (card.days || 0), 0);
  const addedDays = grant.cardIds.reduce((sum, id) => sum + (cards.value.find((card) => card.id === id)?.validDays || 0), 0);
  return Math.max(0, baselineRemain.value - removedDays + addedDays);
});
const showCardRemain = computed(() => currentCards.value.length > 0 || grant.cardIds.length > 0 || removedCards.value.length > 0);

function cardLabel(card: CardRow) {
  return session.role === "MASTER" ? `${card.name} · ${card.storeName}` : card.name;
}

function packLabel(pack: PackRow) {
  const name = session.role === "MASTER" ? `${pack.name} · ${pack.storeName}` : pack.name;
  return `${name}（剩 ${pack.remaining} 节）`;
}

function cardName(id: string) {
  const card = cards.value.find((item) => item.id === id);
  return card ? `${card.name}（${card.validDays}天）` : id;
}

function packName(id: string) {
  const pack = packs.value.find((item) => String(item.id) === id);
  return pack ? String(pack.name) : id;
}

function dropCard(card: CardRow) {
  currentCards.value = currentCards.value.filter((item) => item.id !== card.id);
}

function dropNewCard(id: string) {
  grant.cardIds = grant.cardIds.filter((item) => item !== id);
}

function dropPack(pack: PackRow) {
  currentPacks.value = currentPacks.value.filter((item) => item.id !== pack.id);
}

function dropNewPack(id: string) {
  grant.packIds = grant.packIds.filter((item) => item !== id);
}

function escapeHtml(value: string) {
  return value.replace(/[&<>]/g, (char) => (char === "&" ? "&amp;" : char === "<" ? "&lt;" : "&gt;"));
}

function openGrant(row: Record<string, any>) {
  grant.userId = row.id;
  grant.storeId = row.storeId || filter.storeId || stores.value[0]?.id || "";
  grant.cardIds = [];
  grant.packIds = [];
  baselineName.value = row.name || "";
  baselinePhone.value = row.phone || "";
  baselineBound.value = !!row.wechatBound;
  baselineStatus.value = row.status === "DISABLED" ? "DISABLED" : "ACTIVE";
  grantName.value = baselineName.value;
  grantPhone.value = baselinePhone.value;
  grantBound.value = baselineBound.value;
  grantStatus.value = baselineStatus.value;
  addingCard.value = false;
  addingPack.value = false;
  baselineRemain.value = 0;
  baselineCards.value = [];
  baselinePacks.value = [];
  currentCards.value = [];
  currentPacks.value = [];
  grantOpen.value = true;
  void loadProducts(grant.storeId);
  void loadGrants(row.id);
}

async function loadGrants(userId: string) {
  const response = await http.get<ApiBody<{
    remainDays: number;
    cards: CardRow[];
    packs: PackRow[];
  }>>(`/api/admin/members/${userId}/grants`);
  const data = response.data.data;
  baselineRemain.value = data.remainDays || 0;
  baselineCards.value = data.cards.map((card) => ({ ...card }));
  currentCards.value = data.cards.map((card) => ({ ...card }));
  baselinePacks.value = data.packs.map((pack) => ({ ...pack }));
  currentPacks.value = data.packs.map((pack) => ({ ...pack }));
}

async function loadGrantProducts() {
  grant.cardIds = [];
  grant.packIds = [];
  addingCard.value = false;
  addingPack.value = false;
  await loadProducts(grant.storeId);
}

async function saveGrant() {
  const name = String(grantName.value ?? "").trim();
  const phone = String(grantPhone.value ?? "").replace(/\D/g, "");
  const basePhone = String(baselinePhone.value ?? "").replace(/\D/g, "");
  if (name !== String(baselineName.value ?? "").trim() && (!name || name.length > 32)) {
    ElMessage.warning("请填写姓名");
    return;
  }
  if (phone !== basePhone && !/^1\d{10}$/.test(phone)) {
    ElMessage.warning("请填写 11 位手机号");
    return;
  }
  if ((grant.cardIds.length || grant.packIds.length) && !grant.storeId) {
    ElMessage.warning("请选择门店");
    return;
  }
  const lines: string[] = [];
  if (name !== String(baselineName.value ?? "").trim()) {
    lines.push(`姓名：${baselineName.value || "未填"} → ${name}`);
  }
  if (phone !== basePhone) {
    lines.push(`手机号：${baselinePhone.value || "未填"} → ${phone}`);
  }
  if (baselineBound.value && !grantBound.value) {
    lines.push("解除微信绑定");
  }
  if (removedCards.value.length) {
    lines.push(`删除会员卡：${removedCards.value.map((card) => `${card.name}（${card.days}天）`).join("、")}`);
  }
  if (grant.cardIds.length) {
    lines.push(`新增会员卡：${grant.cardIds.map(cardName).join("、")}`);
  }
  if (removedPacks.value.length) {
    lines.push(`删除课程：${removedPacks.value.map((pack) => `${pack.name}（剩余 ${pack.remaining} 节）`).join("、")}`);
  }
  if (grant.packIds.length) {
    lines.push(`新增课程：${grant.packIds.map(packName).join("、")}`);
  }
  if (grantStatus.value !== baselineStatus.value) {
    lines.push(grantStatus.value === "DISABLED" ? "停用会员，闸机不再识别" : "恢复会员，按现有会员卡重新下发到闸机");
  }
  if (!lines.length) {
    grantOpen.value = false;
    return;
  }
  const touchesGate = removedCards.value.length > 0 || grant.cardIds.length > 0 || grantStatus.value !== baselineStatus.value;
  lines.push(touchesGate ? "确认后才会写入。闸机不在线时，这次修改不会生效。" : "确认后才会写入。");
  try {
    await ElMessageBox.confirm(lines.map(escapeHtml).join("<br>"), "确认保存", {
      type: "warning",
      dangerouslyUseHTMLString: true,
      confirmButtonText: "确认生效",
      cancelButtonText: "返回修改",
    });
  } catch {
    return;
  }
  saving.value = true;
  try {
    await http.post(`/api/admin/members/${grant.userId}/edits`, {
      name,
      phone,
      unbindWechat: baselineBound.value && !grantBound.value,
      storeId: grant.storeId || null,
      addCardIds: [...grant.cardIds],
      removeCardIds: removedCards.value.map((card) => card.id),
      addPackIds: [...grant.packIds],
      removePackIds: removedPacks.value.map((pack) => pack.id),
      status: grantStatus.value !== baselineStatus.value ? grantStatus.value : null,
    });
    ElMessage.success("已保存");
    grantOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

</script>

<style scoped>
.lead { color: #78716c; margin: 0 0 16px; }
.toolbar { margin-bottom: 12px; }
.face { display: block; width: 96px; height: 96px; object-fit: cover; margin-top: 8px; border-radius: 8px; }
.grant-box { width: 100%; }
.remain { margin-bottom: 8px; }
.grant-tag { margin-right: 8px; margin-bottom: 4px; }
.dialog-footer { display: flex; justify-content: space-between; width: 100%; }
.pending { margin-top: 6px; color: #b45309; font-size: 12px; }
</style>
