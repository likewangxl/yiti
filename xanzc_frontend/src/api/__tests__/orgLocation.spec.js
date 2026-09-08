import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../http', () => ({ call: vi.fn() }));

import { call } from '../http';
import {
  getOrgLocationCapabilities,
  getOrgLocation,
  updateOrgLocation,
  previewOrgLocationGeocode
} from '../orgLocation';

describe('机构地址与定位 API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('能力读取不带 fallback，并使用 capabilities 资源', async () => {
    call.mockResolvedValueOnce({ storageEnabled: false });

    await getOrgLocationCapabilities();

    expect(call).toHaveBeenCalledWith('get', '/admin/org-locations/capabilities', {});
  });

  it('记录读取编码机构编码且不把失败降级成空记录', async () => {
    const failure = new Error('forbidden');
    call.mockRejectedValueOnce(failure);

    await expect(getOrgLocation('机构/一号')).rejects.toBe(failure);
    expect(call).toHaveBeenCalledWith('get', '/admin/org-locations/%E6%9C%BA%E6%9E%84%2F%E4%B8%80%E5%8F%B7', {});
  });

  it('保存只透传调用方构造的请求体到 PUT', async () => {
    const body = {
      address: '西安市雁塔区示例路 1 号',
      cityCode: '610100',
      lng: 108.9,
      lat: 34.2,
      coordSys: 'GCJ02',
      manualConfirmed: true,
      version: 0,
      reason: '补录机构地址'
    };
    call.mockResolvedValueOnce({ ...body, orgCode: 'ORG_1', version: 1 });

    await updateOrgLocation('ORG_1', body);

    expect(call).toHaveBeenCalledWith('put', '/admin/org-locations/ORG_1', { data: body });
  });

  it('地址解析预览只在显式调用时 POST 地址、城市和原因', async () => {
    const body = { address: '西安市雁塔区示例路 1 号', cityCode: '610100', reason: '核对候选点' };
    call.mockResolvedValueOnce([]);

    await previewOrgLocationGeocode('ORG_1', body);

    expect(call).toHaveBeenCalledWith('post', '/admin/org-locations/ORG_1/geocode-preview', { data: body });
  });
});
