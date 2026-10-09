<template>
  <div>
    <h1>{{ title }}</h1>
    <p class="lead">{{ lead }}</p>

    <el-form v-if="route.path === '/equipment'" label-width="90px" class="block-form" @submit.prevent="save">
      <el-form-item label="门店">
        <el-select v-model="form.storeId" placeholder="选择门店" style="width: 180px" @change="loadLists">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="器械编号"><el-input v-model="form.code" style="width: 180px" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="form.name" style="width: 180px" /></el-form-item>
      <el-form-item label="图文"><RichTextField v-model="form.intro" biz="EQUIPMENT" /></el-form-item>
      <el-form-item label="视频"><ImageField v-model="form.videoUrl" biz="EQUIPMENT_VIDEO" /></el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">{{ form.id ? "保存修改" : "保存" }}</el-button>
        <el-button v-if="form.id" @click="clearEquipment">取消编辑</el-button>
      </el-form-item>
    </el-form>

    <el-form v-else-if="route.path === '/groupon'" :inline="true" @submit.prevent="save">
      <el-form-item label="门店">
        <el-select v-model="form.storeId" placeholder="选择门店" style="width: 180px" @change="onGrouponStore">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="平台">
        <el-select v-model="form.platform" placeholder="选择平台" style="width: 140px">
          <el-option label="美团" value="MEITUAN" />
          <el-option label="抖音" value="DOUYIN" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!form.editingId" label="券码"><el-input v-model="form.code" /></el-form-item>
      <el-form-item label="卡种">
        <el-select v-model="form.cardProductId" placeholder="选择卡种" style="width: 220px">
          <el-option v-for="card in cards" :key="card.id" :label="cardLabel(card)" :value="card.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">{{ form.editingId ? "保存修改" : "保存" }}</el-button>
        <el-button v-if="form.editingId" @click="form.editingId = ''">取消编辑</el-button>
      </el-form-item>
    </el-form>

    <el-form v-else-if="route.path === '/coaches'" :inline="true" @submit.prevent="save">
      <el-form-item label="门店">
        <el-select v-model="form.storeId" placeholder="选择门店" style="width: 180px" @change="loadLists">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="已有教练">
        <el-select v-model="form.coachId" clearable placeholder="不选则新建" style="width: 180px">
          <el-option v-for="coach in coaches" :key="coach.id" :label="coach.name" :value="coach.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="教练"><el-input v-model="form.name" placeholder="新建时填写" /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="介绍"><el-input v-model="form.intro" /></el-form-item>
      <el-form-item label="课程名"><el-input v-model="form.packName" /></el-form-item>
      <el-form-item label="价格分"><el-input v-model="form.priceFen" /></el-form-item>
      <el-form-item label="课时"><el-input v-model="form.lessonCount" /></el-form-item>
      <el-form-item label="课程内容"><el-input v-model="form.content" /></el-form-item>
      <el-form-item label="适合人群"><el-input v-model="form.audience" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">保存</el-button></el-form-item>
    </el-form>

    <el-form v-else-if="route.path === '/franchise'" label-width="90px" class="block-form" @submit.prevent="save">
      <el-form-item label="介绍"><RichTextField v-model="form.intro" /></el-form-item>
      <el-form-item label="热线"><el-input v-model="form.hotline" style="width: 220px" /></el-form-item>
      <el-form-item label="卖点"><RichTextField v-model="form.points" /></el-form-item>
      <el-form-item label="总部支持"><RichTextField v-model="form.support" /></el-form-item>
      <el-form-item label="流程"><RichTextField v-model="form.steps" /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">保存页面</el-button></el-form-item>
    </el-form>

    <el-form v-else :inline="true" @submit.prevent="save">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <el-input v-model="form[field.key]" />
      </el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">保存</el-button></el-form-item>
    </el-form>

    <el-table v-if="route.path === '/equipment'" :data="rows">
      <el-table-column prop="store_name" label="门店" />
      <el-table-column prop="code" label="器械编号" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="图文">
        <template #default="{ row }">
          <button type="button" class="preview" @click="preview?.show({ title: String(row.name || '图文'), html: String(row.intro || '') })">
            {{ plainIntro(String(row.intro || "")) || "查看图文" }}
          </button>
        </template>
      </el-table-column>
      <el-table-column label="视频" width="100">
        <template #default="{ row }">
          <button v-if="row.video_url" type="button" class="preview" @click="preview?.show({ title: String(row.name || '视频'), video: String(row.video_url) })">预览</button>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="editEquipment(row)">编辑</el-button>
          <el-button link type="danger" @click="removeEquipment(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-table v-else-if="route.path === '/groupon'" :data="rows">
      <el-table-column label="门店">
        <template #default="{ row }">{{ storeName(String(row.store_id)) }}</template>
      </el-table-column>
      <el-table-column label="平台" width="100">
        <template #default="{ row }">{{ row.platform === "DOUYIN" ? "抖音" : "美团" }}</template>
      </el-table-column>
      <el-table-column label="卡种">
        <template #default="{ row }">{{ cardName(String(row.card_product_id)) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="editGroupon(row)">编辑</el-button>
          <el-button link type="danger" @click="removeGroupon(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-table v-else-if="route.path === '/coaches'" :data="rows">
      <el-table-column prop="store_name" label="门店" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column label="介绍">
        <template #default="{ row }">
          <button type="button" class="preview" @click="preview?.show({ title: String(row.name || '介绍'), html: String(row.intro || '') })">
            {{ plainIntro(String(row.intro || "")) || "查看介绍" }}
          </button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="editCoach(row)">编辑</el-button>
          <el-button link type="danger" @click="removeCoach(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-table v-else-if="route.path === '/franchise'" :data="rows">
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="phone" label="电话" />
      <el-table-column prop="budget" label="预算" />
      <el-table-column prop="province" label="省份" />
      <el-table-column prop="city" label="城市" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="editLead(row)">编辑</el-button>
          <el-button link type="danger" @click="removeLead(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-table v-else :data="rows">
      <el-table-column v-for="column in columns" :key="column" :prop="column" :label="columnLabels[column] || column" />
    </el-table>
    <el-dialog v-model="coachOpen" title="编辑教练" width="640px">
      <el-form label-width="70px">
        <el-form-item label="姓名"><el-input v-model="coachDraft.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="coachDraft.phone" /></el-form-item>
        <el-form-item label="介绍"><RichTextField v-model="coachDraft.intro" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="coachOpen = false">取消</el-button>
        <el-button type="primary" @click="saveCoach">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="leadOpen" title="编辑加盟申请" width="520px">
      <el-form label-width="70px">
        <el-form-item label="姓名"><el-input v-model="leadDraft.name" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="leadDraft.phone" /></el-form-item>
        <el-form-item label="预算"><el-input v-model="leadDraft.budget" /></el-form-item>
        <el-form-item label="省份"><el-input v-model="leadDraft.province" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="leadDraft.city" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leadOpen = false">取消</el-button>
        <el-button type="primary" @click="saveLead">保存</el-button>
      </template>
    </el-dialog>
    <ContentPreview ref="preview" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import ContentPreview from "../components/ContentPreview.vue";
