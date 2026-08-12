<template>
  <main class="bp-crud nav-page" aria-labelledby="nav-hub-title">
    <header class="page-h">
      <PageTitle id="nav-hub-title"><span class="sub">科技部可新增、编辑、删除和排序网址导航</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="网址导航操作">
        <el-button :type="sortMode ? 'warning' : 'default'" @click="toggleSortMode">
          {{ sortMode ? '退出排序' : '排序模式' }}
        </el-button>
        <el-button v-if="sortMode" type="primary" :loading="saving" :disabled="saving" @click="saveSort">保存排序</el-button>
        <el-button v-else type="primary" @click="openCreate">新增网址</el-button>
      </div>
    </header>

    <section
      class="card-section data-panel nav-directory"
      aria-label="网址导航"
      aria-describedby="nav-hub-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="nav-hub-heading" class="section-title">网址导航</h2>
          <p class="hint">仅启用的网址可打开外部系统；排序模式下可调整同一分组内顺序。</p>
        </div>
        <p id="nav-hub-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '网址导航加载中' : groups.length ? `共 ${navCount} 个网址` : '暂无网址数据' }}
        </p>
      </div>

      <div class="nav-groups" aria-labelledby="nav-hub-heading">
        <section v-for="g in groups" :key="g.category" class="nav-group" :aria-labelledby="`nav-group-${g.category || 'ungrouped'}`">
          <div class="group-h">
            <h3 :id="`nav-group-${g.category || 'ungrouped'}`" class="group-name">{{ g.category || '未分组' }}</h3>
            <span class="group-count">{{ g.navs.length }} 个网址</span>
          </div>
          <div class="card-grid">
            <article v-for="(n, idx) in g.navs" :key="n.id" class="nav-card" :class="{ disabled: n.status === 'DISABLED' }">
              <a
                v-if="!sortMode && n.status !== 'DISABLED' && n.navUrl"
                class="nav-card-link"
                :href="n.navUrl"
                target="_blank"
                rel="noopener noreferrer"
                :aria-label="`打开网址：${n.navName}`"
              >
                <span class="card-icon" aria-hidden="true">{{ iconText(n) }}</span>
                <span class="card-body">
                  <span class="card-name">{{ n.navName }}</span>
                  <span class="card-url">{{ n.navUrl }}</span>
                </span>
              </a>
              <div v-else class="nav-card-static">
                <span class="card-icon" aria-hidden="true">{{ iconText(n) }}</span>
                <span class="card-body">
                  <span class="card-name">{{ n.navName }}</span>
                  <span class="card-url">{{ n.navUrl }}</span>
                </span>
              </div>
              <div class="card-ops" role="group" :aria-label="`${n.navName} 操作`">
                <template v-if="sortMode">
                  <el-button link size="small" :disabled="idx === 0 || saving" @click="move(g, idx, -1)">上移</el-button>
                  <el-button link size="small" :disabled="idx === g.navs.length - 1 || saving" @click="move(g, idx, 1)">下移</el-button>
                </template>
                <template v-else>
                  <el-button link type="primary" size="small" :disabled="updatingId === n.id || deletingId === n.id" @click="openEdit(n)">编辑</el-button>
                  <el-button link type="primary" size="small" :loading="updatingId === n.id" :disabled="deletingId === n.id" @click="toggleStatus(n)">
                    {{ n.status === 'DISABLED' ? '启用' : '禁用' }}
                  </el-button>
                  <el-popconfirm :title="`确认删除「${n.navName}」？删除后无法恢复。`" @confirm="onDelete(n)">
                    <template #reference>
                      <el-button link type="danger" size="small" :loading="deletingId === n.id" :disabled="updatingId === n.id">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </div>
              <el-tag v-if="n.status === 'DISABLED'" class="tag-warning disabled-tag" effect="plain" size="small">已禁用</el-tag>
            </article>
          </div>
        </section>
      </div>
      <el-empty v-if="!loading && groups.length === 0" description="暂无网址数据" />
    </section>

    <el-dialog v-model="dialogVisible" class="bp-crud-dialog" :title="editing ? '编辑网址' : '新增网址'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.navName" aria-label="网址名称" maxlength="50" placeholder="如 OA 系统" />
        </el-form-item>
        <el-form-item label="网址" required>
          <el-input v-model="form.navUrl" aria-label="网址地址" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="分组" required>
          <el-select
            v-model="form.navCategory"
            aria-label="网址分组"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入分组"
            style="width:100%"
          >
            <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listNav, createNav, updateNav, deleteNav, sortNav } from '@/api/nav';

const loading = ref(false);
const saving = ref(false);
const updatingId = ref(null);
const deletingId = ref(null);
const groups = ref([]);
const sortMode = ref(false);

const categories = computed(() => groups.value.map(g => g.category).filter(Boolean));
const navCount = computed(() => groups.value.reduce((total, group) => total + (group.navs?.length || 0), 0));

