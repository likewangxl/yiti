/**
 * 机构画像管理页的缺口分析。
 *
 * 这里仅分析接口返回的行，不补齐、不改写也不推断机构主数据。坐标只有在
 * 成对、数值范围合法、坐标系为 GCJ02 且不属于未信任的 SCREEN_MAP_DEMO 标记时才算已定位。
 */

const PROFILE_FIELDS = ['version', 'version_no', 'orgNature', 'org_nature', 'operatingLevel', 'operating_level'];
const STATUS_FIELDS = ['status', 'recordStatus', 'record_status'];
const CITY_CODE_FIELDS = ['cityCode', 'city_code'];
const LONGITUDE_FIELDS = ['lng', 'longitude', 'lon', 'longitudinal', 'lng_value'];
const LATITUDE_FIELDS = ['lat', 'latitude', 'lat_value'];
const COORD_SYS_FIELDS = ['coordSys', 'coord_sys', 'coordinateSystem', 'coordinate_system'];
const LOCATION_SOURCE_FIELDS = ['locationSource', 'location_source'];
const TRUSTED_DEMO_LOCATION_SOURCES = new Set(['MANUAL', 'GEOCODE_VERIFIED']);

function pick(row, keys) {
  for (const key of keys) {
    if (row?.[key] !== undefined && row?.[key] !== null) return row[key];
  }
  return undefined;
}

function hasText(value) {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function hasCityCode(value) {
  return /^\d{6,}$/.test(String(value ?? '').trim());
}

function hasProfileValue(row) {
  return PROFILE_FIELDS.some(key => hasText(row?.[key]));
}

function normalizedStatus(value) {
  return typeof value === 'string' ? value.trim().toUpperCase() : value;
}

/** 画像状态缺失且无任何画像字段时才认定为未配置。 */
export function profileStatusOf(row = {}) {
  const status = pick(row, STATUS_FIELDS);
  const normalized = normalizedStatus(status);
  if (!hasText(status) && !hasProfileValue(row)) return 'UNCONFIGURED';
  if (normalized === 'ACTIVE' || normalized === 0 || normalized === '0') return 'ACTIVE';
  if (normalized === 'DISABLED' || normalized === 1 || normalized === '1') return 'DISABLED';
  return 'UNKNOWN';
}

function finiteCoordinate(value) {
  if (typeof value === 'boolean' || value === null || value === undefined) return null;
  if (typeof value === 'string' && value.trim() === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function normalizedCoordSys(value) {
  return String(value ?? '').trim().toUpperCase().replace(/[\s_-]/g, '');
}

function isScreenMapDemo(row) {
  const remark = pick(row, ['remark', 'remarks']);
  return String(remark ?? '').toUpperCase().includes('SCREEN_MAP_DEMO');
}

function addressOf(row) {
  if (hasText(row?.address)) return row.address;
  if (hasText(row?.location?.address)) return row.location.address;
  // 只有响应明确带出地址字段时，空值才代表“已知缺失”；A1 画像 DTO 没有
  // 地址字段，返回 undefined 让调用方保持 UNKNOWN，避免把每一行误报为缺口。
  if (row && Object.prototype.hasOwnProperty.call(row, 'address')) return row.address;
  if (row?.location && Object.prototype.hasOwnProperty.call(row.location, 'address')) return row.location.address;
  return undefined;
}

function trustedDemoLocationSource(row) {
  return TRUSTED_DEMO_LOCATION_SOURCES.has(String(pick(row, LOCATION_SOURCE_FIELDS) ?? '').trim().toUpperCase());
}

function demoCoordinateExcluded(row) {
  return isScreenMapDemo(row) && !trustedDemoLocationSource(row);
}

function hasValidCoordinate(row) {
  const lng = finiteCoordinate(pick(row, LONGITUDE_FIELDS));
  const lat = finiteCoordinate(pick(row, LATITUDE_FIELDS));
  return !demoCoordinateExcluded(row)
    && lng !== null && lat !== null
    && lng >= -180 && lng <= 180
    && lat >= -90 && lat <= 90
    && normalizedCoordSys(pick(row, COORD_SYS_FIELDS)) === 'GCJ02';
}

function analyzeEntry(row = {}) {
  const profileStatus = profileStatusOf(row);
  // 城市名称只是展示文案；没有可用地市码时不能把机构归入地图城市。
  const hasCity = hasCityCode(pick(row, CITY_CODE_FIELDS));
  const located = hasValidCoordinate(row);
  const address = addressOf(row);
  const hasAddress = address === undefined ? null : hasText(address);
  const issues = [];

  if (profileStatus === 'UNCONFIGURED') issues.push('UNCONFIGURED');
  if (profileStatus === 'UNKNOWN') issues.push('UNKNOWN_STATUS');
  if (!located) issues.push('MISSING_COORDINATES');
  if (demoCoordinateExcluded(row)) issues.push('SCREEN_MAP_DEMO');
  if (!hasCity) issues.push('MISSING_CITY');
  if (hasAddress === false) issues.push('MISSING_ADDRESS');

  return {
    orgCode: pick(row, ['orgCode', 'org_code']),
    orgName: pick(row, ['orgName', 'org_name']),
    profileStatus,
    hasCity,
    located,
    hasAddress,
    issues
  };
}

/**
 * 分析当前机构查询结果的画像准备度。
 *
 * @param {Array<object>} rows 接口原始行；函数不会修改输入对象或数组
 * @returns {{total:number,profileConfigured:number,missingProfile:number,enabled:number,disabled:number,unknownStatus:number,located:number,missingCoordinates:number,missingCity:number,missingAddress:number,entries:Array<object>}}
 */
export function analyzeOrgProfiles(rows) {
  const source = Array.isArray(rows) ? rows : [];
  const entries = source.map(row => analyzeEntry(row || {}));
  const count = predicate => entries.filter(predicate).length;
  const missingProfile = count(entry => entry.profileStatus === 'UNCONFIGURED');
  const unknownStatus = count(entry => entry.profileStatus === 'UNKNOWN');
  const located = count(entry => entry.located);

  return {
    total: entries.length,
    profileConfigured: entries.length - missingProfile,
    missingProfile,
    enabled: count(entry => entry.profileStatus === 'ACTIVE'),
    disabled: count(entry => entry.profileStatus === 'DISABLED'),
    unknownStatus,
    located,
    missingCoordinates: entries.length - located,
    missingCity: count(entry => !entry.hasCity),
    // 地址不在机构画像列表 DTO 中；只有显式返回空地址时才计入，缺失字段保持 UNKNOWN。
    missingAddress: count(entry => entry.hasAddress === false),
    entries
  };
}
