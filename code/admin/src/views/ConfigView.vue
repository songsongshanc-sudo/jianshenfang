<template>
  <div>
    <h1>系统配置</h1>
    <p class="lead">这里只调进店防抖、在线人数窗口、未支付订单超时和总部视频号。购买后不能退款。门店没填自己的视频号时，器械教程用这里的编号。</p>
    <el-form v-if="loaded" label-width="140px" style="max-width: 480px" @submit.prevent="save">
      <el-form-item label="进店防抖（秒）">
        <el-input v-model="form.entryDebounceSeconds" />
      </el-form-item>
      <el-form-item label="在线窗口（分钟）">
        <el-input v-model="form.onlineWindowMinutes" />
      </el-form-item>
      <el-form-item label="未支付超时（分钟）">
        <el-input v-model="form.orderExpireMinutes" />
      </el-form-item>
      <el-form-item label="隐私指引链接">
        <el-input v-model="privacyUrl" placeholder="https://" />
      </el-form-item>
      <el-form-item label="总部视频号">
        <el-input v-model="channelsFinderId" placeholder="sph 开头，视频号助手首页" />
      </el-form-item>
      <el-button @click="copyPrivacy">复制链接</el-button>
      <el-button type="primary" native-type="submit">保存</el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";

interface ConfigBody {
  entryDebounceSeconds: number;
  onlineWindowMinutes: number;
  orderExpireMinutes: number;
}

const loaded = ref(false);
const privacyUrl = ref("");
const channelsFinderId = ref("");
const form = reactive({
  entryDebounceSeconds: "15",
  onlineWindowMinutes: "90",
  orderExpireMinutes: "15"
});

async function load() {
  const response = await http.get<ApiBody<ConfigBody>>("/api/admin/configs");
  form.entryDebounceSeconds = String(response.data.data.entryDebounceSeconds);
  form.onlineWindowMinutes = String(response.data.data.onlineWindowMinutes);
  form.orderExpireMinutes = String(response.data.data.orderExpireMinutes);
  const privacy = await http.get<ApiBody<{ url: string }>>("/api/admin/privacy-url");
  privacyUrl.value = privacy.data.data.url || "";
  const channels = await http.get<ApiBody<{ finderId: string }>>("/api/admin/channels-finder");
  channelsFinderId.value = channels.data.data.finderId || "";
  loaded.value = true;
}

async function save() {
  await http.put("/api/admin/configs", {
    entryDebounceSeconds: Number(form.entryDebounceSeconds),
    onlineWindowMinutes: Number(form.onlineWindowMinutes),
    orderExpireMinutes: Number(form.orderExpireMinutes)
  });
  await http.put("/api/admin/privacy-url", { url: privacyUrl.value.trim() });
  await http.put("/api/admin/channels-finder", { finderId: channelsFinderId.value.trim() });
  ElMessage.success("已保存");
}

async function copyPrivacy() {
  if (!privacyUrl.value.trim()) {
    ElMessage.warning("请先填写链接");
    return;
  }
  await navigator.clipboard.writeText(privacyUrl.value.trim());
  ElMessage.success("已复制");
}

onMounted(load);
</script>

<style scoped>
h1 {
  margin-top: 0;
  font-size: 22px;
}
</style>