function iconText(n) {
  return (n.navName || '?').trim().charAt(0).toUpperCase();
}

async function load() {
  loading.value = true;
  try {
    const r = await listNav({ status: 'ALL' });
    groups.value = Array.isArray(r?.groups) ? r.groups.map(g => ({ ...g, navs: [...(g.navs || [])] })) : [];
  } catch { groups.value = []; } finally { loading.value = false; }
}

function toggleSortMode() {
  if (saving.value) return;
  if (sortMode.value) {
    sortMode.value = false;
    load();
  } else {
    sortMode.value = true;
  }
}

function move(group, idx, dir) {
  const arr = group.navs;
  const nextIndex = idx + dir;
  if (nextIndex < 0 || nextIndex >= arr.length) return;
  [arr[idx], arr[nextIndex]] = [arr[nextIndex], arr[idx]];
}

async function saveSort() {
  if (saving.value) return;
  const items = [];
  for (const g of groups.value) {
    g.navs.forEach((n, i) => items.push({ id: n.id, sortOrder: i + 1 }));
  }
  saving.value = true;
  try {
    await sortNav(items);
    ElMessage.success('排序已保存');
    sortMode.value = false;
    load();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); } finally { saving.value = false; }
}

const dialogVisible = ref(false);
const editing = ref(null);
const form = ref({ navName: '', navUrl: '', navCategory: '' });

function openCreate() {
  editing.value = null;
  form.value = { navName: '', navUrl: '', navCategory: '' };
  dialogVisible.value = true;
}

function openEdit(n) {
  editing.value = n;
  form.value = { navName: n.navName, navUrl: n.navUrl, navCategory: n.navCategory };
  dialogVisible.value = true;
}

async function submit() {
  if (saving.value) return;
  if (!form.value.navName?.trim()) return ElMessage.warning('请填写名称');
  if (!form.value.navUrl?.trim()) return ElMessage.warning('请填写网址');
  if (!form.value.navCategory?.trim()) return ElMessage.warning('请选择或输入分组');
  saving.value = true;
  try {
    if (editing.value) {
      await updateNav(editing.value.id, { ...form.value, sortOrder: editing.value.sortOrder, status: editing.value.status });
    } else {
      await createNav({ ...form.value });
    }
    ElMessage.success('已保存');
    dialogVisible.value = false;
    load();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); } finally { saving.value = false; }
}

async function toggleStatus(n) {
  if (updatingId.value === n.id || deletingId.value === n.id) return;
  const next = n.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED';
  updatingId.value = n.id;
  try {
    await updateNav(n.id, {
      navName: n.navName, navUrl: n.navUrl, navIcon: n.navIcon,
      navCategory: n.navCategory, sortOrder: n.sortOrder, status: next
    });
    ElMessage.success(next === 'ACTIVE' ? '已启用' : '已禁用');
    load();
  } catch (e) { ElMessage.error(e?.message || '操作失败'); } finally { updatingId.value = null; }
}

async function onDelete(n) {
  if (deletingId.value === n.id || updatingId.value === n.id) return;
  deletingId.value = n.id;
  try {
    await deleteNav(n.id);
    ElMessage.success('已删除');
    load();
  } catch (e) { ElMessage.error(e?.message || '删除失败'); } finally { deletingId.value = null; }
}

onMounted(load);
</script>

<style scoped>
.nav-groups { display: grid; gap: var(--space-4); }
.nav-group { border: 1px solid var(--color-border); border-radius: var(--radius-control); padding: var(--space-4); }
.group-h { align-items: center; display: flex; justify-content: space-between; margin-bottom: var(--space-3); }
.group-name { color: var(--color-text-strong); font-size: 14px; font-weight: 600; line-height: 22px; }
.group-count { color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.card-grid { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
.nav-card { align-items: stretch; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-control); display: flex; min-width: 0; padding: var(--space-3); position: relative; }
.nav-card.disabled { background: var(--color-surface-soft); }
.nav-card-link, .nav-card-static { align-items: center; color: inherit; display: flex; flex: 1; gap: var(--space-3); min-width: 0; }
.nav-card-link:hover { background: var(--color-brand-100); margin: calc(var(--space-1) * -1); padding: var(--space-1); }
.card-icon { align-items: center; background: var(--color-brand-100); color: var(--color-brand-700); display: inline-flex; flex: 0 0 32px; font-size: 14px; font-weight: 600; height: 32px; justify-content: center; width: 32px; }
.card-body { display: grid; gap: var(--space-1); min-width: 0; }
.card-name { color: var(--color-text-strong); font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-url { color: var(--color-text-muted); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-ops { align-items: center; display: flex; flex: 0 0 auto; margin-left: var(--space-2); }
.disabled-tag { align-self: flex-start; margin-left: var(--space-2); }
</style>
