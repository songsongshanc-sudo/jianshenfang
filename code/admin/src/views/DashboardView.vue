<template>
  <div>
    <h1>今天先从门店开始</h1>
    <p class="lead">{{ session.role === "MASTER" ? "总账号可以配置全部门店、内容和协议。" : "门店账号只能查看本店档案。" }}</p>
    <div class="stats">
      <div class="stat">
        <span>可见门店</span>
        <strong>{{ storeCount }}</strong>
      </div>
      <div class="stat">
        <span>在线人数</span>
        <strong class="wait">待闸机</strong>
      </div>
      <div class="stat">
        <span>卡种</span>
        <strong class="wait">未开放</strong>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const session = useSessionStore();
const storeCount = ref(0);

onMounted(async () => {
  const response = await http.get<ApiBody<unknown[]>>("/api/admin/stores");
  storeCount.value = response.data.data.length;
});
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
.wait {
  font-size: 22px !important;
  color: #9a3412;
}
@media (max-width: 800px) {
  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
