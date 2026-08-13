<template>
  <main class="no-access" aria-labelledby="no-access-title">
    <div class="no-access__card">
      <div class="no-access__code">403</div>
      <h1 id="no-access-title">暂无可访问功能</h1>
      <p role="status" aria-live="polite">可能尚未分配菜单，或权限信息加载失败。你可以重新加载权限，仍无法进入时请联系系统管理员。</p>
      <div class="no-access__actions" role="group" aria-label="权限恢复操作">
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
  padding: var(--space-8) var(--space-6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-surface);
  text-align: center;
  box-shadow: var(--shadow-surface);
}
.no-access__code {
  color: var(--color-brand-700);
  font-size: 48px;
  font-weight: 700;
  line-height: 1;
}
h1 { margin: var(--space-4) 0 var(--space-2); color: var(--color-text-strong); font-size: 22px; }
p { margin: 0; color: var(--color-text-muted); font-size: 14px; line-height: 1.7; }
.no-access__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: center;
  margin-top: var(--space-6);
}
</style>
