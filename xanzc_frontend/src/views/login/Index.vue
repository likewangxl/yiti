<template>
  <div class="login-page">
    <aside class="brand" aria-labelledby="brand-title">
      <div class="bg-deco" aria-hidden="true">
        <span class="ring r1"></span>
        <span class="ring r2"></span>
        <span class="ring r3"></span>
      </div>
      <div class="brand-inner">
        <div class="logo" aria-label="银行营销平台">
          <span class="mark" aria-hidden="true">银</span>
          <span class="logo-text">银行营销平台</span>
        </div>
        <h1 id="brand-title" class="brand-title">银行营销 · 业务执行 · 绩效平台</h1>
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

    <main class="form-wrap" aria-labelledby="login-title">
      <div class="form-card">
        <header class="form-h">
          <p class="eyebrow">BRANCH PLATFORM / ACCESS</p>
          <h2 id="login-title" class="login-title">欢迎登录</h2>
          <p class="hint">{{ showNormal ? '请使用账号登录后台' : '请使用统一认证登录' }}</p>
        </header>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          class="form"
          aria-labelledby="login-title"
          @keyup.enter="onSubmit"
        >
          <div v-if="showNormal" class="normal-login">
            <el-form-item prop="username">
              <label class="field-label" for="login-username">用户名</label>
              <el-input
                id="login-username"
                v-model="form.username"
                placeholder="请输入用户名"
                clearable
                autocomplete="username"
                aria-required="true"
              >
                <template #prefix>
                  <span class="field-icon field-icon--user" aria-hidden="true"></span>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <label class="field-label" for="login-password">密码</label>
              <el-input
                id="login-password"
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                show-password
                autocomplete="current-password"
                aria-required="true"
              >
                <template #prefix>
                  <span class="field-icon field-icon--lock" aria-hidden="true"></span>
                </template>
              </el-input>
            </el-form-item>

            <el-button
              type="primary"
              class="btn-login"
              :loading="loading"
              :disabled="loading"
              native-type="button"
              @click="onSubmit"
            >
              {{ loading ? '登录中...' : '登 录' }}
            </el-button>
          </div>

          <div v-if="showUias" class="uias-login">
            <div class="uias-intro" role="note">
              <span class="uias-icon" aria-hidden="true"></span>
              <p>使用统一身份认证安全访问平台，登录后将返回工作台。</p>
            </div>
            <!-- 统一认证登录入口：直接 302 到后端 /api/auth/uniauth/redirect → UIAS 单点登录页 -->
            <el-button class="btn-uniauth" native-type="button" @click="onUniAuthClick">
              <span class="button-icon button-icon--arrow" aria-hidden="true"></span>
              <span>统一认证登录</span>
            </el-button>
          </div>
        </el-form>

      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { login } from '@/api/auth';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';

const router = useRouter();
const route  = useRoute();
const store  = useUserStore();

// 通过 URL 区分登录方式：
//   默认（不带后缀）→ 统一认证(UIAS)登录    http://localhost:8090/#/login
//   ?normal        → 普通账号密码登录        http://localhost:8090/#/login?normal
//   （兼容旧写法 ?mode=normal）
const isNormal = computed(() =>
  route.query.normal !== undefined || String(route.query.mode || '').toLowerCase() === 'normal');
const showNormal = computed(() => isNormal.value);
const showUias   = computed(() => !isNormal.value);

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
  if (loading.value || !showNormal.value) return;   // 仅 UIAS 模式下回车不触发普通登录
  try {
    await formRef.value.validate();
  } catch (_) { return; }

  loading.value = true;
  try {
    const user = await login(form.username, form.password);
    store.setUser(user);
    // 强制重拉当前用户菜单：session 过期被守卫踢回 /login 等未整页刷新的路径下，
    // menu store 仍缓存着上一个用户的菜单（loaded=true 时 load() 默认跳过）
    const menuStore = useMenuStore();
    let landingPath = '/no-access';
    try {
      await menuStore.load(true);
      landingPath = menuStore.resolveLandingPath(route.query.redirect && String(route.query.redirect));
    } catch (_) {
      // 登录已成功，但授权菜单不可用时 fail-close 到说明页，不能沿用上一用户菜单。
    }
    ElMessage.success(`欢迎，${user.displayName || user.username}`);
    router.replace(landingPath);
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
  grid-template-columns: minmax(0, 1fr) 520px;
  background: var(--color-page);
  color: var(--color-text-strong);
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei",
    "Noto Sans CJK SC", "Source Han Sans SC", sans-serif;
}

