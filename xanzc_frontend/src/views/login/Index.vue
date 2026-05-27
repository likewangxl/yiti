<template>
  <div class="login-page">
    <!-- 左：品牌渐变面板（与 sidebar 头像、workspace hero 同源色板） -->
    <aside class="brand">
      <div class="bg-deco">
        <span class="ring r1"></span>
        <span class="ring r2"></span>
        <span class="ring r3"></span>
      </div>
      <div class="brand-inner">
        <div class="logo">
          <span class="mark">银</span>
          <span class="logo-text">银行营销平台</span>
        </div>
        <h1 class="title">银行营销 · 业务执行 · 绩效平台</h1>
        <p class="tagline">Branch Marketing · Workflow · Performance</p>
        <ul class="hl">
          <li><span class="dot"></span>客户营销 · 线索管理 · 触达任务</li>
          <li><span class="dot"></span>流程审批 · 多级回调 · SLA 管控</li>
          <li><span class="dot"></span>绩效计算 · 报表分析 · 数据范围</li>
        </ul>
        <div class="copy">
          © 2026 Branch Platform · v1.0
        </div>
      </div>
    </aside>

    <!-- 右：表单 -->
    <main class="form-wrap">
      <div class="form-card">
        <div class="form-h">
          <div class="welcome">欢迎登录</div>
          <div class="hint">请使用账号登录后台</div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          class="form"
          @keyup.enter="onSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              clearable
              autocomplete="username"
            >
              <template #prefix><span class="ico">👤</span></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              show-password
              autocomplete="current-password"
            >
              <template #prefix><span class="ico">🔒</span></template>
            </el-input>
          </el-form-item>

          <el-button
            type="primary"
            class="btn-login"
            :loading="loading"
            @click="onSubmit"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>

          <!-- 统一认证登录入口：直接 302 到后端 /api/auth/uniauth/redirect → UIAS 单点登录页 -->
          <div class="alt-login">
            <span class="divider">或</span>
            <el-button class="btn-uniauth" @click="onUniAuthClick">
              🛡️ 统一认证登录
            </el-button>
          </div>

          <div class="tip" v-if="USE_MOCK">
            mock 模式：任意账号可登录（VITE_USE_MOCK=true）
          </div>
          <div class="tip" v-else>
            提示：开发环境默认账号 <code>admin</code> / <code>123456</code>
          </div>
        </el-form>

      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { login, uniAuthLogin } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { USE_MOCK } from '@/api/http';

const router = useRouter();
const route  = useRoute();
const store  = useUserStore();

const formRef = ref(null);
const loading = ref(false);
const form = reactive({ username: '', password: '' });

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 32, message: '用户名长度 2-32', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64', trigger: 'blur' }
  ]
};

async function onSubmit() {
  if (loading.value) return;
  try {
    await formRef.value.validate();
  } catch (_) { return; }

  loading.value = true;
  try {
    const user = await login(form.username, form.password);
    store.setUser(user);
    ElMessage.success(`欢迎，${user.displayName || user.username}`);
    const redirect = route.query.redirect && String(route.query.redirect);
    router.replace(redirect || '/workspace');
  } catch (e) {
    // 登录 401：http.js 拦截器对 /auth/login 故意不弹（避免覆盖其他提示），由本页处理
    // 后端 AUTH-40101 message="用户名或密码错误" 已被 http.js 挂到 e.message
    if (e?.response?.status === 401) {
      ElMessage.error(e?.message || '用户名或密码错误');
    } else if (!e?.response) {
      ElMessage.error(e?.message || '登录失败');
    }
    // 其他 4xx/5xx：http.js 拦截器已弹过 ElMessage.error
  } finally {
    loading.value = false;
  }
}

// === 统一认证登录：直接 302 跳后端 → UIAS 单点登录页 ===
function onUniAuthClick() {
  // 后端 /api/auth/uniauth/redirect 会拼出 UIAS URL 并 302 跳走
  window.location.href = '/api/auth/uniauth/redirect';
}

// 回调失败时（callback 端点 302 回 /#/login?error=...），登录页 mount 后弹错误提示
if (route.query.error) {
  const msg = route.query.error === 'uniauth-missing-user'
    ? '统一认证回调缺失用户身份参数（检查 user-param-name 配置）'
    : route.query.error === 'uniauth-failed'
      ? `统一认证失败：${route.query.msg || '未知错误'}`
      : `登录错误：${route.query.error}`;
  ElMessage.error(msg);
}
</script>

<style lang="scss" scoped>
.login-page {
  height: 100vh;
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 480px;
  background: $bg-page;
  overflow: hidden;
}

