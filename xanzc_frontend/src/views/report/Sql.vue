<!--
  SQL 探查 —— 对应 HTML RptSql
  高危页面：仅 SELECT · 表白名单 · 自动行限 · 超时 · 全程审计
  前端基础校验（关键字黑名单 / 表白名单 / SELECT 开头 / 必填原因），
  最终校验由后端 POST /api/reports/sql-probe/execute 完成。
  接入 yiti API：
    GET  /api/reports/sql-probe/schema-whitelist —— getSqlWhitelist
    GET  /api/reports/sql-probe/history          —— getSqlHistory
    POST /api/reports/sql-probe/execute          —— executeSqlProbe
-->
<template>
  <div class="rpt-sql">
    <div class="page-h">
      <h1>SQL 探查</h1>
      <span class="desc">高危：仅 SELECT · 自动行限 1000 · 30s 超时 · 全程审计 · 传输加密</span>
    </div>

    <el-alert type="error" :closable="false" class="warn">
      <template #title>
        ⚠ 高危操作：所有 SQL 将记录在审计日志（操作人 / SQL / 原因 / 影响行数 / TraceId）。
        仅允许 <b>SELECT</b>，仅允许查询 <b>{{ whitelist.length }}</b> 张白名单表，自动追加 <b>LIMIT 1000</b>，超过 30s 自动终止。
      </template>
    </el-alert>

    <div class="card-section input">
      <div class="row">
        <div class="col grow">
          <div class="lab"><span class="req">*</span> 执行原因</div>
          <el-input v-model="reason" placeholder="必填，进入审计" maxlength="100" />
        </div>
        <div class="col">
          <div class="lab">行数限制</div>
          <el-input-number v-model="rowLimit" :min="1" :max="1000" :step="100" controls-position="right" style="width:160px" />
        </div>
        <div class="col">
          <div class="lab">超时(秒)</div>
          <el-input-number v-model="timeoutSec" :min="1" :max="30" :step="5" controls-position="right" style="width:160px" />
        </div>
      </div>

      <div class="lab" style="margin-top:12px">SQL</div>
      <el-input v-model="sql" type="textarea" :rows="10" class="sql-area" />

      <el-alert v-if="errors.length" type="error" :closable="false" class="vresult">
        <template #title>
          ✗ 校验失败：
          <div v-for="(e, i) in errors" :key="i">· {{ e }}</div>
        </template>
      </el-alert>
      <el-alert v-else-if="reason.trim()" type="success" :closable="false" class="vresult">
        <template #title>
          ✓ 校验通过 · 引用表：<span class="mono">{{ usedTables.join(', ') || '-' }}</span>
        </template>
      </el-alert>

      <div class="ops">
        <el-button @click="formatSql">格式化</el-button>
        <el-button :icon="List"   @click="historyVisible = true">查看历史</el-button>
        <el-button type="primary" :loading="running" :disabled="!valid" @click="run" class="run">
          ▶ 执行
        </el-button>
      </div>
    </div>

    <div v-if="result && !running" class="card-section result">
      <div class="card-h">
        <div class="title">
          查询结果（{{ result.rows }} 行 · 用时 {{ result.time }}
          <span class="audit">· ✓ 已写入审计 TraceId {{ result.traceId }}</span>）
        </div>
      </div>
      <el-table :data="pagedData" size="default" stripe border max-height="480">
        <el-table-column type="index" label="#" width="50" fixed />
        <el-table-column
          v-for="(c, idx) in result.columns" :key="c"
          :prop="c" :label="c"
          :min-width="calcColWidth(c)"
          :fixed="idx < 2 ? true : false"
          :align="isNumeric(c) ? 'right' : 'left'"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span :class="{ mono: isNumeric(c) }">{{ formatCell(row[c]) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="resultPage"
          v-model:page-size="resultPageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="result.data.length"
          background
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </div>

    <!-- 历史记录 Dialog —— 点击行回填到 SQL 编辑器 -->
    <el-dialog v-model="historyVisible" title="SQL 探查历史" width="900px">
      <el-alert type="info" :closable="false" style="margin-bottom:8px">
        点击任一行可载入到上方编辑器（仅回填 SQL 与原因，不会自动执行）。
      </el-alert>
      <el-table
        :data="history" size="default" stripe v-loading="historyLoading"
        highlight-current-row
        @row-click="onPickHistory"
      >
        <el-table-column prop="createdTime" label="时间" width="160" :formatter="fmtDateTimeCol" />
        <el-table-column prop="empId"      label="操作人" width="120" />
        <el-table-column prop="remark"     label="原因" width="160" show-overflow-tooltip />
        <el-table-column label="SQL（节选）" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.sqlText || '').slice(0, 80) }}</template>
        </el-table-column>
        <el-table-column prop="rowCount"   label="行数" width="72" align="right" />
        <el-table-column label="耗时" width="80" align="right">
          <template #default="{ row }">{{ row.executionTimeMs != null ? row.executionTimeMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="" width="64">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="onPickHistory(row)">载入</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager" style="margin-top:12px">
        <el-pagination
          v-model:current-page="historyPageNo"
          v-model:page-size="historyPageSize"
          :page-sizes="[10, 20, 50]"
          :total="historyTotal"
          background
          layout="total, sizes, prev, pager, next"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </el-dialog>

    <!-- 表白名单 Dialog -->
    <el-dialog v-model="whitelistVisible" title="表白名单" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        以下为 SQL 探查可访问的全部表，仅允许 SELECT。
      </el-alert>
      <ul class="wl">
        <li v-for="t in whitelist" :key="t" class="mono">{{ t }}</li>
      </ul>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { List, Search } from '@element-plus/icons-vue';
