<template>
  <div ref="pageRef" class="personal-dashboard-page">
    <PersonalDashboard
      :model="model"
      :loading="loading"
      @refresh="refresh"
      @back="goBack"
      @navigate="navigateToItem"
      @fullscreen="enterFullscreen"
    />

    <MarketingCustomerDetailDrawer
      v-model="customerDrawerVisible"
      :customer="selectedCustomer"
      :loading="customerLoading"
    />
    <TouchTaskDetailDialog
      v-model="touchDialogVisible"
      :task-id="touchTaskId"
      :mode="touchTaskMode"
      :allow-write="true"
      @changed="refresh"
    />

    <div v-if="progressChooserVisible" ref="progressChooserRef" class="personal-progress-chooser" role="dialog" aria-modal="true" aria-label="选择业务进度入口" tabindex="-1" @keydown="handleChooserKeydown">
      <div class="personal-progress-chooser__card" role="document">
        <h2>选择业务进度</h2>
        <p>请选择要查看的业务来源。</p>
        <div class="personal-progress-chooser__actions">
          <button type="button" data-choice="asset" @click="openProgressCollection('asset')">资产立项</button>
          <button type="button" data-choice="support" @click="openProgressCollection('support')">中台支持</button>
          <button type="button" data-choice="cancel" @click="progressChooserVisible = false">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import http, { API_BASE } from '@/api/http';
import {
  buildPersonalDashboardModel,
  loadPersonalDashboard
} from '@/api/personalDashboard';
import { useUserStore } from '@/stores/user';
import { useMenuStore } from '@/stores/menu';
import { usePermissionStore } from '@/stores/permission';
import MarketingCustomerDetailDrawer from '@/components/MarketingCustomerDetailDrawer.vue';
import TouchTaskDetailDialog from '@/components/TouchTaskDetailDialog.vue';
import PersonalDashboard from './PersonalDashboard.vue';

const router = useRouter();
const userStore = useUserStore();
const menuStore = useMenuStore();
const permissionStore = usePermissionStore();

const pageRef = ref(null);
const loading = ref(false);
const generation = ref(0);
const model = ref(emptyModel(identityFromStore()));
const customerDrawerVisible = ref(false);
const customerLoading = ref(false);
const selectedCustomer = ref(null);
const touchDialogVisible = ref(false);
const touchTaskId = ref('');
const touchTaskMode = ref('view');
const progressChooserVisible = ref(false);
const progressChooserRef = ref(null);
const customerRequestGeneration = ref(0);
let expectedStoreEmpId = '';

function identityFromStore() {
  return {
    name: userStore.displayName || '',
    orgName: userStore.orgName || ''
  };
}

function emptySection() {
  return { status: 'empty', message: '', total: null, items: [] };
}

function emptyModel(identity = {}) {
  return {
    identity: { name: identity.name || '', orgName: identity.orgName || '' },
    metrics: emptySection(),
    priorities: emptySection(),
    customers: emptySection(),
    progress: emptySection(),
    refreshedAt: null
  };
}

function formattedNow() {
  const value = new Date();
  const pad = number => String(number).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
}

function authErrorModel(error) {
  const status = error?.response?.status ?? error?.status ?? error?.code;
  const message = Number(status) === 403
    ? '当前账号暂无个人驾驶舱权限'
    : error?.message || '当前会话身份确认失败';
  return {
    identity: identityFromStore(),
    metrics: { status: Number(status) === 403 ? 'forbidden' : 'error', message, items: [], total: null },
    priorities: { status: 'error', message: '当前会话未确认，未加载个人事项', items: [], total: null },
    customers: { status: 'error', message: '当前会话未确认，未加载客户数据', items: [], total: null },
    progress: { status: 'error', message: '当前会话未确认，未加载业务数据', items: [], total: null },
    refreshedAt: null
  };
}

function clearPresentationState() {
  customerRequestGeneration.value += 1;
  model.value = emptyModel(identityFromStore());
  customerDrawerVisible.value = false;
  customerLoading.value = false;
  selectedCustomer.value = null;
  touchDialogVisible.value = false;
  touchTaskId.value = '';
  touchTaskMode.value = 'view';
  progressChooserVisible.value = false;
}

function isForbidden(error) {
  const status = error?.response?.status ?? error?.status ?? error?.code;
  return Number(status) === 403 || String(status).toUpperCase() === 'FORBIDDEN';
}

