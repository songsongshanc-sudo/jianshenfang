<template>
  <div>
    <h1>教练与课程</h1>
    <p class="lead">教练和课程分开维护。先建教练，再给教练加课程。本店账号只能管自己门店。</p>

    <el-form :inline="true" class="toolbar">
      <el-form-item label="门店">
        <el-select v-model="storeId" placeholder="选择门店" style="width: 220px" :disabled="session.role === 'STORE'" @change="reload">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-tabs v-model="tab">
      <el-tab-pane label="教练" name="coach">
        <el-button type="primary" style="margin-bottom: 12px" @click="openCoach()">添加教练</el-button>
        <el-table :data="coaches">
          <el-table-column label="照片" width="80">
            <template #default="{ row }">
              <img v-if="row.avatar_url || row.photo_url" class="thumb" :src="String(row.avatar_url || row.photo_url)" alt="" />
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="phone" label="手机号" width="140" />
          <el-table-column prop="specialty" label="擅长" />
          <el-table-column label="评分" width="80">
            <template #default="{ row }">{{ row.rating ?? 5 }}</template>
          </el-table-column>
          <el-table-column label="累计课时" width="100">
            <template #default="{ row }">{{ row.lesson_taught ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="课程数" width="90">
            <template #default="{ row }">{{ row.pack_count ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCoach(row)">编辑</el-button>
              <el-button link type="danger" @click="removeCoach(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="课程" name="pack">
        <el-button type="primary" style="margin-bottom: 12px" :disabled="!coaches.length" @click="openPack()">添加课程</el-button>
        <el-table :data="packs">
          <el-table-column label="封面" width="80">
            <template #default="{ row }">
              <img v-if="row.cover_url" class="thumb" :src="String(row.cover_url)" alt="" />
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="课程名" />
          <el-table-column prop="coach_name" label="教练" width="120" />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">{{ Number(row.price_fen) / 100 }} 元</template>
          </el-table-column>
          <el-table-column prop="lesson_count" label="课时" width="80" />
          <el-table-column label="单节时长" width="100">
            <template #default="{ row }">{{ row.minutes_per_lesson || 60 }} 分钟</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPack(row)">编辑</el-button>
              <el-button link type="danger" @click="removePack(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="coachOpen" :title="coachForm.id ? '编辑教练' : '添加教练'" width="720px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="姓名"><el-input v-model="coachForm.name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="coachForm.phone" placeholder="需与小程序登录手机号一致" /></el-form-item>
        <el-form-item label="擅长"><el-input v-model="coachForm.specialty" placeholder="如：增肌塑形减脂" /></el-form-item>
        <el-form-item label="评分"><el-input-number v-model="coachForm.rating" :min="0" :max="5" :step="0.1" /></el-form-item>
        <el-form-item label="累计课时"><el-input-number v-model="coachForm.lessonTaught" :min="0" :step="1" /></el-form-item>
        <el-form-item label="头像"><ImageField v-model="coachForm.avatarUrl" biz="COACH" /></el-form-item>
        <el-form-item label="详情背景图"><ImageField v-model="coachForm.photoUrl" biz="COACH" /></el-form-item>
        <el-form-item label="证书照片">
          <div class="certs">
            <div v-for="(url, index) in coachForm.certs" :key="`${url}-${index}`" class="cert">
              <img :src="url" alt="" />
              <el-button link type="danger" @click="coachForm.certs.splice(index, 1)">删除</el-button>
            </div>
            <ImageField v-model="certDraft" biz="CERT" placeholder="上传后自动加入列表" />
          </div>
        </el-form-item>
        <el-form-item label="介绍"><RichTextField v-model="coachForm.intro" biz="RICH" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="coachOpen = false">取消</el-button>
        <el-button type="primary" @click="saveCoach">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="packOpen" :title="packForm.id ? '编辑课程' : '添加课程'" width="720px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="教练">
          <el-select v-model="packForm.coachId" style="width: 240px">
            <el-option v-for="coach in coaches" :key="coach.id" :label="coach.name" :value="coach.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程名"><el-input v-model="packForm.name" /></el-form-item>
        <el-form-item label="价格（分）"><el-input-number v-model="packForm.priceFen" :min="0" :step="100" /></el-form-item>
        <el-form-item label="课时数"><el-input-number v-model="packForm.lessonCount" :min="1" :step="1" /></el-form-item>
        <el-form-item label="单节分钟"><el-input-number v-model="packForm.minutesPerLesson" :min="1" :step="5" /></el-form-item>
        <el-form-item label="封面"><ImageField v-model="packForm.coverUrl" biz="PACK" /></el-form-item>
        <el-form-item v-if="packForm.id" label="状态">
          <el-select v-model="packForm.status" style="width: 160px">
            <el-option label="上架" value="ON" />
            <el-option label="下架" value="OFF" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程内容"><el-input v-model="packForm.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="适合人群"><el-input v-model="packForm.audience" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packOpen = false">取消</el-button>
        <el-button type="primary" @click="savePack">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http, type ApiBody } from "../api/http";
import ImageField from "../components/ImageField.vue";
import RichTextField from "../components/RichTextField.vue";
import { useSessionStore } from "../stores/session";

interface StoreRow { id: string; name: string }
interface CoachRow {
  id: string;
  name: string;
  phone?: string;
  intro?: string;
  avatar_url?: string;
  photo_url?: string;
  specialty?: string;
  rating?: number;
  lesson_taught?: number;
  certificate_urls?: string;
  pack_count?: number;
}