import { executeSqlProbe, getSqlWhitelist, getSqlHistory, getSqlHistoryItem } from '@/api/report';
import { encryptSql } from '@/utils/sqlCrypto';

const reason = ref('');
const rowLimit = ref(1000);
const timeoutSec = ref(30);
const sql = ref(`SELECT cust_name, industry, SUM(amount) AS deposit_inc
FROM mart_cust_deposit_daily d
JOIN dim_customer c ON d.cust_id = c.id
WHERE d.org_id = '0001' AND d.biz_date >= '2026-04-16'
GROUP BY cust_name, industry
ORDER BY deposit_inc DESC LIMIT 10`);

const whitelist = ref([]);
const history = ref([]);
const result = ref(null);
const resultPage = ref(1);
const resultPageSize = ref(20);
const pagedData = computed(() => {
  if (!result.value?.data) return [];
  const start = (resultPage.value - 1) * resultPageSize.value;
  return result.value.data.slice(start, start + resultPageSize.value);
});
function calcColWidth(col) {
  const label = col || '';
  const samples = (result.value?.data || []).slice(0, 20);
  let maxLen = label.length;
  for (const row of samples) {
    const v = row[col];
    const len = v != null ? String(v).length : 0;
    if (len > maxLen) maxLen = len;
  }
  return Math.max(80, Math.min(maxLen * 12 + 24, 400));
}
const running = ref(false);

const historyVisible = ref(false);
const historyLoading = ref(false);
const whitelistVisible = ref(false);

const BLOCKED_KEYWORDS = ['DELETE', 'UPDATE', 'INSERT', 'DROP', 'TRUNCATE', 'ALTER', 'CREATE', 'GRANT', 'REVOKE', 'EXEC', 'CALL'];

// 抓取被引用的表名（FROM/JOIN 后第一个标识符）
const usedTables = computed(() => {
  const m = sql.value.match(/\b(?:FROM|JOIN)\s+([\w.]+)/gi) || [];
  return [...new Set(m.map(x => x.replace(/^(FROM|JOIN)\s+/i, '').toLowerCase()))];
});
const blockedKeywords = computed(() => {
  const upper = sql.value.toUpperCase();
  return BLOCKED_KEYWORDS.filter(k => new RegExp('\\b' + k + '\\b').test(upper));
});
const blockedTables = computed(() => {
  if (!whitelist.value.length) return [];
  return usedTables.value.filter(t => !whitelist.value.includes(t));
});
const noSelect = computed(() => !sql.value.trim().toUpperCase().startsWith('SELECT'));

const errors = computed(() => {
  const out = [];
  if (noSelect.value) out.push('仅允许 SELECT 语句开头');
  if (blockedKeywords.value.length) out.push(`检测到禁用关键字：${blockedKeywords.value.join(', ')}`);
  if (blockedTables.value.length) out.push(`表 ${blockedTables.value.join(', ')} 不在白名单内`);
  return out;
});
const valid = computed(() => errors.value.length === 0 && reason.value.trim().length > 0);

const historyPageNo = ref(1);
const historyPageSize = ref(10);
const historyTotal = ref(0);
async function loadHistory() {
  historyLoading.value = true;
  try {
    const r = await getSqlHistory({ pageNo: historyPageNo.value, pageSize: historyPageSize.value });
    history.value = Array.isArray(r) ? r : (r?.records || []);
    historyTotal.value = r?.total ?? history.value.length;
  } catch { history.value = []; }
  finally { historyLoading.value = false; }
}
watch(() => historyVisible.value, (v) => {
  if (v) { historyPageNo.value = 1; loadHistory(); }
});

onMounted(async () => {
  try {
    const wl = await getSqlWhitelist();
    if (Array.isArray(wl) && wl.length) whitelist.value = wl;
  } catch (e) {}
});

