<template>
  <div class="login-container">
    <!-- Decorative background elements -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-circle bg-circle-3"></div>
    </div>

    <div class="login-content">
      <h1 class="system-title">红色引擎工程管理系统</h1>
      <p class="system-subtitle">西安分行 · 2026年度</p>

      <div class="login-card">
        <div class="card-header">
          <Star class="logo-icon" />
          <h2 class="card-title">用户登录</h2>
        </div>

        <el-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          class="login-form"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              size="large"
              clearable
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              size="large"
              show-password
              clearable
            />
          </el-form-item>

          <el-button
            type="primary"
            class="login-button"
            size="large"
            :loading="isLoading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <div class="demo-info">
          <p class="demo-title">演示账号</p>
          <div class="demo-accounts">
            <div class="account-row" @click="fillAccount('admin', 'admin123')">
              <span class="account-role">管理员</span>
              <span class="account-text">admin / admin123</span>
            </div>
            <div class="account-row" @click="fillAccount('wang', 'admin123')">
              <span class="account-role">组织审核</span>
              <span class="account-text">wang / admin123</span>
            </div>
            <div class="account-row" @click="fillAccount('zhangsh', 'admin123')">
              <span class="account-role">支部审核</span>
              <span class="account-text">zhangsh / admin123</span>
            </div>
            <div class="account-row" @click="fillAccount('xiaoli', 'admin123')">
              <span class="account-role">报送员</span>
              <span class="account-text">xiaoli / admin123</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 红色引擎（党建）登录页：移植自 redengine/red-engine-web/src/views/login/LoginView.vue
// 模板与 style 按任务要求原样保留（红色引擎视觉风格是本项目核心需求），仅 script 改为平台登录：
// - F2: 源工程 userStore.login() 内部走 JWT + localStorage token，本次移植全部删除，
//   改调平台 @/api/auth.js 的 login(username, password)（POST /api/auth/login），
//   登录态由 yiti session cookie 维持（@/api/http.js 已 withCredentials: true）
// - F3: 登录成功后跳转路径由源工程 /dashboard 改为 /redengine/dashboard
// - 参考 xanzc_frontend/src/views/login/Index.vue 137 行的做法：登录成功后把后端返回的
//   LoginRespDTO 写入平台 userStore，供 RedEngineLayout 顶栏展示 displayName / 后续鉴权判断
// 注：下方"演示账号"区块的 admin/wang/zhangsh/xiaoli 账号密码是源红色引擎工程（red-engine-server）
// 自带的种子数据，与平台 yiti 库账号体系无关；按"模板原样保留"要求本次未删改这些展示文案，
// 点击仅回填输入框，不会自动提交，用户仍需填入平台真实账号密码后点击"登录"。
//
// 审查返工（Finding 2）：用户名/密码输入框的 prefix-icon 由裸字符串 "User"/"Lock" 改为 :prefix-icon="User"/"Lock"
// （对象绑定）。源工程 main.js 对 @element-plus/icons-vue 做了全量 app.component 注册，裸字符串能被
// resolveDynamicComponent 按全局注册名解析成功；平台 main.js 未做这层全量注册，裸字符串会解析失败、图标不渲染
// （平台其它页面如 Dashboard.vue/Resources.vue/Users.vue/Metrics.vue 用到 prefix-icon 时也全部是 : 绑定写法）。
// 这是"模板逐字节保真"要求下唯一必要的偏离：保留文字但让图标在新环境里不可见，不是真正的保真，
// 故对这两处做最小必要改动（属性名/绑定符号不变，只把值从字符串字面量换成导入的图标组件对象）。
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { ElMessage } from 'element-plus';
import { Star, User, Lock } from '@element-plus/icons-vue';
import { login } from '@/api/auth';

const router = useRouter();
const userStore = useUserStore();

const formRef = ref(null);
const isLoading = ref(false);
const loginForm = ref({
  username: '',
  password: ''
});

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, max: 20, message: '密码长度在 4 到 20 个字符', trigger: 'blur' }
  ]
};

const fillAccount = (username, password) => {
  loginForm.value.username = username;
  loginForm.value.password = password;
};

// 登录：改调平台 session 登录（POST /api/auth/login），成功后写入 userStore 并进红色引擎工作台
const handleLogin = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
  } catch (e) {
    // 校验未通过：el-form 已自动展示错误提示
    return;
  }

  isLoading.value = true;

  try {
    const user = await login(loginForm.value.username, loginForm.value.password);
    userStore.setUser(user);
    ElMessage.success('登录成功');
    router.push('/redengine/dashboard');
  } catch (error) {
    console.error('Login error:', error);
    ElMessage.error(error?.message || '登录失败，请检查用户名和密码');
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped lang="scss">
.login-container {
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #7f1d1d 0%, #b91c1c 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
  font-family: 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

// Decorative background
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.03);
}

