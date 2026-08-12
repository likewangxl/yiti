<template>
  <header class="hdr">
    <button
      type="button"
      class="shell-toggle"
      data-testid="sidebar-toggle"
      :aria-label="props.sidebarCollapsed ? '展开侧边导航' : '折叠侧边导航'"
      :aria-expanded="props.sidebarCollapsed ? 'false' : 'true'"
      aria-controls="app-sidebar"
      :title="props.sidebarCollapsed ? '展开侧边导航' : '折叠侧边导航'"
      @click="emit('toggle-sidebar')"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
        <path d="M4 6h16M4 12h16M4 18h16" />
      </svg>
    </button>
    <div class="spacer" />

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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { logout } from '@/api/auth';
import { getUnreadCount } from '@/api/workspace';
import { changeMyPassword } from '@/api/users';

const props = defineProps({
  sidebarCollapsed: { type: Boolean, default: false }
});
const emit = defineEmits(['toggle-sidebar']);
const unread = ref(0);
const store = useUserStore();

async function refreshUnread() {
  try { const n = await getUnreadCount(); if (typeof n === 'number') unread.value = n; } catch {}
}
onMounted(() => {
  refreshUnread();
  window.addEventListener('notification-changed', refreshUnread);
});
onUnmounted(() => {
  window.removeEventListener('notification-changed', refreshUnread);
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
.shell-toggle,
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
.spacer { flex: 1; }
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
</style>
