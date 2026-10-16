<template>
  <div>
    <h1>消息</h1>
    <p class="lead">预约和申诉可以处理。系统操作记录新增、修改、删除，用来事后核对。也可手动发站内信。</p>

    <el-form :inline="true" class="toolbar">
      <el-form-item v-if="session.role === 'MASTER'" label="门店">
        <el-select v-model="filter.storeId" clearable placeholder="全部" style="width: 180px" @change="onStoreChange">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="教练">
        <el-select v-model="filter.coachId" clearable placeholder="全部" style="width: 180px">
          <el-option v-for="coach in coaches" :key="coach.id" :label="coach.name" :value="coach.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filter.bizType" clearable placeholder="全部" style="width: 160px">
          <el-option label="预约相关" value="LESSON_BOOKING" />
          <el-option label="申诉相关" value="LESSON_APPEAL" />
          <el-option label="系统操作" value="SYSTEM_OP" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="loadAudits">查询</el-button></el-form-item>
    </el-form>

    <el-table :data="audits" max-height="480" @row-click="openRow">
      <el-table-column prop="created_at" label="时间" width="180" />
      <el-table-column prop="store_name" label="门店" width="120" />
      <el-table-column prop="coach_name" label="教练" width="100" />
      <el-table-column label="学员" min-width="160">
        <template #default="{ row }">
          {{ row.nickname && row.nickname !== "微信用户" ? row.nickname : (row.member_no || row.user_id || "—") }}
          <span v-if="row.phone" class="muted"> {{ row.phone }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="140">
        <template #default="{ row }">{{ bizLabel(String(row.biz_type || "")) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="140" />
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.biz_id && (row.biz_type === 'LESSON_BOOKING' || row.biz_type === 'LESSON_APPEAL')"
            link
            type="primary"
            @click.stop="openRow(row)"
          >处理</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="session.role === 'MASTER'">
      <h2>手动发信</h2>
      <el-form label-width="90px" style="max-width: 560px" @submit.prevent="send">
        <el-form-item label="用户编号"><el-input v-model="manual.userId" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="manual.title" /></el-form-item>
        <el-form-item label="内容"><el-input v-model="manual.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item><el-button type="primary" native-type="submit">发送</el-button></el-form-item>
      </el-form>
    </template>

    <el-drawer v-model="drawer" title="预约详情" size="420px" destroy-on-close>
      <template v-if="booking">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="课程">{{ booking.packName }}</el-descriptions-item>
          <el-descriptions-item label="教练">{{ booking.coachName }}</el-descriptions-item>
          <el-descriptions-item label="学员">{{ booking.userName }}（{{ booking.memberNo || "—" }}）</el-descriptions-item>
          <el-descriptions-item label="手机">{{ booking.phone || "—" }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ booking.classDate }} {{ booking.startHm }}</el-descriptions-item>
          <el-descriptions-item label="座位">{{ booking.seatNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(String(booking.status)) }}</el-descriptions-item>
          <el-descriptions-item v-if="booking.actedByLabel" label="操作端">{{ booking.actedByLabel }}</el-descriptions-item>
          <el-descriptions-item v-if="booking.rejectReason" label="原因">{{ booking.rejectReason }}</el-descriptions-item>
          <el-descriptions-item v-if="booking.pastStart" label="提示">已过开课时间</el-descriptions-item>
        </el-descriptions>

        <div v-if="Array.isArray(booking.appeals) && booking.appeals.length" class="appeals">
          <h3>申诉记录</h3>
          <div v-for="a in booking.appeals" :key="String(a.id)" class="appeal">
            <div>{{ a.status }} · {{ a.reason }}</div>
            <div v-if="a.rejectReason" class="muted">驳回：{{ a.rejectReason }}</div>
          </div>
        </div>

        <div class="actions">
          <el-button v-if="booking.status === 'PENDING_COACH'" type="primary" @click="doApprove">同意</el-button>
          <el-button v-if="booking.status === 'PENDING_COACH'" @click="askReason('reject')">拒绝</el-button>
          <el-button v-if="booking.status === 'APPEALING' || booking.status === 'CONFIRMED'" type="warning" @click="doRefund">退课</el-button>
          <el-button v-if="booking.status === 'APPEALING'" @click="askReason('rejectAppeal')">驳回申诉</el-button>
        </div>
      </template>
      <el-empty v-else description="加载中…" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const session = useSessionStore();
const stores = ref<{ id: string; name: string }[]>([]);
const coaches = ref<{ id: string; name: string }[]>([]);
const audits = ref<Record<string, unknown>[]>([]);
const filter = reactive({ storeId: "", coachId: "", bizType: "" });
const manual = reactive({ userId: "", title: "", content: "" });
const drawer = ref(false);
const booking = ref<Record<string, unknown> | null>(null);
const bookingId = ref("");

