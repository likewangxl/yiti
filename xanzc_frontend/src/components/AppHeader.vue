<template>
  <header class="hdr">
    <div class="toggle">☰</div>
    <div class="search">
      <el-input v-model="kw" placeholder="搜索客户 / 线索 / 任务编号 / 报表..." size="default">
        <template #prefix><span>🔍</span></template>
      </el-input>
    </div>
    <div class="spacer" />

    <el-dropdown trigger="click" @command="onCommand">
      <div class="role-pick">
        <div class="avatar">{{ avatarLetter }}</div>
        <div class="meta">
          <b>{{ store.displayName }}</b>
          <small>{{ store.orgName || store.roleName || '—' }}</small>
        </div>
        <span style="font-size:10px;opacity:.5">▾</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item disabled>
            <span style="color:#9CA3AF;font-size:12px">{{ store.user?.username }} · {{ store.user?.deptNo || store.user?.mainOrgCode || '—' }}</span>
          </el-dropdown-item>
          <el-dropdown-item divided disabled>
            <span style="color:#9CA3AF;font-size:12px">切换角色</span>
          </el-dropdown-item>
          <el-dropdown-item
            v-for="r in store.roles"
            :key="r.roleId"
            :command="`role:${r.roleId}`"
          >
            <span :style="{ fontWeight: r.roleId === store.activeRoleId ? 600 : 400, minWidth: '120px', display: 'inline-flex', justifyContent: 'space-between', alignItems: 'center' }">
              <span>{{ r.roleChName }}</span>
              <span v-if="r.roleId === store.activeRoleId" style="color:#22c55e;margin-left:12px">✓</span>
            </span>
          </el-dropdown-item>
          <el-dropdown-item divided command="changePassword">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <div class="icon-btn" @click="$router.push('/system/notifications')" title="通知中心" style="cursor:pointer">
      <el-badge :value="unread" :max="99" :hidden="!unread">🔔</el-badge>
    </div>
    <div class="icon-btn">❓</div>
    <div class="icon-btn">👤</div>

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
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { logout, switchRole } from '@/api/auth';
import { getUnreadCount } from '@/api/workspace';
import { changeMyPassword } from '@/api/users';

const kw = ref('');
const unread = ref(0);
const store = useUserStore();
const router = useRouter();

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
  if (typeof cmd === 'string' && cmd.startsWith('role:')) {
    const roleId = cmd.slice(5);
    if (roleId === store.activeRoleId) return; // 已是当前角色
    const r = store.roles.find(x => x.roleId === roleId);
    try {
      await ElMessageBox.confirm(
        `确认切换到角色「${r?.roleChName || roleId}」？切换后菜单、业务操作将按该角色权限显示与处理。`,
        '切换角色', { type: 'warning', confirmButtonText: '确认切换', cancelButtonText: '取消' });
    } catch { return; }
    try {
      await switchRole(roleId);
      store.setActiveRole(roleId);
      ElMessage.success(`已切换到「${r?.roleChName || roleId}」，正在进入工作台`);
      // 跳转工作台并整页刷新，确保菜单/权限/数据范围全部按新角色重新拉取
      await router.push('/workspace').catch(() => {});
      setTimeout(() => window.location.reload(), 300);
    } catch (e) {
      ElMessage.error('切换角色失败：' + (e?.message || e));
    }
    return;
  }
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' });
    } catch { return; }
    try { await logout(); } catch { /* yiti session 后端清不掉也无所谓，前端继续清 */ }
    store.clear();
    ElMessage.success('已退出登录');
    router.replace('/login');
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
      router.replace('/login');
    }, 600);
  } catch (e) {
    ElMessage.error('修改失败：' + (e?.message || e));
  } finally { pwdDlg.saving = false; }
}
</script>

<style lang="scss" scoped>
.hdr {
  background: #fff;
  border-bottom: 1px solid $border-1;
  display: flex; align-items: center;
  padding: 0 16px;
  gap: 16px;
  height: $header-h;
  flex-shrink: 0;
}
.toggle {
  width: 32px; height: 32px;
  display: grid; place-items: center;
  border-radius: 4px;
  color: $text-2;
  cursor: pointer;
  &:hover { background: $bg-soft; }
}
.search { width: 360px; }
.spacer { flex: 1; }
.role-pick {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 10px;
  border: 1px solid $border-1;
  border-radius: 16px;
  background: $bg-soft;
  cursor: pointer;
  outline: none;
  &:hover { border-color: $primary-400; }
  .avatar {
    width: 28px; height: 28px; border-radius: 50%;
    background: linear-gradient(135deg, $primary-400, $primary);
    color: #fff; display: grid; place-items: center;
    font-size: 12px; font-weight: 600;
  }
  .meta { font-size: 12px; line-height: 1.2;
    b { display: block; font-weight: 600; font-size: 12px; max-width: 96px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    small { color: $text-3; font-size: 11px; }
  }
}
.icon-btn {
  width: 32px; height: 32px;
  display: grid; place-items: center;
  border-radius: 4px;
  color: $text-2;
  cursor: pointer;
  &:hover { background: $bg-soft; color: $primary; }
}
</style>
