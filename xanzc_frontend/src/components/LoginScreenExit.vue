<template>
  <button v-if="visible" class="login-screen-exit" type="button" data-testid="login-screen-exit" :disabled="loading" @click="close">
    {{ loading ? '正在返回工作台…' : '进入工作台' }}
  </button>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMenuStore } from '@/stores/menu';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const menuStore = useMenuStore();
const userStore = useUserStore();
const loading = ref(false);
const generation = ref(0);
const visible = computed(() => route.query?.from === 'login'
  && ['PersonalDashboard', 'CodeScreenPage', 'BranchOperatingPage'].includes(String(route.name || '')));
let disposed = false;

watch(() => [userStore.user, route.name, route.query?.from], () => { generation.value += 1; loading.value = false; });

async function close() {
  if (!visible.value || loading.value || disposed) return;
  const token = ++generation.value;
  const user = userStore.user;
  loading.value = true;
  try {
    await menuStore.load(true);
    if (disposed || token !== generation.value || !user || userStore.user !== user) return;
    const target = menuStore.resolveLandingPath('/workspace') || '/no-access';
    await router.replace(target);
  } catch (error) {
    if (Number(error?.response?.status || error?.status) === 401) return;
    if (!disposed && token === generation.value && user && userStore.user === user) await router.replace('/no-access');
  } finally {
    if (!disposed && token === generation.value) loading.value = false;
  }
}

onBeforeUnmount(() => { disposed = true; generation.value += 1; });
</script>

<style scoped>
.login-screen-exit { position: fixed; right: 24px; bottom: 24px; z-index: 1200; min-height: 38px; padding: 0 16px; border: 1px solid rgba(127, 199, 255, .55); border-radius: 6px; color: #eaf2ff; background: rgba(7, 16, 44, .94); box-shadow: 0 8px 24px rgba(0, 0, 0, .28); cursor: pointer; }
.login-screen-exit:disabled { opacity: .72; cursor: wait; }
@media (max-width: 600px) { .login-screen-exit { right: 12px; bottom: 12px; width: calc(100% - 24px); } }
</style>
