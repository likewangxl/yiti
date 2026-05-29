<template>
  <div>
    <div class="page-h">
      <h1>人员标签</h1>
      <span class="desc">为员工绑定评价人/被评价人标签</span>
    </div>

    <div class="layout">
      <!-- 左侧：用户搜索 -->
      <div class="card-section user-col">
        <div class="card-h-mini">选择员工</div>
        <el-input
          v-model="userKeyword"
          placeholder="搜索姓名或用户名"
          clearable
          size="small"
          class="user-search"
          @input="onUserSearch"
        />
        <div v-if="userLoading" class="loading-hint">搜索中…</div>
        <div v-else-if="!userList.length" class="empty-hint">
          {{ userKeyword ? '未找到匹配员工' : '请输入关键词搜索' }}
        </div>
        <div v-else class="user-list">
          <div
            v-for="u in userList"
            :key="u.userId"
            class="user-item"
            :class="{ active: pickedUser?.userId === u.userId }"
            @click="pickUser(u)"
          >
            <div class="user-name">{{ u.userchnname || u.username }}</div>
            <div class="user-id">{{ u.userId }}</div>
          </div>
        </div>
      </div>

      <!-- 右侧：标签绑定管理 -->
      <div class="card-section bind-col">
        <!-- 未选中用户时的占位提示 -->
        <div v-if="!pickedUser" class="empty-pane">← 请在左侧搜索并选择员工</div>

        <template v-else>
          <div class="bind-header">
            <div class="bind-title">
              <span class="user-full-name">{{ pickedUser.userchnname || pickedUser.username }}</span>
              <span class="user-full-id">（{{ pickedUser.userId }}）</span>
            </div>
          </div>

          <!-- 绑定新标签区域 -->
          <div class="section-block">
            <div class="section-title">绑定新标签</div>
            <div class="bind-form">
              <el-select
                v-model="toBindTagIds"
                multiple
                filterable
                placeholder="选择要绑定的标签（可多选）"
                style="flex: 1; min-width: 0"
                :loading="allTagsLoading"
              >
                <!-- 按类型分组展示，提升可读性 -->
                <el-option-group label="被评价人标签">
                  <el-option
                    v-for="t in allTagsOfType(1)"
                    :key="t.tagId"
                    :value="t.tagId"
                    :label="t.tagName"
                    :disabled="isBound(t.tagId)"
                  >
                    <span>{{ t.tagName }}</span>
                    <el-tag
                      v-if="isBound(t.tagId)"
                      class="tag-info"
                      effect="plain"
                      size="small"
                      style="margin-left: 8px"
                    >已绑定</el-tag>
                  </el-option>
                </el-option-group>
                <el-option-group label="评价人标签">
                  <el-option
                    v-for="t in allTagsOfType(2)"
                    :key="t.tagId"
                    :value="t.tagId"
                    :label="t.tagName"
                    :disabled="isBound(t.tagId)"
                  />
                </el-option-group>
              </el-select>
              <el-button
                type="primary"
                :disabled="!toBindTagIds.length"
                :loading="binding"
                @click="handleBind"
              >
                确认绑定
              </el-button>
            </div>
          </div>

          <!-- 已绑标签列表 -->
          <div class="section-block">
            <div class="section-title">
              已绑标签
              <span class="tag-count">共 {{ boundTags.length }} 个</span>
            </div>
            <div v-if="boundLoading" class="loading-hint">加载中…</div>
            <div v-else-if="!boundTags.length" class="empty-hint">该员工暂未绑定任何标签</div>
            <div v-else class="bound-tags">
              <div
                v-for="item in boundTags"
                :key="item.id"
                class="bound-tag-item"
              >
                <el-tag
                  :class="item.tagType === 1 ? 'tag-success' : 'tag-info'"
                  effect="plain"
                  size="default"
                  class="bound-tag-label"
                >
                  {{ item.tagName }}
                </el-tag>
                <span class="bound-tag-type">{{ TAG_TYPE_LABEL[item.tagType] || '-' }}</span>
                <el-button
                  link
                  type="danger"
                  size="small"
                  :loading="unbindingIds.has(item.tagId)"
                  @click="handleUnbind(item)"
                >
                  解绑
                </el-button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listTags, listAllTags, listUserTags, bindUserTags, unbindUserTags } from '@/api/eval';
import { listUsers } from '@/api/users';

// === 常量 ===
const TAG_TYPE_LABEL = { 1: '被评价人', 2: '评价人' };

// === 全量标签（供下拉选择，只加载一次） ===
const allTags = ref([]);
const allTagsLoading = ref(false);

/** 按类型过滤全量标签 */
function allTagsOfType(type) {
  return allTags.value.filter(t => t.tagType === type && t.status === 1);
}

/** 加载所有启用标签 */
async function loadAllTags() {
  allTagsLoading.value = true;
  try {
    const r = await listAllTags({ status: 1 });
    allTags.value = Array.isArray(r) ? r : (r?.records || []);
  } catch {
    allTags.value = [];
  } finally {
    allTagsLoading.value = false;
  }
}

// === 用户搜索 ===
const userKeyword = ref('');
const userList = ref([]);
const userLoading = ref(false);
let searchTimer = null;

/** 防抖搜索用户 */
function onUserSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  if (!userKeyword.value.trim()) {
    userList.value = [];
    return;
  }
  searchTimer = setTimeout(doSearchUsers, 300);
}

