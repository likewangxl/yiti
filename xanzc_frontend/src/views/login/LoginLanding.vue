<template>
  <main class="login-landing" data-testid="login-landing" aria-live="polite">
    <section v-if="loading" class="login-landing__card"><h1>正在准备经营大屏</h1><p>正在确认当前账号的角色与大屏权限…</p></section>
    <section v-else class="login-landing__card" role="alert">
      <h1>已进入工作台</h1><p>{{ message }}</p>
    </section>
    <button type="button" data-action="login-landing-workspace" @click="goWorkspace">进入工作台</button>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getScreenView, listAvailableScreens } from '@/api/screen';
import { getLoginScreenRules } from '@/api/loginScreen';
import { useMenuStore } from '@/stores/menu';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { markLoginScreenShown, resolveLoginScreen } from '@/utils/loginScreen';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const menuStore = useMenuStore();
const permissionStore = usePermissionStore();
const loading = ref(true);
const message = ref('当前账号未配置可用的默认大屏。');
let generation = 0;

function workspace() {
  return menuStore.resolveLandingPath();
}
async function goWorkspace() {
  ++generation;
  const user = userStore.user;
  try { await menuStore.load(); } catch (error) {
    if (Number(error?.response?.status || error?.status) === 401) { userStore.clear?.(); router.replace('/login'); return; }
    if (userStore.user === user && user) router.replace('/no-access'); return;
  }
  if (userStore.user !== user || !user) return;
  markLoginScreenShown(user);
  router.replace(workspace());
}

async function open() {
  const token = ++generation;
  const user = userStore.user;
  const isCurrent = () => token === generation && Boolean(user) && userStore.user === user;
  try {
    await menuStore.load();
    if (!isCurrent()) return;
    markLoginScreenShown(user);
    const redirect = typeof route.query?.redirect === 'string' ? route.query.redirect : '';
    if (redirect && !['/', '/workspace', '/login-landing'].includes(redirect.split(/[?#]/)[0]) && menuStore.isAuthorizedRedirect(redirect)) {
      router.replace(redirect); return;
    }
    await permissionStore.load();
    if (!isCurrent()) return;
    if (!permissionStore.canAccess('/api/sys/login-screens')) { router.replace(workspace()); return; }
    const payload = await getLoginScreenRules();
    if (!isCurrent()) return;
    const destination = await resolveLoginScreen({ user, payload, menuStore, permissionStore,
      listAvailableScreens, getScreenView, isCurrent });
    if (isCurrent() && destination) router.replace(destination);
  } catch (error) {
    if (!isCurrent()) return;
    if (Number(error?.response?.status || error?.status) === 401) { userStore.clear?.(); router.replace('/login'); return; }
    message.value = '默认大屏暂不可用，请从工作台进入已授权功能。';
    await goWorkspace();
  } finally {
    if (isCurrent()) loading.value = false;
  }
}
onMounted(open);
onBeforeUnmount(() => { ++generation; });
</script>

<style scoped>
.login-landing { min-height: 100vh; display: grid; place-items: center; background: var(--color-page); color: var(--color-text-strong); }
.login-landing__card { width: min(420px, calc(100% - 40px)); padding: 32px; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-surface); text-align: center; }
.login-landing__card button { min-height: 40px; padding: 0 18px; border: 0; border-radius: 6px; color: #fff; background: var(--color-brand-700); cursor: pointer; }
</style>
