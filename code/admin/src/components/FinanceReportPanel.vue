<template>
  <div class="panel">
    <div class="head">
      <div>
        <h2>门店财务</h2>
        <p class="sub">统计已支付订单（不含已退款）。可按日期与门店筛选，支持导出 Excel。</p>
      </div>
      <el-button type="primary" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
    </div>

    <el-tabs v-if="session.role === 'MASTER'" v-model="tab">
      <el-tab-pane label="总览" name="overview" />
      <el-tab-pane label="分店对比" name="stores" />
    </el-tabs>

    <el-form :inline="true" class="filters">
      <el-form-item label="区间">
        <el-radio-group v-model="preset" @change="applyPreset">
          <el-radio-button value="7d">近7天</el-radio-button>
          <el-radio-button value="month">本月</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          :disabled="preset !== 'custom'"
          @change="load"
        />
      </el-form-item>
      <el-form-item v-if="session.role === 'MASTER' && tab === 'overview'" label="门店">
        <el-select v-model="storeId" clearable placeholder="全部门店" style="width: 180px" @change="load">
          <el-option v-for="store in stores" :key="store.id" :label="store.name" :value="store.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="柱图">
        <el-radio-group v-model="metric" @change="renderCharts">
          <el-radio-button value="amount">按金额</el-radio-button>
          <el-radio-button value="count">按数量</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item><el-button @click="load">刷新</el-button></el-form-item>
    </el-form>

    <div class="kpis">
      <div class="kpi">
        <span>总收入</span>
        <strong>{{ yuan(summary.amountFen) }}</strong>
        <small>{{ summary.orderCount || 0 }} 笔订单</small>
      </div>
      <div class="kpi">
        <span>会员卡</span>
        <strong>{{ yuan(summary.cardAmountFen) }}</strong>
        <small>{{ summary.cardCount || 0 }} 张</small>
      </div>
      <div class="kpi">
        <span>教练课程</span>
        <strong>{{ yuan(summary.courseAmountFen) }}</strong>
        <small>{{ summary.courseCount || 0 }} 份</small>
      </div>
      <div class="kpi">
        <span>参观卡</span>
        <strong>{{ yuan(summary.visitAmountFen) }}</strong>
        <small>{{ summary.visitCount || 0 }} 张</small>
      </div>
    </div>

    <div v-show="tab === 'overview'" class="charts">
      <div class="chart-card">
        <div class="chart-title">收入构成</div>
        <div ref="pieEl" class="chart" />
      </div>
      <div class="chart-card">
        <div class="chart-title">卡种{{ metric === "amount" ? "金额" : "销量" }}</div>
        <div ref="cardEl" class="chart" />
      </div>
      <div class="chart-card wide">
        <div class="chart-title">教练课程{{ metric === "amount" ? "金额" : "销量" }}</div>
        <div ref="courseEl" class="chart" />
      </div>
    </div>

    <div v-show="tab === 'stores'" class="charts">
      <div class="chart-card wide">
        <div class="chart-title">分店{{ metric === "amount" ? "收入" : "订单数" }}对比</div>
        <div ref="storeEl" class="chart tall" />
      </div>
    </div>

    <div v-show="tab === 'overview'" class="tables">
      <h3>卡种明细</h3>
      <el-table :data="report?.byCard || []" size="small" max-height="260">
        <el-table-column prop="name" label="卡种" min-width="160" />
        <el-table-column prop="count" label="销量" width="90" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ yuan(row.amountFen) }}</template>
        </el-table-column>
      </el-table>

      <h3>教练课程明细</h3>
      <el-table :data="report?.byCoachCourse || []" size="small" max-height="260">
        <el-table-column prop="coachName" label="教练" width="120" />
        <el-table-column prop="packName" label="课包" min-width="160" />
        <el-table-column prop="count" label="销量" width="90" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">{{ yuan(row.amountFen) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div v-show="tab === 'stores'">
      <h3>分店明细</h3>
      <el-table :data="report?.byStore || []" size="small" max-height="360">
        <el-table-column prop="storeName" label="门店" min-width="160" />
        <el-table-column prop="count" label="订单数" width="100" />
        <el-table-column label="金额" width="140">
          <template #default="{ row }">{{ yuan(row.amountFen) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts/core";
