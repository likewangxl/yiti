const SLA = { BLUE: '蓝灯', GREEN: '蓝灯', YELLOW: '黄灯', RED: '红灯' };
const TASK_STATUS = {
  PENDING: '待办理',
  IN_PROGRESS: '办理中',
  SUCCESS: '已完成',
  CANCELLED: '已取消'
};

export const slaLabel = value => SLA[value] || value || '-';
export const taskStatusLabel = value => TASK_STATUS[value] || value || '-';

export function normalizePhotoGroups(value) {
  const empty = { keyPerson: [], doorplate: [], workplace: [] };
  if (!value) return empty;
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return {
      keyPerson: Array.isArray(parsed?.keyPerson) ? parsed.keyPerson : [],
      doorplate: Array.isArray(parsed?.doorplate) ? parsed.doorplate : [],
      workplace: Array.isArray(parsed?.workplace) ? parsed.workplace : []
    };
  } catch {
    return empty;
  }
}

export const slaTagType = value => ({ RED: 'danger', YELLOW: 'warning', BLUE: 'primary', GREEN: 'primary' }[value] || 'info');
export const taskTagType = value => ({ SUCCESS: 'success', CANCELLED: 'info', IN_PROGRESS: 'warning', PENDING: 'primary' }[value] || 'info');