const session = useSessionStore();
const stores = ref<StoreRow[]>([]);
const storeId = ref("");
const tab = ref("coach");
const coaches = ref<CoachRow[]>([]);
const packs = ref<Record<string, unknown>[]>([]);
const coachOpen = ref(false);
const packOpen = ref(false);
const certDraft = ref("");
const coachForm = reactive({
  id: "",
  name: "",
  phone: "",
  intro: "",
  avatarUrl: "",
  photoUrl: "",
  specialty: "",
  rating: 5,
  lessonTaught: 0,
  certs: [] as string[]
});
const packForm = reactive({
  id: "",
  coachId: "",
  name: "",
  priceFen: 0,
  lessonCount: 1,
  minutesPerLesson: 60,
  coverUrl: "",
  content: "",
  audience: "",
  status: "ON"
});

watch(certDraft, (value) => {
  if (!value) return;
  if (!coachForm.certs.includes(value)) {
    coachForm.certs.push(value);
  }
  certDraft.value = "";
});

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  stores.value = response.data.data;
  storeId.value = stores.value[0]?.id || "";
  await reload();
});

async function reload() {
  if (!storeId.value) {
    coaches.value = [];
    packs.value = [];
    return;
  }
  const [coachRes, packRes] = await Promise.all([
    http.get<ApiBody<CoachRow[]>>("/api/admin/coaches", { params: { storeId: storeId.value } }),
    http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/packs", { params: { storeId: storeId.value } })
  ]);
  coaches.value = coachRes.data.data;
  packs.value = packRes.data.data;
}

function parseCerts(raw?: string) {
  if (!raw) return [] as string[];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(String).filter(Boolean) : [];
  } catch {
    return raw.split(/[\n,]/).map((item) => item.trim()).filter(Boolean);
  }
}

function openCoach(row?: CoachRow) {
  coachForm.id = row?.id || "";
  coachForm.name = row?.name || "";
  coachForm.phone = row?.phone || "";
  coachForm.intro = row?.intro || "";
  coachForm.avatarUrl = row?.avatar_url || "";
  coachForm.photoUrl = row?.photo_url || "";
  coachForm.specialty = row?.specialty || "";
  coachForm.rating = Number(row?.rating ?? 5);
  coachForm.lessonTaught = Number(row?.lesson_taught ?? 0);
  coachForm.certs = parseCerts(row?.certificate_urls);
  coachOpen.value = true;
}

async function saveCoach() {
  const body = {
    storeId: storeId.value,
    name: coachForm.name,
    phone: coachForm.phone,
    intro: coachForm.intro,
    avatarUrl: coachForm.avatarUrl,
    photoUrl: coachForm.photoUrl || coachForm.avatarUrl,
    specialty: coachForm.specialty,
    rating: coachForm.rating,
    lessonTaught: coachForm.lessonTaught,
    certificateUrls: JSON.stringify(coachForm.certs)
  };
  if (coachForm.id) {
    await http.put(`/api/admin/coaches/${coachForm.id}`, body);
  } else {
    await http.post("/api/admin/coaches", body);
  }
  coachOpen.value = false;
  ElMessage.success("已保存");
  await reload();
}

async function removeCoach(row: CoachRow) {
  try {
    await ElMessageBox.confirm(`删除教练「${row.name}」。已有学员课时时不能删。`, "删除教练", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/coaches/${row.id}`);
  ElMessage.success("已删除");
  await reload();
}

function openPack(row?: Record<string, unknown>) {
  packForm.id = row ? String(row.id) : "";
  packForm.coachId = row ? String(row.coach_id) : (coaches.value[0]?.id || "");
  packForm.name = row ? String(row.name || "") : "";
  packForm.priceFen = row ? Number(row.price_fen || 0) : 0;
  packForm.lessonCount = row ? Number(row.lesson_count || 1) : 1;
  packForm.minutesPerLesson = row ? Number(row.minutes_per_lesson || 60) : 60;
  packForm.coverUrl = row ? String(row.cover_url || "") : "";
  packForm.content = row ? String(row.content || "") : "";
  packForm.audience = row ? String(row.audience || "") : "";
  packForm.status = row ? String(row.status || "ON") : "ON";
  packOpen.value = true;
}

async function savePack() {
  if (!packForm.coachId) {
    ElMessage.warning("请先选择教练");
    return;
  }
  const body = {
    coachId: packForm.coachId,
    name: packForm.name,
    priceFen: packForm.priceFen,
    lessonCount: packForm.lessonCount,
    minutesPerLesson: packForm.minutesPerLesson,
    coverUrl: packForm.coverUrl,
    content: packForm.content,
    audience: packForm.audience,
    status: packForm.status
  };
  if (packForm.id) {
    await http.put(`/api/admin/packs/${packForm.id}`, body);
  } else {
    await http.post("/api/admin/packs", body);
  }
  packOpen.value = false;
  ElMessage.success("已保存");
  await reload();
}

async function removePack(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm(`删除课程「${row.name}」。已有学员课时时不能删。`, "删除课程", { type: "warning" });
  } catch {
    return;
  }
  await http.delete(`/api/admin/packs/${row.id}`);
  ElMessage.success("已删除");
  await reload();
}
</script>

<style scoped>
.lead { color: #78716c; margin: 0 0 16px; }
.toolbar { margin-bottom: 8px; }
.thumb { width: 48px; height: 48px; object-fit: cover; border-radius: 8px; }
.certs { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.cert { display: flex; align-items: center; gap: 12px; }
.cert img { width: 72px; height: 72px; object-fit: cover; border-radius: 8px; }
</style>
