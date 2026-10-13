<template>
  <div>
    <h1>本店档案</h1>
    <p class="lead">名称、地址、营业状态由总账号维护。本店可以自己设置 WiFi、电话和到店指引。</p>
    <el-descriptions v-if="store" :column="1" border>
      <el-descriptions-item label="编号">{{ store.code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ store.name }}</el-descriptions-item>
      <el-descriptions-item label="地址">{{ store.province }} {{ store.city }} {{ store.address }}</el-descriptions-item>
      <el-descriptions-item label="营业状态">{{ store.status === "OPEN" ? "营业" : "停业" }}</el-descriptions-item>
    </el-descriptions>

    <h3>本店 WiFi</h3>
    <p class="hint">小程序里注册完成的用户可查看账号和密码。留空表示未配置。</p>
    <el-form label-width="90px" class="wifi-form">
      <el-form-item label="WiFi 账号"><el-input v-model="wifiSsid" maxlength="64" placeholder="SSID" /></el-form-item>
      <el-form-item label="WiFi 密码"><el-input v-model="wifiPassword" maxlength="64" placeholder="可留空" show-password /></el-form-item>
    </el-form>

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
      <el-button v-if="guide.imageUrl" link type="primary" @click="preview?.show({ title: guide.caption || '到店指引', image: guide.imageUrl })">预览</el-button>
      <el-input v-model="guide.caption" placeholder="说明" />
      <el-button link @click="moveGuide(index, -1)">上移</el-button>
      <el-button link @click="moveGuide(index, 1)">下移</el-button>
      <el-button link type="danger" @click="guides.splice(index, 1)">删除</el-button>
    </div>
    <el-button @click="guides.push({ imageUrl: '', caption: '' })">添加步骤</el-button>
    <div class="actions">
      <el-button type="primary" :disabled="!store" @click="save">保存 WiFi、电话和指引</el-button>
    </div>
    <ContentPreview ref="preview" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import ContentPreview from "../components/ContentPreview.vue";
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

const preview = ref<InstanceType<typeof ContentPreview>>();
const store = ref<StoreRow | null>(null);
const wifiSsid = ref("");
const wifiPassword = ref("");
const phones = ref<PhoneRow[]>([]);
const guides = ref<GuideRow[]>([]);

onMounted(async () => {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  store.value = response.data.data[0] || null;
  if (!store.value) {
    return;
  }
  wifiSsid.value = store.value.wifiSsid || "";
  wifiPassword.value = store.value.wifiPassword || "";
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
  await http.put(`/api/admin/stores/${store.value.id}/wifi`, {
    wifiSsid: wifiSsid.value,
    wifiPassword: wifiPassword.value
  });
  await http.put(`/api/admin/stores/${store.value.id}/phones`, {
    items: phones.value.map((item, index) => ({ ...item, sortNo: index }))
  });
  await http.put(`/api/admin/stores/${store.value.id}/guides`, {
    items: guides.value.map((item, index) => ({ ...item, sortNo: index }))
  });
  store.value.wifiSsid = wifiSsid.value || null;
  store.value.wifiPassword = wifiPassword.value || null;
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
.lead,
.hint {
  color: #606266;
  font-size: 13px;
}
.wifi-form {
  max-width: 480px;
}
</style>
