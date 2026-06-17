<template>
  <div class="nav-page">
    <div class="page-h">
      <div>
        <h1>网址导航 <span class="sub">科技部可新增 / 编辑 / 删除 / 排序</span></h1>
      </div>
      <div class="actions">
        <el-button :type="sortMode ? 'warning' : 'default'" @click="toggleSortMode">
          {{ sortMode ? '退出排序' : '↕ 排序模式' }}
        </el-button>
        <el-button v-if="sortMode" type="primary" :loading="saving" @click="saveSort">保存排序</el-button>
        <el-button v-else type="primary" @click="openCreate">＋ 新增网址</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-for="g in groups" :key="g.category" class="nav-group">
        <div class="group-h">
          <span class="group-name">{{ g.category || '未分组' }}</span>
          <span class="group-count">{{ g.navs.length }} 个网址</span>
        </div>
        <div class="card-grid">
          <div
            v-for="(n, idx) in g.navs"
            :key="n.id"
            class="nav-card"
            :class="{ disabled: n.status === 'DISABLED', clickable: !sortMode && n.status !== 'DISABLED' }"
            @click="onCardClick(n)">
            <div class="card-icon">{{ iconText(n) }}</div>
            <div class="card-body">
              <div class="card-name">{{ n.navName }}</div>
              <div class="card-url">{{ n.navUrl }}</div>
            </div>
            <div class="card-ops" @click.stop>
              <template v-if="sortMode">
                <el-button link size="small" :disabled="idx === 0" @click="move(g, idx, -1)">↑</el-button>
                <el-button link size="small" :disabled="idx === g.navs.length - 1" @click="move(g, idx, 1)">↓</el-button>
              </template>
              <template v-else>
                <el-button link type="primary" size="small" @click="openEdit(n)">编辑</el-button>
                <el-button link type="primary" size="small" @click="toggleStatus(n)">{{ n.status === 'DISABLED' ? '启用' : '禁用' }}</el-button>
                <el-popconfirm :title="`确认删除「${n.navName}」？`" @confirm="onDelete(n)">
                  <template #reference><el-button link type="primary" size="small">删除</el-button></template>
                </el-popconfirm>
              </template>
            </div>
            <span v-if="n.status === 'DISABLED'" class="disabled-tag">已禁用</span>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && groups.length === 0" description="暂无网址" />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑网址' : '新增网址'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.navName" maxlength="50" placeholder="如 OA 系统" />
        </el-form-item>
        <el-form-item label="网址" required>
          <el-input v-model="form.navUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="分组" required>
          <el-select v-model="form.navCategory" filterable allow-create default-first-option
                     placeholder="选择或输入分组" style="width:100%">
            <el-option v-for="c in categories" :key="c" :value="c" :label="c" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listNav, createNav, updateNav, deleteNav, sortNav } from '@/api/nav';

const loading = ref(false);
const saving = ref(false);
const groups = ref([]);
const sortMode = ref(false);

const categories = computed(() => groups.value.map(g => g.category).filter(Boolean));

function iconText(n) {
  // 统一显示标题第一个字 / 字母
  return (n.navName || '?').trim().charAt(0).toUpperCase();
}

async function load() {
  loading.value = true;
  try {
    const r = await listNav({ status: 'ALL' });
    groups.value = Array.isArray(r?.groups) ? r.groups.map(g => ({ ...g, navs: [...(g.navs || [])] })) : [];
  } catch { groups.value = []; } finally { loading.value = false; }
}

function onCardClick(n) {
  if (sortMode.value || n.status === 'DISABLED') return;
  if (n.navUrl) window.open(n.navUrl, '_blank');
}

// ---- 排序模式 ----
function toggleSortMode() {
  if (sortMode.value) { sortMode.value = false; load(); } // 退出不保存 → 还原
  else sortMode.value = true;
}
function move(group, idx, dir) {
  const arr = group.navs;
  const j = idx + dir;
  if (j < 0 || j >= arr.length) return;
  [arr[idx], arr[j]] = [arr[j], arr[idx]];
}
async function saveSort() {
  // 每个分组内按当前顺序重排 sortOrder（1-based）
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

// ---- 新增 / 编辑 ----
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
  if (!form.value.navName?.trim()) return ElMessage.warning('请填写名称');
  if (!form.value.navUrl?.trim()) return ElMessage.warning('请填写网址');
  if (!form.value.navCategory?.trim()) return ElMessage.warning('请选择/输入分组');
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
  const next = n.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED';
  try {
    await updateNav(n.id, {
      navName: n.navName, navUrl: n.navUrl, navIcon: n.navIcon,
      navCategory: n.navCategory, sortOrder: n.sortOrder, status: next
    });
    ElMessage.success(next === 'ACTIVE' ? '已启用' : '已禁用');
    load();
  } catch (e) { ElMessage.error(e?.message || '操作失败'); }
}
async function onDelete(n) {
  try {
    await deleteNav(n.id);
    ElMessage.success('已删除');
    load();
  } catch (e) { ElMessage.error(e?.message || '删除失败'); }
}

onMounted(load);
</script>

<style scoped>
.nav-page { }
.page-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-h h1 { font-size: 18px; margin: 0; }
.page-h .sub { font-size: 12px; color: #909399; font-weight: normal; margin-left: 8px; }
.nav-group { margin-bottom: 12px; background: #fff; border: 1px solid #ebeef5; border-radius: 4px; padding: 16px 20px; }
.group-h { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.group-name { font-size: 15px; font-weight: 600; color: #303133; }
.group-count { font-size: 12px; color: #909399; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px; }
.nav-card { position: relative; display: flex; align-items: center; gap: 10px; border: 1px solid #ebeef5; border-radius: 4px; padding: 12px; transition: box-shadow .15s, border-color .15s; }
.nav-card.clickable { cursor: pointer; }
.nav-card.clickable:hover { box-shadow: 0 2px 10px rgba(30,91,186,.15); border-color: #1E5BBA; }
.nav-card.disabled { opacity: .55; background: #fafafa; }
.card-icon { flex: 0 0 40px; width: 40px; height: 40px; border-radius: 4px; background: var(--el-color-primary); color: #fff; font-size: 18px; font-weight: 600; display: flex; align-items: center; justify-content: center; }
.card-body { flex: 1; min-width: 0; }
.card-name { font-size: 14px; font-weight: 500; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-url { font-size: 12px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.card-ops { flex: 0 0 auto; display: flex; align-items: center; }
.disabled-tag { position: absolute; top: 6px; right: 8px; font-size: 11px; color: #c0c4cc; }
</style>
