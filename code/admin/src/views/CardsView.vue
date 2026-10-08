<template>
  <div>
    <h1>卡种</h1>
    <p class="lead">价格按元填写。不填库存表示不限量。累计天数和连续天数没达到时，小程序里这张卡是灰色的。</p>
    <el-form :inline="true">
      <el-form-item v-if="session.role === 'MASTER'" label="门店">
        <el-select v-model="storeId" placeholder="选择门店" style="width: 180px" @change="load">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="门店">
        <span>{{ storeName }}</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!storeId" @click="openCreate">新增卡种</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="name" label="名称" />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">{{ fenToYuan(row.priceFen) }} 元</template>
      </el-table-column>
      <el-table-column prop="validDays" label="天数" width="80" />
      <el-table-column label="剩余" width="90">
        <template #default="{ row }">{{ row.remaining == null ? "不限" : row.remaining }}</template>
      </el-table-column>
      <el-table-column label="解锁" min-width="160">
        <template #default="{ row }">{{ unlockText(row) }}</template>
      </el-table-column>
      <el-table-column label="首页" width="80">
        <template #default="{ row }">{{ row.homeVisible === 1 ? "是" : "否" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">{{ row.status === "ON" ? "上架" : "下架" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editing" :title="form.id ? '编辑卡种' : '新增卡种'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="价格（元）"><el-input v-model="form.priceYuan" /></el-form-item>
        <el-form-item label="展示文案"><el-input v-model="form.displayText" placeholder="例如每天最低 0.33 元，可不填" /></el-form-item>
        <el-form-item label="有效天数"><el-input v-model="form.validDays" /></el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="form.durationMode">
            <el-option label="满 24 小时算 1 天" value="HOURS_24" />
            <el-option label="自然日" value="NATURAL_DAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="库存"><el-input v-model="form.stockTotal" placeholder="不填表示不限量" /></el-form-item>
        <el-form-item label="解锁">
          <el-select v-model="form.unlockType">
            <el-option label="无" value="NONE" />
            <el-option label="累计满 N 天" value="CUMULATIVE_DAYS" />
            <el-option label="连续满 N 天" value="CONSECUTIVE_DAYS" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.unlockType !== 'NONE'" label="解锁天数"><el-input v-model="form.unlockDays" /></el-form-item>
        <el-form-item label="通卡">
          <el-select v-model="form.crossStore">
            <el-option label="仅本店" :value="0" />
            <el-option label="通卡" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="首页横滑">
          <el-select v-model="form.homeVisible">
            <el-option label="显示" :value="1" />
            <el-option label="不显示" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input v-model="form.sortNo" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="上架" value="ON" />
            <el-option label="下架" value="OFF" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

interface StoreOption {
  id: string;
  name: string;
}

interface CardRow {
  id: string;
  storeId: string;
  name: string;
  priceFen: number;
  displayText: string | null;
  validDays: number;
  durationMode: string;
  stockTotal: number | null;
  unlockType: string;
  unlockDays: number | null;
  crossStore: number;
  homeVisible: number;
  sortNo: number;
  status: string;
  remaining: number | null;
}

const session = useSessionStore();
const stores = ref<StoreOption[]>([]);
const storeId = ref("");
const storeName = computed(() => stores.value.find((item) => item.id === storeId.value)?.name || "");
const rows = ref<CardRow[]>([]);
const editing = ref(false);
const form = reactive({
  id: "",
  name: "",
  priceYuan: "",
  displayText: "",
  validDays: "7",
  durationMode: "HOURS_24",
  stockTotal: "",
  unlockType: "NONE",
  unlockDays: "",
  crossStore: 0,
  homeVisible: 1,
  sortNo: "0",
  status: "ON"
});

function fenToYuan(fen: number) {
  const yuan = fen / 100;
  return Number.isInteger(yuan) ? String(yuan) : yuan.toFixed(2);
}

function unlockText(row: CardRow) {
  if (row.unlockType === "CUMULATIVE_DAYS") return `累计满 ${row.unlockDays} 天`;
  if (row.unlockType === "CONSECUTIVE_DAYS") return `连续满 ${row.unlockDays} 天`;
  return "无";
}

async function loadStores() {
  const response = await http.get<ApiBody<StoreOption[]>>("/api/admin/stores");
  stores.value = response.data.data;
  if (!storeId.value && stores.value[0]) {
    storeId.value = stores.value[0].id;
    await load();
  }
}

async function load() {
  if (!storeId.value) {
    rows.value = [];
    return;
  }
  const response = await http.get<ApiBody<CardRow[]>>("/api/admin/cards", { params: { storeId: storeId.value } });
  rows.value = response.data.data;
}

function openCreate() {
  form.id = "";
  form.name = "";
  form.priceYuan = "";
  form.displayText = "";
  form.validDays = "7";
  form.durationMode = "HOURS_24";
  form.stockTotal = "";
  form.unlockType = "NONE";
  form.unlockDays = "";
  form.crossStore = 0;
  form.homeVisible = 1;
  form.sortNo = "0";
  form.status = "ON";
  editing.value = true;
}

function openEdit(row: CardRow) {
  form.id = row.id;
  form.name = row.name;
  form.priceYuan = fenToYuan(row.priceFen);
  form.displayText = row.displayText || "";
  form.validDays = String(row.validDays);
  form.durationMode = row.durationMode;
  form.stockTotal = row.stockTotal == null ? "" : String(row.stockTotal);
  form.unlockType = row.unlockType;
  form.unlockDays = row.unlockDays == null ? "" : String(row.unlockDays);
  form.crossStore = row.crossStore;
  form.homeVisible = row.homeVisible;
  form.sortNo = String(row.sortNo);
  form.status = row.status;
  editing.value = true;
}

async function save() {
  const price = Number(form.priceYuan);
  if (!Number.isFinite(price) || price <= 0) {
    ElMessage.warning("请填写价格");
    return;
  }
  const body = {
    storeId: storeId.value,
    name: form.name,
    priceFen: Math.round(price * 100),
    displayText: form.displayText,
    validDays: Number(form.validDays),
    durationMode: form.durationMode,
    stockTotal: form.stockTotal === "" ? null : Number(form.stockTotal),
    unlockType: form.unlockType,
    unlockDays: form.unlockType === "NONE" || form.unlockDays === "" ? null : Number(form.unlockDays),
    crossStore: form.crossStore,
    homeVisible: form.homeVisible,
    sortNo: Number(form.sortNo),
    status: form.status
  };
  if (form.id) {
    await http.put(`/api/admin/cards/${form.id}`, body);
  } else {
    await http.post("/api/admin/cards", body);
  }
  ElMessage.success("已保存");
  editing.value = false;
  await load();
}

onMounted(loadStores);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
