// 机构详细地址与定位管理 API。
// 位置能力、记录和解析预览均为真实数据边界，不提供 mock/fallback 兜底。
import { call } from './http';

function orgLocationPath(orgCode) {
  return `/admin/org-locations/${encodeURIComponent(String(orgCode))}`;
}

export function getOrgLocationCapabilities() {
  return call('get', '/admin/org-locations/capabilities', {});
}

export function getOrgLocation(orgCode) {
  return call('get', orgLocationPath(orgCode), {});
}

export function updateOrgLocation(orgCode, data) {
  return call('put', orgLocationPath(orgCode), { data });
}

export function previewOrgLocationGeocode(orgCode, data) {
  return call('post', `${orgLocationPath(orgCode)}/geocode-preview`, { data });
}

// 兼容语义相同的调用命名，实际仍指向同一严格 HTTP 契约。
export const getOrgLocationRecord = getOrgLocation;
export const previewOrgLocation = previewOrgLocationGeocode;