import ImageField from "../components/ImageField.vue";
import RichTextField from "../components/RichTextField.vue";
import { http, type ApiBody } from "../api/http";

interface StoreRow {
  id: string;
  name: string;
}

interface CardRow {
  id: string;
  name: string;
  priceFen: number;
}

interface CoachRow {
  id: string;
  name: string;
  phone?: string;
  intro?: string;
}

const route = useRoute();
const rows = ref<Record<string, unknown>[]>([]);
const form = reactive<Record<string, string>>({});
const stores = ref<StoreRow[]>([]);
const cards = ref<CardRow[]>([]);
const coaches = ref<CoachRow[]>([]);
const preview = ref<InstanceType<typeof ContentPreview>>();
const silentStore = ref(false);
const coachOpen = ref(false);
const leadOpen = ref(false);
const coachDraft = reactive({ id: "", name: "", phone: "", intro: "" });
const leadDraft = reactive({ id: "", name: "", phone: "", budget: "", province: "", city: "" });

const title = computed(() => ({
  "/equipment": "器械教程",
  "/groupon": "团购券",
  "/coaches": "教练与课程",
  "/franchise": "加盟",
  "/messages": "站内消息",
  "/violations": "违规处理"
}[route.path] || "管理"));

const lead = computed(() => ({
  "/equipment": "先选择门店。图文里可以直接写字，也可以插入图片。视频可以上传本地文件，也可以粘贴链接，不超过 200MB。",
  "/groupon": "先选择门店和对应卡种。券码只存哈希。用户在小程序提交后，匹配到规则就按这张卡开通会员。美团和抖音平台接口还没接。",
  "/coaches": "先选择门店。不选已有教练时会按姓名和手机号新建。买课增加课时，不会开通进店会员。",
  "/franchise": "页面文案由总账号配置。申请记录只在这里查看。",
  "/messages": "只做站内已读，没有运营推送规则。",
  "/violations": "扣天数会改会员结束时间，并写入调整记录。勾选停用则立即结束会员。"
}[route.path] || ""));

