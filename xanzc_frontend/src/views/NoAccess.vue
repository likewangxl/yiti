<template>
  <main class="no-access">
    <div class="no-access__card">
      <div class="no-access__code">403</div>
      <h1>暂无可访问功能</h1>
      <p>可能尚未分配菜单，或权限信息加载失败。你可以重新加载权限，仍无法进入时请联系系统管理员。</p>
      <div class="no-access__actions">
        <el-button type="primary" :loading="retrying" @click="retry">重新加载权限</el-button>
        <el-button @click="exitLogin">退出登录</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { logout } from '@/api/auth';
import { useMenuStore } from '@/stores/menu';
import { useUserStore } from '@/stores/user';
import { resetAuthorizationSnapshots } from '@/stores/authorizationSnapshot';

const router = useRouter();
const menuStore = useMenuStore();
const userStore = useUserStore();
const retrying = ref(false);

async function retry() {
  if (retrying.value) return;
  retrying.value = true;
  try {
    resetAuthorizationSnapshots();
    await menuStore.load();
    const landingPath = menuStore.resolveLandingPath();
    if (landingPath === '/no-access') {
      ElMessage.warning('当前账号仍没有可访问菜单，请联系系统管理员');
      return;
    }
    router.replace(landingPath);
  } catch (_) {
    ElMessage.error('权限信息加载失败，请稍后重试');
  } finally {
    retrying.value = false;
  }
}

async function exitLogin() {
  try { await logout(); } catch (_) { /* 本地会话仍需清理 */ }
  userStore.clear();
  window.location.replace('/#/login');
}
</script>

<style scoped lang="scss">
.no-access {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: $bg-page;
}
.no-access__card {
  width: min(440px, 100%);
  padding: 48px 36px;
  border: 1px solid $border-1;
  border-radius: 12px;
  background: #fff;
  text-align: center;
  box-shadow: 0 12px 32px rgba(15, 23, 42, .08);
}
.no-access__code {
  color: $primary;
  font-size: 52px;
  font-weight: 700;
  line-height: 1;
}
h1 { margin: 18px 0 8px; color: $text-1; font-size: 22px; }
p { margin: 0; color: $text-3; font-size: 14px; line-height: 1.7; }
.no-access__actions {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}
</style>