/* === 左侧品牌面板 === */
.brand {
  position: relative;
  background: linear-gradient(135deg, $primary 0%, $primary-400 60%, #2d6fd1 100%);
  color: #fff;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.bg-deco {
  position: absolute; inset: 0; pointer-events: none;
  .ring {
    position: absolute; border-radius: 50%;
    border: 1px solid rgba(255,255,255,.12);
  }
  .r1 { width: 520px; height: 520px; top: -160px; right: -180px; }
  .r2 { width: 360px; height: 360px; bottom: -120px; left: -120px; border-color: rgba(255,255,255,.08); }
  .r3 { width: 180px; height: 180px; top: 60%; right: 12%; border-color: rgba(255,255,255,.18); }
}
.brand-inner {
  position: relative;
  padding: 56px 64px;
  max-width: 540px;
  width: 100%;
}
.logo {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 56px;
  .mark {
    width: 36px; height: 36px; border-radius: 6px;
    background: rgba(255,255,255,.18);
    backdrop-filter: blur(6px);
    display: grid; place-items: center;
    color: #fff; font-size: 16px; font-weight: 700;
  }
  .logo-text { font-size: 15px; font-weight: 500; letter-spacing: .5px; opacity: .92; }
}
.title {
  font-size: 32px; line-height: 1.3;
  font-weight: 600; margin: 0 0 12px;
  letter-spacing: 1px;
}
.tagline {
  font-size: 13px; opacity: .6;
  margin: 0 0 48px;
  letter-spacing: 1px;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", system-ui, sans-serif;
}
.hl {
  list-style: none; padding: 0; margin: 0;
  li {
    font-size: 13.5px;
    padding: 10px 0;
    opacity: .9;
    display: flex; align-items: center; gap: 10px;
    .dot {
      width: 5px; height: 5px; border-radius: 50%;
      background: #93c5fd;
      box-shadow: 0 0 0 4px rgba(147,197,253,.18);
    }
  }
}
.copy {
  position: absolute; bottom: 32px; left: 64px;
  font-size: 11px; opacity: .5;
  letter-spacing: .5px;
}

/* === 右侧表单面板 === */
.form-wrap {
  background: #fff;
  display: grid;
  place-items: center;
  padding: 0 56px;
}
.form-card {
  width: 100%;
  max-width: 360px;
}
.form-h {
  margin-bottom: 36px;
  .welcome {
    font-size: 24px; font-weight: 600;
    color: $text-1;
    margin-bottom: 6px;
  }
  .hint {
    font-size: 13px;
    color: $text-3;
  }
}
.form {
  :deep(.el-form-item) { margin-bottom: 22px; }
  :deep(.el-input__wrapper) {
    box-shadow: none;
    border-bottom: 1px solid $border-2;
    border-radius: 0;
    padding-left: 0; padding-right: 0;
    transition: border-color .15s;
    &:hover { border-bottom-color: $primary-400; }
    &.is-focus { border-bottom-color: $primary; box-shadow: none; }
  }
  :deep(.el-input__inner) { font-size: 14px; height: 38px; }
  .ico { font-size: 14px; opacity: .65; margin-right: 6px; }
}
.btn-login {
  width: 100%;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 4px;
  background: linear-gradient(135deg, $primary 0%, $primary-400 100%);
  border: none;
  border-radius: 4px;
  margin-top: 8px;
  &:hover, &:focus {
    background: linear-gradient(135deg, $primary-400 0%, $primary 100%);
  }
}
.alt-login {
  margin-top: 14px;
  text-align: center;
  .divider {
    display: block;
    color: $text-3;
    font-size: 12px;
    margin: 12px 0;
    position: relative;
    &::before, &::after {
      content: '';
      position: absolute; top: 50%;
      width: 38%; height: 1px;
      background: $border-2;
    }
    &::before { left: 0; }
    &::after  { right: 0; }
  }
  .btn-uniauth {
    width: 100%; height: 40px;
    font-size: 13px;
    border: 1px solid $border-2;
    background: #fff;
    color: $text-1;
    border-radius: 4px;
    &:hover {
      border-color: $primary;
      color: $primary;
    }
  }
}
.uni-tip {
  background: $bg-soft;
  border-left: 3px solid $primary-400;
  padding: 8px 12px;
  font-size: 12px;
  color: $text-3;
  line-height: 1.6;
  margin-bottom: 16px;
  border-radius: 2px;
}
.tip {
  margin-top: 20px;
  font-size: 12px;
  color: $text-3;
  text-align: center;
  code {
    background: $bg-soft;
    padding: 1px 5px;
    border-radius: 3px;
    font-family: ui-monospace, monospace;
    font-size: 11.5px;
    color: $primary;
  }
}

/* 响应式：窄屏只保留右侧表单 */
@media (max-width: 900px) {
  .login-page { grid-template-columns: 1fr; }
  .brand { display: none; }
  .form-wrap { padding: 0 24px; }
}
</style>
