// updateSavedQuery 必须把乐观锁字段以后端契约名 expectedVersion 下发(修 version→expectedVersion bug)
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn().mockResolvedValue({ ok: true }) }));

import { call } from '../http';
import { updateSavedQuery } from '../report';

describe('updateSavedQuery 乐观锁字段名', () => {
  beforeEach(() => call.mockClear());

  it('把 version 以 expectedVersion 字段下发,并序列化 metrics/subjects', () => {
    updateSavedQuery('SQ1', {
      name: '新名', dim: 'EMP', version: 3,
      metrics: ['M0001', 'M0002'],
      subjects: [{ id: 'E1', name: '张三', org: '总行' }]
    });
    const [method, url, config] = call.mock.calls[0];
    expect(method).toBe('put');
    expect(url).toBe('/reports/saved-queries/SQ1');
    expect(config.data.expectedVersion).toBe(3);
    expect(config.data).not.toHaveProperty('version');
    expect(config.data.metricCodes).toBe(JSON.stringify(['M0001', 'M0002']));
    expect(JSON.parse(config.data.subjectIds)).toEqual([{ id: 'E1', name: '张三', org: '总行' }]);
  });

  it('对象为空数组也下发 subjectIds:"[]"(编辑允许对象为空)', () => {
    updateSavedQuery('SQ1', { name: 'x', dim: 'EMP', expectedVersion: 5, metrics: ['M1'], subjects: [] });
    const config = call.mock.calls[0][2];
    expect(config.data.subjectIds).toBe('[]');
    expect(config.data.expectedVersion).toBe(5);
  });
});
