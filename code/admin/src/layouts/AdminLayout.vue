<template>
  <el-container class="layout">
    <el-aside width="240px" class="aside">
      <div class="brand">
        <span class="mark">24</span>
        <div>
          <strong>自助健身</strong>
          <small>门店管理</small>
        </div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1c1917"
        text-color="#d6d3d1"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item index="/">工作台</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/stores">全部门店</el-menu-item>
        <el-menu-item v-if="session.role === 'STORE'" index="/my-store">本店档案</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/accounts">账号管理</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/banners">Banner</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/notices">公告</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/agreements">会员协议</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/configs">系统配置</el-menu-item>
        <el-menu-item index="/cards">卡种</el-menu-item>
        <el-menu-item index="/orders">订单</el-menu-item>
        <el-menu-item index="/gates">闸机</el-menu-item>
        <el-menu-item index="/doors">进店记录</el-menu-item>
        <el-menu-item index="/repairs">报修</el-menu-item>
        <el-menu-item index="/complaints">投诉</el-menu-item>
        <el-menu-item index="/lost">失物</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/equipment">器械</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/groupon">团购</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/coaches">教练</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/franchise">加盟</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/messages">消息</el-menu-item>
        <el-menu-item v-if="session.role === 'MASTER'" index="/violations">违规</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>
          <div class="crumb">24 小时无人门店</div>
          <div class="headline">{{ title }}</div>
        </div>
        <div class="account">
          <span class="role">{{ session.role === "MASTER" ? "总账号" : "门店账号" }}</span>
          <el-button round @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <div class="main-card">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "../stores/session";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const titles: Record<string, string> = {
  "/": "工作台",
  "/stores": "全部门店",
  "/my-store": "本店档案",
  "/accounts": "账号管理",
  "/banners": "Banner",
  "/notices": "公告",
  "/agreements": "会员协议",
  "/configs": "系统配置",
  "/cards": "卡种",
  "/orders": "订单",
  "/gates": "闸机",
  "/doors": "进店记录",
  "/repairs": "报修",
  "/complaints": "投诉",
  "/lost": "失物",
  "/equipment": "器械",
  "/groupon": "团购",
  "/coaches": "教练",
  "/franchise": "加盟",
  "/messages": "消息",
  "/violations": "违规"
};

const title = computed(() => titles[route.path] || "管理端");

function logout() {
  session.clear();
  router.push("/login");
}
</script>

<style scoped>
.aside {
  height: 100%;
  overflow: auto;
  background: var(--sidebar);
  color: #fff;
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 28px 22px 18px;
}
.brand strong,
.brand small {
  display: block;
}
.brand strong {
  font-size: 16px;
  letter-spacing: 0.04em;
}
.brand small {
  margin-top: 2px;
  color: #a8a29e;
  font-size: 12px;
}
.mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: #c2410c;
  font-weight: 800;
}
.menu {
  border-right: 0;
  padding: 8px 12px;
}
.menu :deep(.el-menu-item) {
  height: 44px;
  margin-bottom: 4px;
  border-radius: 12px;
}
.menu :deep(.el-menu-item.is-active) {
  background: #c2410c;
}
.soon {
  margin: auto 16px 20px;
  padding: 12px 14px;
  border-radius: 12px;
  color: #a8a29e;
  background: rgba(255, 255, 255, 0.04);
  font-size: 13px;
}
.header {
  height: 76px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--canvas);
  border-bottom: 1px solid var(--line);
}
.crumb {
  color: #a8a29e;
  font-size: 12px;
  letter-spacing: 0.08em;
}
.headline {
  margin-top: 2px;
  font-size: 18px;
  font-weight: 700;
}
.account {
  display: flex;
  align-items: center;
  gap: 12px;
}
.role {
  padding: 6px 10px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 13px;
}
</style>
