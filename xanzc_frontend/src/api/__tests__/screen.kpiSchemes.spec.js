// KPI 方案下拉封装（spec 2026-07-17 §3.1）：GET /api/screen/admin/kpi-schemes → [{schemeCode, schemeName}]
// 只校验 call() 的调用参数（方法/URL/fallback），同 screen.spec.js 约定。
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue([{ schemeCode: 'KPI_2026_STD', schemeName: '标准方案' }]) }));

import { call } from '../http';
import { listKpiSchemes } from '../screen';

describe('listKpiSchemes', () => {
  beforeEach(() => call.mockClear());

  it('GET /screen/admin/kpi-schemes，失败兜底空数组（下拉宁空不假）', async () => {
    const r = await listKpiSchemes();
    expect(call).toHaveBeenCalledWith('get', '/screen/admin/kpi-schemes', {}, []);
    expect(r).toEqual([{ schemeCode: 'KPI_2026_STD', schemeName: '标准方案' }]);
  });
});