const fields = computed(() => {
  if (route.path === "/messages") return [
    { key: "userId", label: "用户编号" }, { key: "title", label: "标题" }, { key: "content", label: "内容" }
  ];
  return [
    { key: "membershipId", label: "会员记录编号" }, { key: "deltaDays", label: "扣减天数" },
    { key: "revoke", label: "停用填1" }, { key: "reason", label: "原因" }
  ];
});

const columnLabels: Record<string, string> = {
  store_id: "门店编号",
  code: "编号",
  name: "名称",
  intro: "说明",
  image_url: "图文",
  video_url: "视频",
  platform: "平台",
  card_product_id: "卡种编号",
  created_at: "创建时间"
};

const columns = computed(() => rows.value[0] ? Object.keys(rows.value[0]).filter((key) => key !== "id") : []);

watch(() => route.path, async () => {
  for (const key of Object.keys(form)) {
    delete form[key];
  }
  rows.value = [];
  cards.value = [];
  coaches.value = [];
  await load();
});
onMounted(load);

function plainIntro(html: string) {
  return html.replace(/<[^>]+>/g, " ").replace(/&nbsp;/g, " ").replace(/\s+/g, " ").trim();
}

function cardLabel(card: CardRow) {
  return `${card.name} ${card.priceFen / 100} 元`;
}

function storeName(id: string) {
  return stores.value.find((store) => store.id === id)?.name || id;
}

function cardName(id: string) {
  return cards.value.find((card) => card.id === id)?.name || id;
}

function clearEquipment() {
  form.id = "";
  form.code = "";
  form.name = "";
  form.intro = "";
  form.imageUrl = "";
  form.videoUrl = "";
}

function editEquipment(row: Record<string, unknown>) {
  form.id = String(row.id);
  form.code = String(row.code || "");
  form.name = String(row.name || "");
  form.intro = String(row.intro || "");
  form.imageUrl = String(row.image_url || "");
  form.videoUrl = String(row.video_url || "");
}

async function removeEquipment(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm(`删除器械「${row.name}」。`, "删除器械", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/equipment/${row.id}`);
  if (form.id === String(row.id)) clearEquipment();
  ElMessage.success("已删除");
  await loadLists();
}

async function onGrouponStore() {
  if (silentStore.value) return;
  form.cardProductId = "";
  form.editingId = "";
  await loadLists();
}

async function editGroupon(row: Record<string, unknown>) {
  silentStore.value = true;
  form.storeId = String(row.store_id);
  form.platform = String(row.platform);
  form.editingId = String(row.id);
  await loadCards();
  form.cardProductId = String(row.card_product_id);
  silentStore.value = false;
}

async function removeGroupon(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm("删除这条团购规则。已核销的券不能删。", "删除团购券", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/groupon-rules/${row.id}`);
  if (form.editingId === String(row.id)) form.editingId = "";
  ElMessage.success("已删除");
  await loadLists();
}

function editCoach(row: Record<string, unknown>) {
  coachDraft.id = String(row.id);
  coachDraft.name = String(row.name || "");
  coachDraft.phone = String(row.phone || "");
  coachDraft.intro = String(row.intro || "");
  coachOpen.value = true;
}

async function saveCoach() {
  await http.put(`/api/admin/coaches/${coachDraft.id}`, coachDraft);
  coachOpen.value = false;
  ElMessage.success("已保存");
  await loadLists();
}