async function run() {
  if (!valid.value) { ElMessage.warning('请先通过校验'); return; }
  running.value = true;
  result.value = null;
  try {
    // 后端 SqlProbeExecuteReqDTO 只认 sql + remark 两字段，前端 ref 仍叫 reason 但提交时映射为 remark
    const r = await executeSqlProbe({
      sql: encryptSql(sql.value),
      remark: reason.value
    });
    result.value = {
      columns: r?.columns || [],
      data: r?.rows || [],
      rows: r?.rowCount ?? r?.rows?.length ?? 0,
      time: r?.executionTimeMs != null ? r.executionTimeMs + 'ms' : '-',
      traceId: r?.historyId || '-'
    };
    ElMessage.success(`执行成功：${result.value.rows} 行 · 用时 ${result.value.time}`);
  } catch (e) {
    ElMessage.error('执行失败：' + (e?.message || '后端校验未通过'));
  } finally {
    running.value = false;
  }
}

// 关键字大写 + 主子句换行 —— 不是真正的 SQL parser，覆盖常见 SELECT/JOIN/WHERE/GROUP BY 等
const FORMAT_KEYWORDS = [
  'SELECT','FROM','WHERE','GROUP BY','HAVING','ORDER BY','LIMIT','OFFSET',
  'INNER JOIN','LEFT JOIN','RIGHT JOIN','FULL JOIN','JOIN','ON',
  'AND','OR','UNION ALL','UNION','AS','DISTINCT','CASE','WHEN','THEN','ELSE','END'
];
const BREAK_BEFORE = new Set([
  'FROM','WHERE','GROUP BY','HAVING','ORDER BY','LIMIT','OFFSET',
  'INNER JOIN','LEFT JOIN','RIGHT JOIN','FULL JOIN','JOIN','UNION ALL','UNION'
]);
function formatSql() {
  let s = sql.value.replace(/\s+/g, ' ').trim();
  // 长关键字优先匹配（GROUP BY 在 GROUP/BY 之前）
  const sorted = [...FORMAT_KEYWORDS].sort((a, b) => b.length - a.length);
  for (const kw of sorted) {
    const re = new RegExp(`\\b${kw.replace(/ /g, '\\s+')}\\b`, 'gi');
    s = s.replace(re, BREAK_BEFORE.has(kw) ? `\n${kw}` : kw);
  }
  // 头部 SELECT 不换行；首行去掉前置换行
  sql.value = s.replace(/^\n+/, '').trim();
}

async function onPickHistory(row) {
  if (!row?.id) return;
  try {
    const full = await getSqlHistoryItem(row.id);
    sql.value = full?.sqlText || row.sqlText || sql.value;
    if (full?.remark || row.remark) reason.value = full?.remark || row.remark;
    historyVisible.value = false;
    ElMessage.success(`已载入历史：${full?.id || row.id}`);
  } catch (e) {
    ElMessage.error('载入失败：' + (e?.message || '未知错误'));
  }
}

function isNumeric(col) { return /(_inc|_amt|_cnt|_num|_rate|amount|value)$/i.test(col); }
function formatCell(v) {
  if (typeof v === 'number') return v.toLocaleString();
  return v ?? '-';
}
</script>

<style lang="scss" scoped>
.rpt-sql {
  .page-h { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px;
    h1 { font-size: 18px; font-weight: 600; color: $text-1; }
    .desc { color: $text-3; font-size: 12px; }
  }
  .warn { margin-bottom: 12px; }
  .input { padding: 16px 20px; }
  .row { display: flex; gap: 16px; flex-wrap: wrap; }
  .col { display: flex; flex-direction: column; gap: 6px; min-width: 160px;
    &.grow { flex: 1; min-width: 240px; }
  }
  .lab { font-size: 12px; color: $text-3;
    .req { color: $danger; margin-right: 4px; }
  }
  .sql-area :deep(textarea) { font-family: Menlo, Consolas, monospace; font-size: 12px; background: #fafafa; }
  .vresult { margin-top: 8px; }
  .ops { display: flex; gap: 8px; margin-top: 12px;
    .run { margin-left: auto; }
  }
  .result { padding: 16px 20px;
    .audit { color: $success; font-weight: 400; font-size: 12px; }
  }
  .card-h { display: flex; align-items: center; padding: 0 0 12px; border-bottom: 1px solid $border-1; margin-bottom: 12px;
    .title { font-size: 14px; font-weight: 600; }
  }
  .mono { font-family: Menlo, Consolas, monospace; font-size: 12px; }
  .wl { padding: 0 0 0 18px; line-height: 1.9; color: $text-2; }
}
</style>
