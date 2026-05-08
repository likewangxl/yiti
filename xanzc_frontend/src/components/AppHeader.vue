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
            <span style="color:#9CA3AF;font-size:12px">{{ store.user?.username }} · {{ store.roleName }}</span>
          </el-dropdown-item>
          <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <div class="icon-btn">
      <el-badge :value="unread" :max="99" :hidden="!unread">🔔</el-badge>
    </div>
    <div class="icon-btn">❓</div>
    <div class="icon-btn">👤</div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { logout } from '@/api/auth';
import { getUnreadCount } from '@/api/workspace';

const kw = ref('');
const unread = ref(0);
const store = useUserStore();
const router = useRouter();

onMounted(async () => {
  try { const n = await getUnreadCount(); if (typeof n === 'number') unread.value = n; } catch {}
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
    ElMessage.success('已退出登录');
    router.replace('/login');
  }
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
