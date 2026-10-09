<template>
  <div>
    <h1>本店档案</h1>
    <p class="lead">营业信息由总账号维护。本店可以自己设置电话和到店指引。</p>
    <el-descriptions v-if="store" :column="1" border>
      <el-descriptions-item label="编号">{{ store.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ store.name }}</el-descriptions-item>
      <el-descriptions-item label="地址">{{ store.province }} {{ store.city }} {{ store.address }}</el-descriptions-item>
      <el-descriptions-item label="营业状态">{{ store.status === "OPEN" ? "营业" : "停业" }}</el-descriptions-item>
      <el-descriptions-item label="WiFi">{{ store.wifiSsid || "未配置" }} / {{ store.wifiPassword || "未配置" }}</el-descriptions-item>
    </el-descriptions>

    <h3>分时段电话</h3>
    <p class="hint">白班、夜班必须填写时段。夜班可以跨过 0 点，例如 21:30 到 09:00。</p>
    <div v-for="(phone, index) in phones" :key="index" class="row">
      <el-select v-model="phone.phoneType" style="width: 110px">
        <el-option label="白班" value="DAY" />
        <el-option label="夜班" value="NIGHT" />
        <el-option label="后勤" value="LOGISTICS" />
        <el-option label="投诉" value="COMPLAINT" />
      </el-select>
      <el-input v-model="phone.phone" placeholder="号码" />
      <el-input v-model="phone.timeStart" placeholder="开始 HH:mm" />
      <el-input v-model="phone.timeEnd" placeholder="结束 HH:mm" />
      <el-button link type="danger" @click="phones.splice(index, 1)">删除</el-button>
    </div>
    <el-button @click="phones.push({ phoneType: 'DAY', phone: '', timeStart: '', timeEnd: '' })">添加电话</el-button>

    <h3>到店指引</h3>
    <div v-for="(guide, index) in guides" :key="index" class="row">
      <ImageField v-model="guide.imageUrl" biz="GUIDE" />
      <el-input v-model="guide.caption" placeholder="说明" />
      <el-button link @click="moveGuide(index, -1)">上移</el-button>
      <el-button link @click="moveGuide(index, 1)">下移</el-button>
      <el-button link type="danger" @click="guides.splice(index, 1)">删除</el-button>
    </div>
    <el-button @click="guides.push({ imageUrl: '', caption: '' })">添加步骤</el-button>
    <div class="actions">
      <el-button type="primary" :disabled="!store" @click="save">保存电话和指引</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import ImageField from "../components/ImageField.vue";
import { http, type ApiBody } from "../api/http";

interface StoreRow {
  id: string;
  code: string;
  name: string;
  province: string;
  city: string;
  address: string;
  status: string;
  wifiSsid: string | null;
  wifiPassword: string | null;
}

interface PhoneRow {
  phoneType: string;
  phone: string;
  timeStart: string;
  timeEnd: string;
}

interface GuideRow {
  imageUrl: string;
  caption: string;
}

const store = ref<StoreRow | null>(null);
const phones = ref<PhoneRow[]>([]);
const guides = ref<GuideRow[]>([]);

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  store.value = response.data.data[0] || null;
  if (!store.value) {
    return;
  }
  const phoneRes = await http.get<ApiBody<PhoneRow[]>>(`/api/admin/stores/${store.value.id}/phones`);
  phones.value = phoneRes.data.data.map((item) => ({
    phoneType: item.phoneType,
    phone: item.phone,
    timeStart: item.timeStart || "",
    timeEnd: item.timeEnd || ""
  }));
  const guideRes = await http.get<ApiBody<GuideRow[]>>(`/api/admin/stores/${store.value.id}/guides`);
  guides.value = guideRes.data.data.map((item) => ({ imageUrl: item.imageUrl, caption: item.caption }));
});

function moveGuide(index: number, delta: number) {
  const next = index + delta;
  if (next < 0 || next >= guides.value.length) return;
  const [item] = guides.value.splice(index, 1);
  guides.value.splice(next, 0, item);
}

async function save() {
  if (!store.value) {
    return;
  }
  await http.put(`/api/admin/stores/${store.value.id}/phones`, {
    items: phones.value.map((item, index) => ({ ...item, sortNo: index }))
  });
  await http.put(`/api/admin/stores/${store.value.id}/guides`, {
    items: guides.value.map((item, index) => ({ ...item, sortNo: index }))
  });
  ElMessage.success("已保存");
}
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
h3 {
  margin: 22px 0 8px;
}
.row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.actions {
  margin-top: 16px;
}
</style>