async function doSearchUsers() {
  userLoading.value = true;
  try {
    const r = await listUsers({ keyword: userKeyword.value.trim(), pageSize: 30 });
    // listUsers 可能返回数组或 PageResult
    userList.value = Array.isArray(r) ? r : (r?.records || []);
  } catch {
    userList.value = [];
  } finally {
    userLoading.value = false;
  }
}

// === 选中用户 ===
const pickedUser = ref(null);

/** 点击用户 → 加载其已绑标签 */
async function pickUser(user) {
  pickedUser.value = user;
  toBindTagIds.value = [];
  await loadBoundTags(user.userId);
}

// === 已绑标签 ===
const boundUserTags = ref([]);  // 原始绑定记录 [{ id, userId, tagId }]
const boundLoading = ref(false);

/**
 * 将绑定记录与全量标签合并，得到带 tagName/tagType 的展示列表
 * 若全量标签还未加载完成，等待后再计算
 */
const boundTags = computed(() => {
  return boundUserTags.value.map(ut => {
    const tag = allTags.value.find(t => t.tagId === ut.tagId) || {};
    return {
      id: ut.id,
      tagId: ut.tagId,
      tagName: tag.tagName || `TAG_${ut.tagId}`,
      tagType: tag.tagType
    };
  });
});

/** 判断某个 tagId 是否已绑定 */
function isBound(tagId) {
  return boundUserTags.value.some(ut => ut.tagId === tagId);
}

/** 加载指定用户的已绑标签记录 */
async function loadBoundTags(userId) {
  boundLoading.value = true;
  try {
    const r = await listUserTags(userId);
    boundUserTags.value = Array.isArray(r) ? r : [];
  } catch {
    boundUserTags.value = [];
  } finally {
    boundLoading.value = false;
  }
}

// === 绑定操作 ===
const toBindTagIds = ref([]);  // 待绑定的 tagId 列表
const binding = ref(false);

/** 批量绑定标签 */
async function handleBind() {
  if (!toBindTagIds.value.length) return;
  binding.value = true;
  try {
    await bindUserTags(pickedUser.value.userId, toBindTagIds.value);
    ElMessage.success(`已成功绑定 ${toBindTagIds.value.length} 个标签`);
    toBindTagIds.value = [];
    await loadBoundTags(pickedUser.value.userId);
  } catch {
    ElMessage.error('绑定失败，请重试');
  } finally {
    binding.value = false;
  }
}

// === 解绑操作 ===
// 用 Set 跟踪正在解绑中的 tagId，支持多个并发解绑
const unbindingIds = reactive(new Set());

/** 确认后解绑单个标签 */
async function handleUnbind(item) {
  try {
    await ElMessageBox.confirm(
      `确认为「${pickedUser.value.userchnname || pickedUser.value.username}」解绑标签「${item.tagName}」？`,
      '解绑确认',
      { type: 'warning', confirmButtonText: '确认解绑', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  unbindingIds.add(item.tagId);
  try {
    await unbindUserTags(pickedUser.value.userId, [item.tagId]);
    ElMessage.success('标签已解绑');
    await loadBoundTags(pickedUser.value.userId);
  } catch {
    ElMessage.error('解绑失败，请重试');
  } finally {
    unbindingIds.delete(item.tagId);
  }
}

onMounted(loadAllTags);
</script>

<style lang="scss" scoped>
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
}

/* 左侧用户列 */
.user-col {
  padding: 16px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.card-h-mini {
  font-size: 14px;
  font-weight: 600;
  padding: 0 0 12px;
  border-bottom: 1px solid $border-1;
  margin-bottom: 10px;
  color: $text-1;
}
.user-search {
  margin-bottom: 10px;
}
.user-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.user-item {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
  border: 1px solid transparent;

  &:hover {
    background: $bg-soft;
  }

  &.active {
    background: mix($primary, #fff, 10%);
    border-color: mix($primary, #fff, 30%);
  }
}
.user-name {
  font-size: 14px;
  font-weight: 500;
  color: $text-1;
}
.user-id {
  font-size: 12px;
  color: $text-3;
  margin-top: 2px;
  font-family: ui-monospace, monospace;
}

/* 右侧绑定列 */
.bind-col {
  padding: 20px 24px;
  max-height: calc(100vh - 200px);
  overflow: auto;
}
.empty-pane {
  padding: 80px 20px;
  text-align: center;
  color: $text-3;
  font-size: 13px;
}
.bind-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 1px solid $border-1;
  margin-bottom: 20px;
}
.bind-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-1;
}
.user-full-id {
  font-size: 13px;
  color: $text-3;
  font-weight: 400;
}

/* 区块标题 */
.section-block {
  margin-bottom: 28px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-1;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.tag-count {
  font-size: 12px;
  font-weight: 400;
  color: $text-3;
}

/* 绑定新标签行 */
.bind-form {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 已绑标签列表 */
.bound-tags {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bound-tag-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: $bg-soft;
  border-radius: 4px;
  border: 1px solid $border-1;
}
.bound-tag-label {
  min-width: 80px;
}
.bound-tag-type {
  font-size: 12px;
  color: $text-3;
  flex: 1;
}

/* 通用 */
.loading-hint {
  color: $text-3;
  font-size: 13px;
  padding: 12px 0;
  text-align: center;
}
.empty-hint {
  color: $text-3;
  font-size: 13px;
  padding: 20px 0;
  text-align: center;
}
</style>