import { PieChart, BarChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

echarts.use([PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

type NamedAmount = { name?: string; count: number; amountFen: number; packName?: string; coachName?: string; storeName?: string };
type FinanceReport = {
  from: string;
  to: string;
  summary: Record<string, number>;
  byBizType: Array<{ bizType: string; label: string; count: number; amountFen: number }>;
  byCard: NamedAmount[];
  byCoachCourse: NamedAmount[];
  byStore: NamedAmount[];
};

const session = useSessionStore();
const stores = ref<{ id: string; name: string }[]>([]);
const tab = ref("overview");
const preset = ref("month");
const range = ref<[string, string] | null>(null);
const storeId = ref("");
const metric = ref<"amount" | "count">("amount");
const report = ref<FinanceReport | null>(null);
const exporting = ref(false);
const summary = computed(() => report.value?.summary || {});

const pieEl = ref<HTMLDivElement | null>(null);
const cardEl = ref<HTMLDivElement | null>(null);
const courseEl = ref<HTMLDivElement | null>(null);
const storeEl = ref<HTMLDivElement | null>(null);
let pieChart: echarts.ECharts | null = null;
let cardChart: echarts.ECharts | null = null;
let courseChart: echarts.ECharts | null = null;
let storeChart: echarts.ECharts | null = null;

onMounted(async () => {
  const storeRes = await http.get<ApiBody<{ id: string; name: string }[]>>("/api/admin/stores");
  stores.value = storeRes.data.data;
  applyPreset();
  window.addEventListener("resize", onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
  pieChart?.dispose();
  cardChart?.dispose();
  courseChart?.dispose();
  storeChart?.dispose();
});

watch(tab, async () => {
  await nextTick();
  renderCharts();
});

function monthRange(): [string, string] {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const last = new Date(y, now.getMonth() + 1, 0).getDate();
  return [`${y}-${m}-01`, `${y}-${m}-${String(last).padStart(2, "0")}`];
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function today(): string {
  return daysAgo(0);
}

function applyPreset() {
  if (preset.value === "7d") {
    range.value = [daysAgo(6), today()];
  } else if (preset.value === "month") {
    range.value = monthRange();
  }
  void load();
}

function yuan(fen: unknown) {
  const n = Number(fen || 0) / 100;
  return n.toLocaleString("zh-CN", { style: "currency", currency: "CNY" });
}

function queryParams() {
  const params: Record<string, string> = {};
  if (range.value?.[0]) params.from = range.value[0];
  if (range.value?.[1]) params.to = range.value[1];
  if (session.role === "MASTER" && tab.value === "overview" && storeId.value) {
    params.storeId = storeId.value;
  }
  return params;
}

async function load() {
  if (!range.value?.[0] || !range.value?.[1]) return;
  const response = await http.get<ApiBody<FinanceReport>>("/api/admin/reports/finance", {
    params: queryParams(),
  });
  report.value = response.data.data;
  await nextTick();
  renderCharts();
}

function ensureCharts() {
  if (pieEl.value && !pieChart) pieChart = echarts.init(pieEl.value);
  if (cardEl.value && !cardChart) cardChart = echarts.init(cardEl.value);
  if (courseEl.value && !courseChart) courseChart = echarts.init(courseEl.value);
  if (storeEl.value && !storeChart) storeChart = echarts.init(storeEl.value);
}

function renderCharts() {
  if (!report.value) return;
  ensureCharts();
  const byBiz = report.value.byBizType || [];
  pieChart?.setOption({
    tooltip: { trigger: "item", formatter: "{b}: {c} 元 ({d}%)" },
    legend: { bottom: 0 },
    color: ["#ea580c", "#0d9488", "#6366f1"],
    series: [
      {
        type: "pie",
        radius: ["42%", "68%"],
        data: byBiz.map((item) => ({
          name: item.label,
          value: Number((Number(item.amountFen || 0) / 100).toFixed(2)),
        })),
        label: { formatter: "{b}\n{d}%" },
      },
    ],
  });

  const cards = report.value.byCard || [];
  cardChart?.setOption(barOption(
    cards.map((r) => String(r.name || "")),
    cards.map((r) => (metric.value === "amount" ? Number(r.amountFen || 0) / 100 : Number(r.count || 0))),
    metric.value === "amount" ? "元" : "笔",
  ));

  const courses = report.value.byCoachCourse || [];
  courseChart?.setOption(barOption(
    courses.map((r) => `${r.coachName || ""}·${r.packName || ""}`),
    courses.map((r) => (metric.value === "amount" ? Number(r.amountFen || 0) / 100 : Number(r.count || 0))),
    metric.value === "amount" ? "元" : "笔",
  ));

  const storesRows = report.value.byStore || [];
  storeChart?.setOption(barOption(
    storesRows.map((r) => String(r.storeName || "")),
    storesRows.map((r) => (metric.value === "amount" ? Number(r.amountFen || 0) / 100 : Number(r.count || 0))),
    metric.value === "amount" ? "元" : "笔",
  ));
}

function barOption(names: string[], values: number[], unit: string) {
  return {
    tooltip: { trigger: "axis" },
    grid: { left: 48, right: 16, top: 24, bottom: names.some((n) => n.length > 6) ? 72 : 40 },
    xAxis: {
      type: "category",
      data: names.length ? names : ["暂无数据"],
      axisLabel: { interval: 0, rotate: names.some((n) => n.length > 6) ? 30 : 0 },
    },
    yAxis: { type: "value", name: unit },
    series: [
      {
        type: "bar",
        data: names.length ? values : [0],
        itemStyle: { color: "#ea580c", borderRadius: [6, 6, 0, 0] },
        barMaxWidth: 36,
      },
    ],
  };
}

function onResize() {
  pieChart?.resize();
  cardChart?.resize();
  courseChart?.resize();
  storeChart?.resize();
}

async function exportExcel() {
  if (!range.value?.[0] || !range.value?.[1]) {
    ElMessage.warning("请选择日期区间");
    return;
  }
  exporting.value = true;
  try {
    const response = await http.get("/api/admin/reports/finance.xlsx", {
      params: queryParams(),
      responseType: "blob",
    });
    const blob = new Blob([response.data], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `财务报表_${range.value[0]}_${range.value[1]}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
    ElMessage.success("已开始下载");
  } finally {
    exporting.value = false;
  }
}
</script>

<style scoped>
.panel {
  margin-top: 28px;
  padding-top: 8px;
  border-top: 1px solid #f5f5f4;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}
h2 { margin: 0; font-size: 20px; }
h3 { margin: 20px 0 10px; font-size: 15px; }
.sub { margin: 6px 0 0; color: #78716c; font-size: 13px; }
.filters { margin: 8px 0 4px; }
.kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 12px 0 16px;
}
.kpi {
  padding: 16px 18px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #eceff3;
}
.kpi span { display: block; color: #78716c; font-size: 13px; }
.kpi strong { display: block; margin-top: 8px; font-size: 24px; letter-spacing: -0.03em; }
.kpi small { display: block; margin-top: 6px; color: #a8a29e; font-size: 12px; }
.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.chart-card {
  padding: 12px 12px 8px;
  border-radius: 14px;
  background: #fff;
  border: 1px solid #eceff3;
}
.chart-card.wide { grid-column: 1 / -1; }
.chart-title { font-size: 14px; font-weight: 700; margin-bottom: 4px; }
.chart { height: 280px; }
.chart.tall { height: 360px; }
.tables { margin-top: 8px; }
@media (max-width: 960px) {
  .kpis, .charts { grid-template-columns: 1fr; }
  .chart-card.wide { grid-column: auto; }
}
</style>