/* === 左侧品牌面板 === */
.brand {
  position: relative;
  background: linear-gradient(135deg, var(--color-sidebar-bg) 0%, var(--color-brand-700) 62%, var(--color-brand-500) 100%);
  color: var(--color-surface);
  overflow: hidden;
  display: flex;
  align-items: stretch;
  justify-content: center;
}

.bg-deco {
  position: absolute;
  inset: 0;
  pointer-events: none;

  .ring {
    position: absolute;
    border-radius: 50%;
    border: 1px solid color-mix(in srgb, var(--color-surface) 14%, transparent);
  }

  .r1 { width: 520px; height: 520px; top: -160px; right: -180px; }
  .r2 {
    width: 360px;
    height: 360px;
    bottom: -120px;
    left: -120px;
    border-color: color-mix(in srgb, var(--color-surface) 9%, transparent);
  }
  .r3 {
    width: 180px;
    height: 180px;
    top: 60%;
    right: 12%;
    border-color: color-mix(in srgb, var(--color-surface) 18%, transparent);
  }
}

.brand-inner {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  max-width: 680px;
  min-height: 100%;
  padding: 64px clamp(48px, 6vw, 88px);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 56px;

  .mark {
    width: 36px;
    height: 36px;
    border: 1px solid color-mix(in srgb, var(--color-surface) 25%, transparent);
    border-radius: var(--radius-control);
    background: color-mix(in srgb, var(--color-surface) 14%, transparent);
    display: grid;
    place-items: center;
    color: var(--color-surface);
    font-size: 16px;
    font-weight: 700;
  }

  .logo-text {
    font-size: 15px;
    font-weight: 500;
    letter-spacing: .5px;
    opacity: .92;
  }
}

.brand-title {
  max-width: 620px;
  margin: 0 0 12px;
  font-size: 32px;
  line-height: 1.3;
  font-weight: 600;
  letter-spacing: 1px;
}

.tagline {
  margin: 0 0 48px;
  color: color-mix(in srgb, var(--color-surface) 72%, transparent);
  font-size: 13px;
  letter-spacing: 1px;
}

.hl {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 0;
    font-size: 14px;
    line-height: 22px;
    color: color-mix(in srgb, var(--color-surface) 92%, transparent);
  }

  .dot {
    flex: 0 0 auto;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-brand-100);
    box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-brand-100) 18%, transparent);
  }
}

.copy {
  position: absolute;
  bottom: 32px;
  left: clamp(48px, 6vw, 88px);
  color: color-mix(in srgb, var(--color-surface) 64%, transparent);
  font-size: 12px;
  letter-spacing: .5px;
}

/* === 右侧表单面板 === */
.form-wrap {
  box-sizing: border-box;
  min-width: 0;
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  display: grid;
  place-items: center;
  padding: 48px 72px;
}

.form-card {
  width: 100%;
  max-width: 392px;
}

.form-h {
  margin-bottom: 32px;

  .eyebrow {
    margin: 0 0 12px;
    color: var(--color-brand-500);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 1.2px;
  }

  .login-title {
    margin: 0 0 8px;
    color: var(--color-text-strong);
    font-size: 30px;
    line-height: 40px;
    font-weight: 600;
  }

  .hint {
    margin: 0;
    color: var(--color-text-muted);
    font-size: 14px;
    line-height: 22px;
  }
}

.form {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  .field-label {
    display: block;
    margin-bottom: 8px;
    color: var(--color-text-strong);
    font-size: 14px;
    line-height: 22px;
    font-weight: 500;
  }

  :deep(.el-input) {
    width: 100%;
  }

  :deep(.el-input__wrapper) {
    min-height: 44px;
    box-sizing: border-box;
    padding: 0 12px;
    border: 1px solid var(--color-border-strong);
    border-radius: var(--radius-control);
    background: var(--color-surface-soft);
    box-shadow: none;
    transition: border-color var(--motion-fast) var(--ease-enter),
      box-shadow var(--motion-fast) var(--ease-enter),
      background-color var(--motion-fast) var(--ease-enter);

    &:hover {
      border-color: var(--color-brand-500);
      background: var(--color-surface);
    }

    &.is-focus {
      border-color: var(--color-focus);
      background: var(--color-surface);
      box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-focus) 18%, transparent);
    }
  }

  :deep(.el-input__inner) {
    height: 42px;
    color: var(--color-text-strong);
    font-size: 14px;
  }
}