async function removeCoach(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm(`删除教练「${row.name}」。已有学员课时时不能删。`, "删除教练", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/coaches/${row.id}`);
  ElMessage.success("已删除");
  await loadLists();
}

function editLead(row: Record<string, unknown>) {
  leadDraft.id = String(row.id);
  leadDraft.name = String(row.name || "");
  leadDraft.phone = String(row.phone || "");
  leadDraft.budget = String(row.budget || "");
  leadDraft.province = String(row.province || "");
  leadDraft.city = String(row.city || "");
  leadOpen.value = true;
}

async function saveLead() {
  await http.put(`/api/admin/franchise/leads/${leadDraft.id}`, leadDraft);
  leadOpen.value = false;
  ElMessage.success("已保存");
  await load();
}

async function removeLead(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm(`删除「${row.name}」的加盟申请。`, "删除申请", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/franchise/leads/${row.id}`);
  ElMessage.success("已删除");
  await load();
}

async function load() {
  if (stores.value.length === 0) {
    const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
    stores.value = response.data.data;
  }
  if (!form.storeId && stores.value[0] && ["/equipment", "/groupon", "/coaches"].includes(route.path)) {
    form.storeId = stores.value[0].id;
  }
  if (route.path === "/franchise") {
    const page = await http.get<ApiBody<Record<string, string>>>("/api/admin/franchise");
    Object.assign(form, page.data.data);
    const leads = await http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/franchise/leads");
    rows.value = leads.data.data;
    return;
  }
  await loadLists();
}

async function loadLists() {
  if (route.path === "/equipment") {
    if (!form.storeId) {
      rows.value = [];
      return;
    }
    const response = await http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/equipment", { params: { storeId: form.storeId } });
    rows.value = response.data.data;
    return;
  }
  if (route.path === "/groupon") {
    const response = await http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/groupon-rules", {
      params: form.storeId ? { storeId: form.storeId } : {}
    });
    rows.value = response.data.data;
    await loadCards();
    return;
  }
  if (route.path === "/coaches") {
    coaches.value = [];
    form.coachId = "";
    if (!form.storeId) {
      rows.value = [];
      return;
    }
    const response = await http.get<ApiBody<CoachRow[]>>("/api/admin/coaches", { params: { storeId: form.storeId } });
    coaches.value = response.data.data;
    rows.value = response.data.data as unknown as Record<string, unknown>[];
  }
}

async function loadCards() {
  cards.value = [];
  if (!form.storeId) return;
  const cardRes = await http.get<ApiBody<CardRow[]>>("/api/admin/cards", { params: { storeId: form.storeId } });
  cards.value = cardRes.data.data;
}

async function save() {
  if (route.path === "/equipment") {
    await http.post("/api/admin/equipment", form);
    clearEquipment();
  } else if (route.path === "/groupon") {
    if (form.editingId) {
      await http.put(`/api/admin/groupon-rules/${form.editingId}`, {
        storeId: form.storeId, platform: form.platform, cardProductId: form.cardProductId
      });
      form.editingId = "";
    } else {
      await http.post("/api/admin/groupon-rules", form);
      form.code = "";
    }
  } else if (route.path === "/coaches") {
    if (!form.coachId) {
      const created = await http.post<ApiBody<{ id: string }>>("/api/admin/coaches", form);
      form.coachId = created.data.data.id;
    }
    if (form.packName) {
      await http.post("/api/admin/packs", {
        coachId: form.coachId, name: form.packName, priceFen: Number(form.priceFen),
        lessonCount: Number(form.lessonCount), content: form.content, audience: form.audience
      });
    }
    form.name = "";
    form.phone = "";
    form.intro = "";
    form.packName = "";
    form.priceFen = "";
    form.lessonCount = "";
    form.content = "";
    form.audience = "";
  } else if (route.path === "/franchise") {
    await http.post("/api/admin/franchise", form);
  } else if (route.path === "/messages") {
    await http.post("/api/admin/messages", form);
  } else {
    await http.post("/api/admin/violations", {
      membershipId: form.membershipId,
      deltaDays: -Math.abs(Number(form.deltaDays || 0)),
      revoke: form.revoke === "1",
      reason: form.reason
    });
  }
  ElMessage.success("已保存");
  if (route.path === "/franchise") {
    await load();
  } else {
    await loadLists();
  }
}
</script>

<style scoped>
.preview { padding: 0; border: 0; background: none; color: var(--el-color-primary); cursor: pointer; text-align: left; }
</style>
