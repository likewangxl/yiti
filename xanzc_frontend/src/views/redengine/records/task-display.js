/**
 * 任务填报页面的展示口径。
 *
 * 业务类型和任务性质是两个独立维度：GENERAL 只表示普通任务，不能据此
 * 将周期任务误标为临时任务。这里兼容当前接口值以及历史/联调数据中的
 * RECURRING 别名，避免列表和详情页各自维护一套判断。
 */
const PERIODIC_NATURES = new Set(['PERIODIC', 'SCHEDULED', 'RECURRING'])

export function isPeriodicTaskNature(value) {
  return PERIODIC_NATURES.has(String(value || '').trim().toUpperCase())
}

export function taskNatureLabel(value) {
  if (isPeriodicTaskNature(value)) return '定时任务'
  if (String(value || '').trim().toUpperCase() === 'TEMPORARY') return '临时任务'
  return value || '—'
}

export function taskEntryTitle(value) {
  return `${taskNatureLabel(value)}填报`
}

export function taskDimensionLabel(value) {
  return isPeriodicTaskNature(value) ? '普通任务' : '临时任务'
}