async function confirmCurrentUser() {
  const current = await http.get(`${API_BASE}/auth/current-user`, { silent: true });
  if (!current?.empId) throw new Error('当前会话未返回员工身份');
  return current;
}

async function load() {
  const currentGeneration = ++generation.value;
  loading.value = true;
  clearPresentationState();
  try {
    const current = await confirmCurrentUser();
    if (currentGeneration !== generation.value) return;
    const previousEmpId = userStore.user?.empId;
    if (!previousEmpId || String(previousEmpId) !== String(current.empId)) {
      expectedStoreEmpId = String(current.empId);
      userStore.setUser(current);
      // 同工号刷新不清空既有授权快照；换工号必须重载并确认工作台菜单仍存在。
      if (previousEmpId) {
        await Promise.all([
          menuStore.load(true),
          permissionStore.load(true)
        ]);
        if (!menuStore.hasUrl('/workspace')) throw new Error('当前账号未授权工作台');
      }
    }
    if (currentGeneration !== generation.value) return;
    const identity = {
      name: current.displayName || '',
      orgName: current.mainOrgName || ''
    };
    const sources = await loadPersonalDashboard();
    if (currentGeneration !== generation.value) return;
    model.value = {
      ...buildPersonalDashboardModel(sources, identity),
      refreshedAt: formattedNow()
    };
  } catch (error) {
    if (currentGeneration !== generation.value) return;
    model.value = authErrorModel(error);
    if (isForbidden(error)) ElMessage.warning('当前账号暂无个人驾驶舱权限');
  } finally {
    if (currentGeneration === generation.value) loading.value = false;
  }
}

function refresh() {
  return load();
}

function goBack() {
  router.push('/workspace');
}

async function openCustomer(id) {
  if (id === null || id === undefined || id === '') return;
  const requestGeneration = ++customerRequestGeneration.value;
  const pageGeneration = generation.value;
  selectedCustomer.value = null;
  customerDrawerVisible.value = true;
  customerLoading.value = true;
  try {
    const customer = await http.get(
      `${API_BASE}/marketing/customers/${encodeURIComponent(String(id))}`,
      { silent: true }
    );
    if (requestGeneration !== customerRequestGeneration.value || pageGeneration !== generation.value) return;
    selectedCustomer.value = customer;
  } catch (error) {
    if (requestGeneration !== customerRequestGeneration.value || pageGeneration !== generation.value) return;
    customerDrawerVisible.value = false;
    ElMessage[isForbidden(error) ? 'warning' : 'error'](
      isForbidden(error) ? '当前账号暂无客户详情权限' : (error?.message || '客户详情加载失败')
    );
  } finally {
    if (requestGeneration === customerRequestGeneration.value && pageGeneration === generation.value) {
      customerLoading.value = false;
    }
  }
}

function openTouch(target) {
  if (target?.id === null || target?.id === undefined || target?.id === '') return;
  touchTaskId.value = String(target.id);
  touchTaskMode.value = ['handle', 'supplement', 'view'].includes(target.mode) ? target.mode : 'view';
  touchDialogVisible.value = true;
}

function workflowRoute(target) {
  const taskId = target?.taskId || target?.id;
  const bizType = String(target?.bizType || '').toUpperCase();
  if (!taskId) return null;
  if (bizType === 'ASSET_PROJECT') {
    const assetId = target.bizId || (String(target.businessKey || '').startsWith('ASSET_PROJECT:')
      ? String(target.businessKey).slice('ASSET_PROJECT:'.length) : '');
    return assetId ? { path: `/marketing/asset-projects/${encodeURIComponent(String(assetId))}`, query: { tab: 'PENDING', taskId } } : null;
  }
  if (bizType === 'SUPPORT' || bizType === 'SUPPORT_DEPT') {
    const supportId = target.bizId || (String(target.businessKey || '').startsWith('SUPPORT:')
      ? String(target.businessKey).slice('SUPPORT:'.length) : '');
    return supportId ? { path: `/bizexec/supports/${encodeURIComponent(String(supportId))}`, query: { tab: 'DEPT_TODO', taskId } } : null;
  }
  if (bizType === 'TARGET_ADJUST') return { path: '/perf/targets', query: { tab: 'todo', taskId } };
  if (bizType === 'ALLOC_ADJUST') return { path: '/perf/adjust', query: { tab: 'todo', taskId, action: 'open' } };
  return null;
}

