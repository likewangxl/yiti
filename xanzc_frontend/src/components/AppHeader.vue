<template>
  <header class="hdr">
    <WorkspaceTabs />

    <button
      v-if="screenCenterAvailable"
      type="button"
      class="screen-center-shortcut"
      data-testid="screen-center-shortcut"
      aria-label="大屏中心"
      title="大屏中心"
      @click="$router.push({ name: 'ScreenCenter' })"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
        <rect x="3" y="4" width="18" height="13" rx="1.5" />
        <path d="M8 20h8M12 17v3M7 8h10M7 12h6" />
      </svg>
      <span>大屏中心</span>
    </button>

    <el-dropdown trigger="click" @command="onCommand">
      <div class="account-pick">
        <div class="avatar">{{ avatarLetter }}</div>
        <div class="meta">
          <b>{{ store.displayName }}</b>
          <small>{{ store.orgName || store.roleSummary || '—' }}</small>
        </div>
        <span class="account-caret" aria-hidden="true">▾</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item disabled>
            <span style="color:#9CA3AF;font-size:12px">{{ store.user?.username }} · {{ store.user?.deptNo || store.user?.mainOrgCode || '—' }}</span>
          </el-dropdown-item>
          <el-dropdown-item divided command="changePassword">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <button
      type="button"
      class="icon-btn"
      aria-label="通知中心"
      title="通知中心"
      @click="$router.push('/system/notifications')"
    >
      <el-badge :value="unread" :max="99" :hidden="!unread">
        <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
          <path d="M18 10a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 22h4" />
        </svg>
      </el-badge>
    </button>

    <!-- 修改密码弹窗（用户改自己的密码，要求旧密码） -->
    <el-dialog v-model="pwdDlg.show" title="修改密码" width="440px" :close-on-click-modal="false">
      <el-form :model="pwdDlg" label-position="top">
        <el-form-item label="旧密码" required>
          <el-input v-model="pwdDlg.oldPassword" type="password" show-password placeholder="当前密码" maxlength="64" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdDlg.newPassword" type="password" show-password placeholder="6-64 位" minlength="6" maxlength="64" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdDlg.confirmPassword" type="password" show-password placeholder="再输一遍" minlength="6" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="pwdDlg.saving" @click="onChangePassword">确认</el-button>
      </template>
    </el-dialog>
  </header>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import WorkspaceTabs from '@/components/WorkspaceTabs.vue';
import { useUserStore } from '@/stores/user';
import { usePermissionStore } from '@/stores/permission';
import { logout } from '@/api/auth';
import { getUnreadCount } from '@/api/workspace';
import { changeMyPassword } from '@/api/users';

const unread = ref(0);
const store = useUserStore();
const permissionStore = usePermissionStore();
const screenCenterAvailable = ref(false);
const SCREEN_VIEW_RESOURCE = '/api/screen/view/*';
let permissionGeneration = 0;

async function refreshUnread() {
  try { const n = await getUnreadCount(); if (typeof n === 'number') unread.value = n; } catch {}
}
onMounted(() => {
  refreshUnread();
  refreshScreenCenterAccess();
  window.addEventListener('notification-changed', refreshUnread);
});
onUnmounted(() => {
  permissionGeneration += 1;
  window.removeEventListener('notification-changed', refreshUnread);
});

async function refreshScreenCenterAccess() {
  const generation = ++permissionGeneration;
  screenCenterAvailable.value = false;
  if (!store.user) return;
  try {
    await permissionStore.load();
    if (generation !== permissionGeneration) return;
    screenCenterAvailable.value = permissionStore.canAccess(SCREEN_VIEW_RESOURCE);
  } catch {
    // 权限加载失败时保持隐藏，避免把不可用入口暴露给当前会话。
    if (generation === permissionGeneration) screenCenterAvailable.value = false;
  }
}

// 登录、退出或用户切换会让 permission store 废弃旧快照；顶栏入口跟随当前会话重新计算。
watch(() => store.user, () => {
  refreshScreenCenterAccess();
});

const avatarLetter = computed(() => {
  const n = store.displayName || store.user?.username || '?';
  return String(n).slice(0, 1);
});

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' });
    } catch { return; }
    try { await logout(); } catch { /* yiti session 后端清不掉也无所谓，前端继续清 */ }
    store.clear();
    // 整页跳转：SPA 内跳转不会销毁全部运行时状态，
    // menu store 等 Pinia 内存态会残留给下一个登录用户（旧菜单/旧索引）
    window.location.replace('/#/login');
  } else if (cmd === 'changePassword') {
    pwdDlg.oldPassword = '';
    pwdDlg.newPassword = '';
    pwdDlg.confirmPassword = '';
    pwdDlg.show = true;
  }
}

// 修改密码弹窗
const pwdDlg = reactive({ show: false, saving: false, oldPassword: '', newPassword: '', confirmPassword: '' });
async function onChangePassword() {
  const o = pwdDlg.oldPassword.trim();
  const p = pwdDlg.newPassword.trim();
  const c = pwdDlg.confirmPassword.trim();
  if (!o) return ElMessage.warning('请输入旧密码');
  if (!p || p.length < 6 || p.length > 64) return ElMessage.warning('新密码须 6-64 位');
  if (p !== c) return ElMessage.warning('两次输入的新密码不一致');
  pwdDlg.saving = true;
  try {
    await changeMyPassword(o, p);
    ElMessage.success('密码已修改，请重新登录');
    pwdDlg.show = false;
    // 安全起见，改密成功后强制重新登录
    setTimeout(async () => {
      try { await logout(); } catch {}
      store.clear();
      window.location.replace('/#/login');
    }, 600);
  } catch (e) {
    ElMessage.error('修改失败：' + (e?.message || e));
  } finally { pwdDlg.saving = false; }
}
</script>

<style lang="scss" scoped>
.hdr {
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex; align-items: center;
  padding: 0 var(--space-4);
  gap: var(--space-4);
  height: var(--layout-header-height);
  flex-shrink: 0;
}
.icon-btn {
  width: 40px; height: 40px;
  display: grid; place-items: center;
  padding: 0;
  color: var(--color-text);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-enter), background-color var(--motion-fast) var(--ease-enter);

  &:hover { color: var(--color-brand-700); background: var(--color-surface-soft); }
  svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
}
.screen-center-shortcut {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 10px;
  color: var(--color-brand-700);
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: color var(--motion-fast) var(--ease-enter), background-color var(--motion-fast) var(--ease-enter), border-color var(--motion-fast) var(--ease-enter);

  &:hover { background: var(--color-surface); border-color: var(--color-brand-500); }
  &:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
  svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
}
.account-pick {
  display: flex; align-items: center; gap: var(--space-2);
  padding: var(--space-1) 10px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-surface-soft);
  cursor: pointer;
  &:hover { border-color: var(--color-brand-500); }
  .avatar {
    width: 28px; height: 28px; border-radius: 50%;
    background: linear-gradient(135deg, var(--color-brand-500), var(--color-brand-700));
    color: var(--color-surface); display: grid; place-items: center;
    font-size: 12px; font-weight: 600;
  }
  .meta { font-size: 12px; line-height: 1.2;
    b { display: block; font-weight: 600; font-size: 12px; max-width: 96px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    small { color: var(--color-text-muted); font-size: 11px; }
  }
}
.account-caret { font-size: 10px; opacity: .5; }

@media (max-width: 760px) {
  .screen-center-shortcut {
    padding: 0 8px;
    span { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
  }
}
</style>
