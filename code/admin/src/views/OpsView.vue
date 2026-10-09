<template>
  <div>
    <h1>{{ title }}</h1>
    <p class="lead">{{ lead }}</p>
    <el-form :inline="true" @submit.prevent="save">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <el-input v-model="form[field.key]" />
      </el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">保存</el-button></el-form-item>
    </el-form>
    <el-table :data="rows">
      <el-table-column v-for="column in columns" :key="column" :prop="column" :label="column" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";

const route = useRoute();
const rows = ref<Record<string, unknown>[]>([]);
const form = reactive<Record<string, string>>({});

const title = computed(() => ({
  "/equipment": "器械教程",
  "/groupon": "团购券",
  "/coaches": "教练与课程",
  "/franchise": "加盟",
  "/messages": "站内消息",
  "/violations": "违规处理"
}[route.path] || "管理"));

const lead = computed(() => ({
  "/equipment": "视频先填写可访问的链接。正式环境应直传到对象存储，单文件不超过 200MB。",
  "/groupon": "券码只存哈希。用户在小程序提交后，匹配到规则就按对应卡种开通会员。美团和抖音平台接口还没接。",
  "/coaches": "教练用手机号和会员账号对应。买课增加课时，不会开通进店会员。",
  "/franchise": "页面文案由总账号配置。申请记录只在这里查看。",
  "/messages": "只做站内已读，没有运营推送规则。",
  "/violations": "扣天数会改会员结束时间，并写入调整记录。勾选停用则立即结束会员。"
}[route.path] || ""));

const fields = computed(() => {
  if (route.path === "/equipment") return [
    { key: "storeId", label: "门店编号" }, { key: "code", label: "器械编号" }, { key: "name", label: "名称" },
    { key: "intro", label: "图文" }, { key: "videoUrl", label: "视频链接" }
  ];
  if (route.path === "/groupon") return [
    { key: "storeId", label: "门店编号" }, { key: "platform", label: "平台 MEITUAN/DOUYIN" },
    { key: "code", label: "券码" }, { key: "cardProductId", label: "卡种编号" }
  ];
  if (route.path === "/coaches") return [
    { key: "storeId", label: "门店编号" }, { key: "name", label: "教练" }, { key: "phone", label: "手机号" },
    { key: "coachId", label: "已有教练编号" }, { key: "packName", label: "课程名" }, { key: "priceFen", label: "价格分" },
    { key: "lessonCount", label: "课时" }, { key: "content", label: "课程内容" }, { key: "audience", label: "适合人群" }
  ];
  if (route.path === "/franchise") return [
    { key: "intro", label: "介绍" }, { key: "hotline", label: "热线" }, { key: "points", label: "卖点" },
    { key: "support", label: "总部支持" }, { key: "steps", label: "流程" }
  ];
  if (route.path === "/messages") return [
    { key: "userId", label: "用户编号" }, { key: "title", label: "标题" }, { key: "content", label: "内容" }
  ];
  return [
    { key: "membershipId", label: "会员记录编号" }, { key: "deltaDays", label: "扣减天数" },
    { key: "revoke", label: "停用填1" }, { key: "reason", label: "原因" }
  ];
});

const columns = computed(() => rows.value[0] ? Object.keys(rows.value[0]) : []);

watch(() => route.path, load);
onMounted(load);

async function load() {
  if (route.path === "/franchise") {
    const page = await http.get<ApiBody<Record<string, string>>>("/api/admin/franchise");
    Object.assign(form, page.data.data);
    const leads = await http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/franchise/leads");
    rows.value = leads.data.data;
    return;
  }
  if (route.path === "/groupon") {
    const response = await http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/groupon-rules");
    rows.value = response.data.data;
    return;
  }
  rows.value = [];
}

async function save() {
  if (route.path === "/equipment") {
    await http.post("/api/admin/equipment", form);
  } else if (route.path === "/groupon") {
    await http.post("/api/admin/groupon-rules", form);
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
  await load();
}
</script>