function openProgressCollection(kind) {
  progressChooserVisible.value = false;
  if (kind === 'asset') router.push('/marketing/asset-projects');
  if (kind === 'support') router.push('/bizexec/supports');
}

function navigateToCollection(kind) {
  if (kind === 'todos') return router.push('/workspace');
  if (kind === 'customers') return router.push('/customers/list');
  if (kind === 'progress') {
    progressChooserVisible.value = true;
    return;
  }
  return undefined;
}

function navigateToItem(target = {}) {
  const kind = String(target.kind || '').toLowerCase();
  if (kind === 'todos' || kind === 'customers' || kind === 'progress') return navigateToCollection(kind);
  if (kind === 'customer') return openCustomer(target.id);
  if (kind === 'touch') return openTouch(target);
  if (kind === 'asset' && target.id !== null && target.id !== undefined && target.id !== '') {
    return router.push(`/marketing/asset-projects/${encodeURIComponent(String(target.id))}`);
  }
  if (kind === 'support' && target.id !== null && target.id !== undefined && target.id !== '') {
    return router.push(`/bizexec/supports/${encodeURIComponent(String(target.id))}`);
  }
  if (kind === 'todo') {
    const route = workflowRoute(target);
    return route ? router.push(route) : router.push('/workspace');
  }
  return undefined;
}

async function enterFullscreen() {
  const documentElement = document.documentElement;
  if (document.fullscreenElement) {
    if (typeof document.exitFullscreen === 'function') {
      try { await document.exitFullscreen(); } catch { ElMessage.info('无法退出全屏显示'); }
    }
    return;
  }
  if (!documentElement || typeof documentElement.requestFullscreen !== 'function') {
    ElMessage.info('当前浏览器不支持全屏显示');
    return;
  }
  try {
    await documentElement.requestFullscreen();
  } catch {
    ElMessage.info('无法进入全屏显示，请使用浏览器全屏控件');
  }
}

function focusChooser() {
  progressChooserRef.value?.querySelector('button')?.focus();
}

function handleChooserKeydown(event) {
  if (event.key === 'Escape') {
    progressChooserVisible.value = false;
    return;
  }
  if (event.key !== 'Tab') return;
  const buttons = [...(progressChooserRef.value?.querySelectorAll('button') || [])];
  if (!buttons.length) return;
  const index = buttons.indexOf(document.activeElement);
  const next = event.shiftKey
    ? (index <= 0 ? buttons.length - 1 : index - 1)
    : (index === buttons.length - 1 ? 0 : index + 1);
  event.preventDefault();
  buttons[next].focus();
}

watch(() => userStore.user?.empId, (next, previous) => {
  if (!next) {
    expectedStoreEmpId = '';
    generation.value += 1;
    loading.value = false;
    clearPresentationState();
    return;
  }
  if (next === previous) return;
  if (expectedStoreEmpId && String(next) === expectedStoreEmpId) {
    expectedStoreEmpId = '';
    return;
  }
  load();
});

watch(progressChooserVisible, visible => {
  if (visible) requestAnimationFrame(focusChooser);
});

onMounted(load);
onBeforeUnmount(() => {
  expectedStoreEmpId = '';
  generation.value += 1;
  clearPresentationState();
});

defineExpose({
  model,
  loading,
  refresh,
  navigateToItem,
  touchTaskId,
  touchTaskMode,
  progressChooserVisible
});
</script>

<style scoped>
.personal-dashboard-page { min-height: 100vh; background: #030b1c; }
.personal-progress-chooser { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 20px; background: rgb(1 8 22 / 72%); }
.personal-progress-chooser__card { width: min(420px, 100%); padding: 24px; color: #f2f7ff; background: #0a1e45; border: 1px solid rgb(110 170 241 / 42%); border-radius: 12px; box-shadow: 0 20px 60px rgb(0 0 0 / 35%); }
.personal-progress-chooser__card h2 { margin: 0 0 8px; }
.personal-progress-chooser__card p { margin: 0 0 20px; color: #bfd2ef; }
.personal-progress-chooser__actions { display: flex; flex-wrap: wrap; gap: 10px; }
.personal-progress-chooser__actions button { padding: 9px 14px; color: #effcff; background: #123d72; border: 1px solid rgb(75 229 236 / 48%); border-radius: 7px; cursor: pointer; }
.personal-progress-chooser__actions button[data-choice="cancel"] { margin-left: auto; color: #bfd2ef; background: transparent; border-color: rgb(110 170 241 / 30%); }
</style>
