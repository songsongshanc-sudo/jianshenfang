<template>
  <div>
    <h1>今天先从门店开始</h1>
    <p class="lead">{{ session.role === "MASTER" ? "总账号可以配置全部门店、内容和协议。" : "门店账号可以设置本店 WiFi、电话、指引和卡种。" }}</p>
    <div class="stats">
      <div class="stat clickable" @click="goStores">
        <span>可见门店</span>
        <strong>{{ storeCount }}</strong>
        <em>点击查看</em>
      </div>
      <div class="stat clickable" @click="goDoors">
        <span>在线人数</span>
        <strong>{{ onlineCount }}</strong>
        <em>通行记录</em>
      </div>
      <div class="stat clickable" @click="goCards">
        <span>卡种</span>
        <strong>{{ cardCount }}</strong>
        <em>点击管理</em>
      </div>
    </div>

    <FinanceReportPanel />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";
import FinanceReportPanel from "../components/FinanceReportPanel.vue";

const session = useSessionStore();
const router = useRouter();
const storeCount = ref(0);
const cardCount = ref(0);
const onlineCount = ref(0);

onMounted(async () => {
  const response = await http.get<ApiBody<unknown[]>>("/api/admin/stores");
  storeCount.value = response.data.data.length;
  const cards = await http.get<ApiBody<unknown[]>>("/api/admin/cards");
  cardCount.value = cards.data.data.length;
  const online = await http.get<ApiBody<number>>("/api/admin/online");
  onlineCount.value = online.data.data;
});

function goStores() {
  router.push(session.role === "MASTER" ? "/stores" : "/my-store");
}

function goCards() {
  router.push("/cards");
}

function goDoors() {
  router.push("/doors");
}
</script>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.stat {
  padding: 22px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fff7ed, #fff);
  border: 1px solid #ffedd5;
}
.stat.clickable {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.stat.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(234, 88, 12, 0.12);
}
.stat span {
  display: block;
  color: #78716c;
  font-size: 13px;
}
.stat strong {
  display: block;
  margin-top: 10px;
  font-size: 32px;
  letter-spacing: -0.04em;
}
.stat em {
  display: block;
  margin-top: 8px;
  font-style: normal;
  color: #ea580c;
  font-size: 12px;
}
@media (max-width: 800px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