.field-icon,
.button-icon,
.uias-icon {
  display: inline-block;
  position: relative;
  flex: 0 0 auto;
  color: var(--color-text-muted);
}

.field-icon {
  width: 16px;
  height: 16px;
  margin-right: 8px;
}

.field-icon--user::before {
  content: '';
  position: absolute;
  top: 0;
  left: 5px;
  width: 6px;
  height: 6px;
  border: 1.5px solid currentColor;
  border-radius: 50%;
}

.field-icon--user::after {
  content: '';
  position: absolute;
  right: 1px;
  bottom: 0;
  left: 1px;
  height: 7px;
  border: 1.5px solid currentColor;
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.field-icon--lock::before {
  content: '';
  position: absolute;
  top: 0;
  left: 4px;
  width: 7px;
  height: 8px;
  border: 1.5px solid currentColor;
  border-bottom: 0;
  border-radius: 6px 6px 0 0;
}

.field-icon--lock::after {
  content: '';
  position: absolute;
  right: 1px;
  bottom: 0;
  left: 1px;
  height: 9px;
  border: 1.5px solid currentColor;
  border-radius: 2px;
}

.btn-login,
.btn-uniauth {
  width: 100%;
  min-height: 44px;
  border-radius: var(--radius-control);
  font-size: 14px;
  font-weight: 600;
  transition: background-color var(--motion-fast) var(--ease-enter),
    border-color var(--motion-fast) var(--ease-enter),
    color var(--motion-fast) var(--ease-enter),
    box-shadow var(--motion-fast) var(--ease-enter);
}

.btn-login {
  margin-top: 4px;
  border: 1px solid var(--color-brand-700);
  background: linear-gradient(135deg, var(--color-brand-700), var(--color-brand-500));
  color: var(--color-surface);
  letter-spacing: 4px;

  &:hover {
    border-color: var(--color-brand-500);
    background: var(--color-brand-500);
  }

  &:active {
    background: var(--color-brand-700);
  }

  &.is-disabled,
  &:disabled {
    border-color: var(--color-border-strong);
    background: var(--color-border-strong);
    color: var(--color-surface);
  }
}

.uias-login {
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-soft);
}

.uias-intro {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;

  p {
    margin: 0;
    color: var(--color-text);
    font-size: 14px;
    line-height: 22px;
  }
}

.uias-icon {
  width: 22px;
  height: 24px;
  margin-top: 1px;
  border: 1.5px solid var(--color-brand-500);
  border-radius: 5px 5px 9px 9px;
  background: var(--color-brand-100);
}

.uias-icon::after {
  content: '';
  position: absolute;
  top: 6px;
  right: 5px;
  bottom: 5px;
  left: 5px;
  border: 1px solid var(--color-brand-500);
  border-radius: 50%;
}

.btn-uniauth {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid var(--color-brand-700);
  background: var(--color-surface);
  color: var(--color-brand-700);

  &:hover {
    border-color: var(--color-brand-500);
    background: var(--color-brand-100);
    color: var(--color-brand-700);
  }
}

.button-icon--arrow {
  width: 14px;
  height: 14px;
  border: 1.5px solid currentColor;
  border-radius: 50%;
}

.button-icon--arrow::after {
  content: '';
  position: absolute;
  top: 5px;
  left: 3px;
  width: 5px;
  height: 5px;
  border-top: 1.5px solid currentColor;
  border-right: 1.5px solid currentColor;
  transform: rotate(45deg);
}

:deep(button:focus-visible),
:deep(input:focus-visible) {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .login-page *,
  .login-page *::before,
  .login-page *::after {
    transition-duration: .01ms !important;
    animation-duration: .01ms !important;
    animation-iteration-count: 1 !important;
  }
}
</style>