.bg-circle-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -100px;
}

.bg-circle-2 {
  width: 400px;
  height: 400px;
  bottom: -150px;
  left: -100px;
}

.bg-circle-3 {
  width: 300px;
  height: 300px;
  top: 50%;
  left: 10%;
  background: rgba(255, 255, 255, 0.02);
}

// Main content
.login-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 1;
  animation: fadeInUp 0.6s ease-out;
}

.system-title {
  font-size: 48px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 6px;
  margin-bottom: 8px;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.2);
}

.system-subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.7);
  letter-spacing: 4px;
  margin-bottom: 40px;
}

// Login card
.login-card {
  width: 420px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);

  .card-header {
    text-align: center;
    margin-bottom: 32px;

    .logo-icon {
      width: 36px;
      height: 36px;
      color: #fca5a5;
      margin-bottom: 8px;
      display: block;
      margin-left: auto;
      margin-right: auto;
    }

    :deep(svg) {
      width: 36px;
      height: 36px;
    }

    .card-title {
      font-size: 20px;
      font-weight: 600;
      color: #ffffff;
      letter-spacing: 2px;
    }
  }

  .login-form {
    :deep(.el-form-item) {
      margin-bottom: 22px;

      .el-form-item__error {
        color: #fca5a5;
      }

      .el-input__wrapper {
        background: rgba(255, 255, 255, 0.85);
        border: 1px solid rgba(255, 255, 255, 0.9);
        border-radius: 8px;
        box-shadow: none;
        transition: all 0.3s;
        padding: 1px 11px;

        &:hover {
          border-color: #f87171;
          background: rgba(255, 255, 255, 0.95);
        }

        &.is-focus {
          border-color: #dc2626;
          background: #ffffff;
        }

        input {
          color: #1e293b;
          caret-color: #1e293b;
          height: 40px;

          &::placeholder {
            color: rgba(100, 116, 139, 0.6);
          }

          // Override autofill background
          &:-webkit-autofill {
            -webkit-text-fill-color: #1e293b;
            transition: background-color 5000s ease-in-out 0s;
          }
        }

        .el-input__prefix,
        .el-input__suffix {
          font-size: 16px;

          .el-icon {
            font-size: 16px;
            width: 16px;
            height: 16px;
          }

          svg {
            width: 16px;
            height: 16px;
          }
        }

        .el-input__prefix .el-icon {
          color: #94a3b8;
        }

        .el-input__suffix .el-icon {
          color: #94a3b8;
        }
      }
    }

    .login-button {
      width: 100%;
      height: 48px;
      font-size: 16px;
      font-weight: 600;
      letter-spacing: 8px;
      border-radius: 8px;
      background: linear-gradient(135deg, #dc2626 0%, #ef4444 100%);
      border: none;
      box-shadow: 0 4px 16px rgba(220, 38, 38, 0.4);
      transition: all 0.3s;

      &:hover {
        background: linear-gradient(135deg, #b91c1c 0%, #dc2626 100%);
        box-shadow: 0 6px 24px rgba(220, 38, 38, 0.5);
        transform: translateY(-1px);
      }

      &:active {
        transform: translateY(0);
      }
    }
  }

  .demo-info {
    margin-top: 28px;
    padding-top: 24px;
    border-top: 1px solid rgba(255, 255, 255, 0.1);

    .demo-title {
      margin: 0 0 12px;
      font-size: 12px;
      font-weight: 500;
      color: rgba(255, 255, 255, 0.5);
      text-align: center;
      letter-spacing: 2px;
    }

    .demo-accounts {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .account-row {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px 12px;
        background: rgba(255, 255, 255, 0.06);
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover {
          background: rgba(255, 255, 255, 0.12);
        }

        .account-role {
          font-size: 11px;
          color: #fca5a5;
          background: rgba(252, 165, 165, 0.1);
          padding: 2px 8px;
          border-radius: 4px;
          white-space: nowrap;
        }

        .account-text {
          font-size: 13px;
          color: rgba(255, 255, 255, 0.65);
          font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace;
        }
      }
    }
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// Responsive
@media (max-width: 480px) {
  .system-title {
    font-size: 28px;
    letter-spacing: 3px;
  }

  .system-subtitle {
    font-size: 13px;
  }

  .login-card {
    width: calc(100vw - 32px);
    padding: 28px 24px;
  }
}
</style>