onMounted(async () => {
  const storeRes = await http.get<ApiBody<{ id: string; name: string }[]>>("/api/admin/stores");
  stores.value = storeRes.data.data;
  if (session.role === "STORE" && session.storeId) {
    filter.storeId = String(session.storeId);
  } else {
    filter.storeId = stores.value[0]?.id || "";
  }
  await onStoreChange();
  await loadAudits();
});

async function onStoreChange() {
  filter.coachId = "";
  coaches.value = [];
  if (!filter.storeId) return;
  const response = await http.get<ApiBody<{ id: string; name: string }[]>>("/api/admin/coaches", {
    params: { storeId: filter.storeId },
  });
  coaches.value = response.data.data;
}

async function loadAudits() {
  const response = await http.get<ApiBody<Record<string, unknown>[]>>("/api/admin/message-audits", {
    params: {
      storeId: filter.storeId || undefined,
      coachId: filter.coachId || undefined,
      bizType: filter.bizType || undefined,
    },
  });
  audits.value = response.data.data;
}

function bizLabel(bizType: string) {
  const map: Record<string, string> = {
    LESSON_BOOKING: "预约相关",
    LESSON_APPEAL: "申诉相关",
    SYSTEM_OP: "系统操作",
  };
  return map[bizType] || bizType || "—";
}

async function openRow(row: Record<string, unknown>) {
  const bizType = String(row.biz_type || "");
  const bizId = row.biz_id == null ? "" : String(row.biz_id);
  if (!bizId || (bizType !== "LESSON_BOOKING" && bizType !== "LESSON_APPEAL")) {
    return;
  }
  bookingId.value = bizId;
  drawer.value = true;
  booking.value = null;
  const response = await http.get<ApiBody<Record<string, unknown>>>(`/api/admin/lesson-bookings/${bizId}`);
  booking.value = response.data.data;
}

function statusText(status: string) {
  const map: Record<string, string> = {
    PENDING_COACH: "待确认",
    CONFIRMED: "已确认",
    REJECTED: "已拒绝",
    CANCELLED: "已取消",
    APPEALING: "申诉中",
    REFUNDED: "已退课",
  };
  return map[status] || status;
}

async function reloadBooking() {
  if (!bookingId.value) return;
  const response = await http.get<ApiBody<Record<string, unknown>>>(`/api/admin/lesson-bookings/${bookingId.value}`);
  booking.value = response.data.data;
  await loadAudits();
}

async function doApprove() {
  await http.post(`/api/admin/lesson-bookings/${bookingId.value}/approve`);
  ElMessage.success("已同意（操作端：门店）");
  await reloadBooking();
}

async function doRefund() {
  if (booking.value?.pastStart) {
    try {
      await ElMessageBox.confirm("已过开课时间，确认仍要退回 1 节课时？", "确认退课", { type: "warning" });
    } catch {
      return;
    }
  }
  await http.post(`/api/admin/lesson-bookings/${bookingId.value}/refund`);
  ElMessage.success("已退课（操作端：门店）");
  await reloadBooking();
}

async function askReason(mode: "reject" | "rejectAppeal") {
  try {
    const { value } = await ElMessageBox.prompt(mode === "reject" ? "请填写拒绝原因" : "请填写驳回原因", "填写原因", {
      inputPlaceholder: "原因",
      confirmButtonText: "提交",
      cancelButtonText: "取消",
      inputValidator: (v) => (!!v && !!String(v).trim()) || "请填写原因",
    });
    const reason = String(value).trim();
    if (mode === "reject") {
      await http.post(`/api/admin/lesson-bookings/${bookingId.value}/reject`, { reason });
      ElMessage.success("已拒绝（操作端：门店）");
    } else {
      await http.post(`/api/admin/lesson-bookings/${bookingId.value}/reject-appeal`, { reason });
      ElMessage.success("已驳回申诉（操作端：门店）");
    }
    await reloadBooking();
  } catch {
    /* cancel */
  }
}

async function send() {
  await http.post("/api/admin/messages", manual);
  ElMessage.success("已发送");
  manual.userId = "";
  manual.title = "";
  manual.content = "";
  await loadAudits();
}
</script>

<style scoped>
.lead { color: #78716c; margin: 0 0 16px; }
.toolbar { margin-bottom: 12px; }
.muted { color: #a8a29e; }
h2 { margin-top: 28px; font-size: 18px; }
h3 { margin: 20px 0 8px; font-size: 15px; }
.appeals { margin-top: 16px; }
.appeal { padding: 8px 0; border-bottom: 1px solid #f3f4f6; font-size: 13px; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 20px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
