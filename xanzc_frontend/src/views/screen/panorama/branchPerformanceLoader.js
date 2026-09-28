const PAGE_SIZE = 100;
const MAX_RECORDS = 10000;

/** 完整读取后才发布统计，任何缺页或读取期间变化都拒绝输出部分排名。 */
export async function readAllPerformancePages(readPage, params, isCurrent = () => true) {
  const records = [], seen = new Set();
  let total = null, metrics = null;
  for (let pageNo = 1; ; pageNo += 1) {
    if (!isCurrent()) throw new Error('请求已过期');
    const page = await readPage({ ...params, pageNo, pageSize: PAGE_SIZE });
    if (!isCurrent()) throw new Error('请求已过期');
    if (!page || !Array.isArray(page.records) || !['number', 'string'].includes(typeof page.total) || String(page.total).trim() === '' || !Number.isSafeInteger(Number(page.total)) || Number(page.total) < 0) throw new Error('KPI分页响应无效');
    if (params.orgCode && String(page.scopeOrgCode || '') !== params.orgCode) throw new Error('后端尚未确认支行KPI范围，请更新服务后重试');
    const nextTotal = Number(page.total);
    if (nextTotal > MAX_RECORDS) throw new Error('统计对象超过全量读取上限，未展示部分排名，请缩小统计范围');
    if (total !== null && total !== nextTotal) throw new Error('读取期间KPI对象总数发生变化，请刷新');
    total = nextTotal;
    const nextMetrics = Array.isArray(page.metrics) ? page.metrics : [];
    if (metrics !== null && JSON.stringify(metrics) !== JSON.stringify(nextMetrics)) throw new Error('读取期间KPI指标定义发生变化，请刷新');
    metrics = nextMetrics;
    for (const row of page.records) {
      if (params.subjectType && row?.subjectType !== params.subjectType) throw new Error('KPI响应主体类型不一致，已拒绝展示');
      const id = String(row?.schemeCode || row?.subjectType || '') + '|' + String(row?.subjectId ?? row?.id ?? row?.schemeCode ?? '').trim();
      if (!id.split('|')[1]) throw new Error('KPI分页响应缺少对象身份');
      if (seen.has(id)) throw new Error('KPI分页存在重复对象，未展示部分排名');
      seen.add(id); records.push(row);
    }
    if (records.length === total) return { records, metrics, total };
    if (records.length > total || page.records.length !== PAGE_SIZE) throw new Error('KPI分页数据不完整，未展示部分排名');
  }
}

/** 同机构、方案、日期分别读取机构KPI和全部可见员工，不把两种维度混算。 */
export async function loadBranchPerformance(readPage, context, isCurrent = () => true) {
  const orgCode = String(context?.orgCode || '').trim(), schemeCode = String(context?.schemeCode || '').trim();
  const dataDate = String(context?.dataDate || '').trim();
  if (!orgCode || !schemeCode || !/^\d{4}-\d{2}-\d{2}$/.test(dataDate)) throw new Error('必须选择支行、考核方案和数据日期');
  const params = { orgCode, schemeCode, dataDate };
  const [org, emp] = await Promise.all([
    readAllPerformancePages(readPage, { ...params, subjectType: 'ORG' }, isCurrent),
    readAllPerformancePages(readPage, { ...params, subjectType: 'EMP' }, isCurrent)
  ]);
  return { orgMetrics: org.metrics, empMetrics: emp.metrics, orgRecords: org.records, empRecords: emp.records };
}
