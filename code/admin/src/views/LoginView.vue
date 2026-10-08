<template>
  <div class="page">
    <section class="hero">
      <div class="mark">24</div>
      <h1>24 小时自助健身</h1>
      <p>总账号看全部店，门店账号只看自己的店。人脸进店，没有开门码。</p>
    </section>
    <section class="panel">
      <el-form class="form" :model="form" @submit.prevent="submit">
        <h2>登录管理端</h2>
        <p class="sub">使用总账号或门店账号</p>
        <el-form-item label="账号">
          <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-button type="primary" native-type="submit" size="large" class="submit" :loading="loading">进入后台</el-button>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { http, type ApiBody } from "../api/http";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({
  username: "",
  password: ""
});

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning("请填写账号和密码");
    return;
  }
  loading.value = true;
  try {
    const response = await http.post<ApiBody<LoginResult>>("/api/admin/auth/login", form);
    const data = response.data.data;
    session.setSession(data.accessToken, data.role);
    await router.push("/");
  } finally {
    loading.value = false;
  }
}

interface LoginResult {
  accessToken: string;
  refreshToken: string;
  role: string;
  storeId: string | null;
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
}
.hero {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 64px;
  color: #fff;
  background:
    linear-gradient(160deg, rgba(28, 25, 23, 0.2), rgba(28, 25, 23, 0.72)),
    radial-gradient(circle at 20% 20%, #ea580c, transparent 42%),
    #1c1917;
}
.mark {
  width: 64px;
  height: 64px;
  border-radius: 20px;
  display: grid;
  place-items: center;
  background: #c2410c;
  font-size: 24px;
  font-weight: 800;
}
.hero h1 {
  margin: 28px 0 12px;
  font-size: 48px;
  line-height: 1.1;
  letter-spacing: -0.04em;
}
.hero p {
  max-width: 420px;
  margin: 0;
  color: #e7e5e4;
  font-size: 16px;
  line-height: 1.7;
}
.panel {
  display: grid;
  place-items: center;
  background: #f7f4ef;
}
.form {
  width: min(400px, calc(100% - 48px));
}
h2 {
  margin: 0;
  font-size: 28px;
  letter-spacing: -0.03em;
}
.sub {
  margin: 8px 0 28px;
  color: #78716c;
}
.submit {
  width: 100%;
}
@media (max-width: 860px) {
  .page {
    grid-template-columns: 1fr;
  }
  .hero {
    min-height: 240px;
    padding: 32px;
  }
  .hero h1 {
    font-size: 32px;
  }
}
</style>
