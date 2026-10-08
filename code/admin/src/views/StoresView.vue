<template>
  <div>
    <h1>全部门店</h1>
    <p class="lead">编号创建后不能在这里修改。点「地图选点」可以在地图上选位置，省、市、地址和经纬度会自动填上，也可以再改。</p>
    <el-form :inline="true" :model="form" @submit.prevent="create">
      <el-form-item label="编号"><el-input v-model="form.code" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="省"><el-input v-model="form.province" /></el-form-item>
      <el-form-item label="市"><el-input v-model="form.city" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="form.address" class="address-input" /></el-form-item>
      <el-form-item label="经度"><el-input v-model="form.longitude" /></el-form-item>
      <el-form-item label="纬度"><el-input v-model="form.latitude" /></el-form-item>
      <el-form-item>
        <el-button native-type="button" @click="openPick('create')">地图选点</el-button>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">新增</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column prop="code" label="编号" width="100" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="city" label="城市" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">{{ row.status === "OPEN" ? "营业" : "停业" }}</template>
      </el-table-column>
      <el-table-column label="在线人数" width="100">
        <template #default>待闸机</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editing" title="编辑门店" width="760px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="edit.name" /></el-form-item>
        <el-form-item label="省"><el-input v-model="edit.province" /></el-form-item>
        <el-form-item label="市"><el-input v-model="edit.city" /></el-form-item>
        <el-form-item label="位置">
          <el-button native-type="button" @click="openPick('edit')">地图选点</el-button>
        </el-form-item>
        <el-form-item label="地址"><el-input v-model="edit.address" /></el-form-item>
        <el-form-item label="经度"><el-input v-model="edit.longitude" /></el-form-item>
        <el-form-item label="纬度"><el-input v-model="edit.latitude" /></el-form-item>
        <el-form-item label="封面">
          <ImageField v-model="edit.coverUrl" biz="COVER" />
        </el-form-item>
        <el-form-item label="营业时间"><el-input v-model="edit.businessHours" /></el-form-item>
        <el-form-item label="营业状态">
          <el-select v-model="edit.status" style="width: 160px">
            <el-option label="营业" value="OPEN" />
            <el-option label="停业" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="WiFi 账号"><el-input v-model="edit.wifiSsid" /></el-form-item>
        <el-form-item label="WiFi 密码"><el-input v-model="edit.wifiPassword" /></el-form-item>
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
        <el-input v-model="guide.caption" placeholder="说明" />
        <el-button link type="danger" @click="guides.splice(index, 1)">删除</el-button>
      </div>
      <el-button @click="guides.push({ imageUrl: '', caption: '' })">添加步骤</el-button>
      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <MapPicker v-model:visible="picking" :longitude="pickLongitude" :latitude="pickLatitude" @pick="onPick" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import ImageField from "../components/ImageField.vue";
import MapPicker from "../components/MapPicker.vue";
import { http, type ApiBody } from "../api/http";

interface StoreRow {
  id: string;
  code: string;
  name: string;
  province: string;
  city: string;
  address: string;
  longitude: number;
  latitude: number;
  coverUrl: string | null;
  businessHours: string;
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

const rows = ref<StoreRow[]>([]);
const editing = ref(false);
const picking = ref(false);
const pickMode = ref<"create" | "edit">("create");
const currentId = ref("");
const phones = ref<PhoneRow[]>([]);
const guides = ref<GuideRow[]>([]);
const form = reactive({
  code: "",
  name: "",
  province: "",
  city: "",
  address: "",
  longitude: "",
  latitude: ""
});
const edit = reactive({
  name: "",
  province: "",
  city: "",
  address: "",
  longitude: "",
  latitude: "",
  coverUrl: "",
  businessHours: "24h",
  status: "OPEN",
  wifiSsid: "",
  wifiPassword: ""
});

const pickLongitude = computed(() => (pickMode.value === "create" ? form.longitude : edit.longitude));
const pickLatitude = computed(() => (pickMode.value === "create" ? form.latitude : edit.latitude));

function openPick(mode: "create" | "edit") {
  pickMode.value = mode;
  picking.value = true;
}

function onPick(place: { province: string; city: string; address: string; longitude: string; latitude: string }) {
  const target = pickMode.value === "create" ? form : edit;
  target.province = place.province;
  target.city = place.city;
  target.address = place.address;
  target.longitude = place.longitude;
  target.latitude = place.latitude;
}

async function load() {
  const response = await http.get<ApiBody<StoreRow[]>>("/api/admin/stores");
  rows.value = response.data.data;
}

async function create() {
  await http.post("/api/admin/stores", {
    ...form,
    longitude: Number(form.longitude),
    latitude: Number(form.latitude)
  });
  form.code = "";
  form.name = "";
  await load();
}

async function openEdit(row: StoreRow) {
  currentId.value = row.id;
  edit.name = row.name;
  edit.province = row.province;
  edit.city = row.city;
  edit.address = row.address;
  edit.longitude = String(row.longitude);
  edit.latitude = String(row.latitude);
  edit.coverUrl = row.coverUrl || "";
  edit.businessHours = row.businessHours || "24h";
  edit.status = row.status;
  edit.wifiSsid = row.wifiSsid || "";
  edit.wifiPassword = row.wifiPassword || "";
  const phoneRes = await http.get<ApiBody<PhoneRow[]>>(`/api/admin/stores/${row.id}/phones`);
  phones.value = phoneRes.data.data.map((item) => ({
    phoneType: item.phoneType,
    phone: item.phone,
    timeStart: item.timeStart || "",
    timeEnd: item.timeEnd || ""
  }));
  const guideRes = await http.get<ApiBody<GuideRow[]>>(`/api/admin/stores/${row.id}/guides`);
  guides.value = guideRes.data.data.map((item) => ({ imageUrl: item.imageUrl, caption: item.caption }));
  editing.value = true;
}

async function save() {
  await http.put(`/api/admin/stores/${currentId.value}`, {
    name: edit.name,
    province: edit.province,
    city: edit.city,
    address: edit.address,
    longitude: Number(edit.longitude),
    latitude: Number(edit.latitude),
    coverUrl: edit.coverUrl,
    businessHours: edit.businessHours,
    status: edit.status,
    wifiSsid: edit.wifiSsid,
    wifiPassword: edit.wifiPassword
  });
  await http.put(`/api/admin/stores/${currentId.value}/phones`, {
    items: phones.value.map((item, index) => ({ ...item, sortNo: index }))
  });
  await http.put(`/api/admin/stores/${currentId.value}/guides`, {
    items: guides.value.map((item, index) => ({ ...item, sortNo: index }))
  });
  ElMessage.success("已保存");
  editing.value = false;
  await load();
}

onMounted(load);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
h3 {
  margin: 16px 0 8px;
}
.hint {
  color: #888;
  font-size: 13px;
}
.row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
