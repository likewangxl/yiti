const TRUE_VALUES = new Set(['1', 'TRUE', 'YES', 'Y', 'ACTIVE', 'ENABLED', 'AUTHORIZED', 'ALLOW', 'ALLOWED']);
const FALSE_VALUES = new Set([
  '0', 'FALSE', 'NO', 'N', 'INACTIVE', 'DISABLED', 'STOPPED', 'OFF',
  'UNAUTHORIZED', 'NOT_AUTHORIZED', 'DENIED', 'FORBIDDEN', 'REVOKED', 'REJECTED',
  'NO_ACCESS', 'EXPIRED'
]);

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function text(value) {
  return value === null || value === undefined ? '' : String(value).trim();
}

function valueOf(source, ...keys) {
  if (!object(source)) return undefined;
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null) return source[key];
  }
  return undefined;
}

function number(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const result = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(result) ? result : null;
}

function flag(value, defaultValue = true) {
  if (value === undefined || value === null || value === '') return { value: defaultValue, explicit: false };
  if (typeof value === 'boolean') return { value, explicit: true };
  if (typeof value === 'number') return { value: Number.isFinite(value) && value !== 0, explicit: true };
  const normalized = text(value).toUpperCase();
  if (FALSE_VALUES.has(normalized)) return { value: false, explicit: true };
  if (TRUE_VALUES.has(normalized)) return { value: true, explicit: true };
  return { value: defaultValue, explicit: true };
}

function copyMetrics(value) {
  if (!object(value)) return {};
  return { ...value };
}

function hasMetricValue(value) {
  if (value === null || value === undefined || value === '') return false;
  if (typeof value === 'number') return Number.isFinite(value);
  if (typeof value === 'boolean') return true;
  if (Array.isArray(value)) return value.some(hasMetricValue);
  if (object(value)) return Object.values(value).some(hasMetricValue);
  return true;
}

function validCoordinate(lng, lat, coordSys) {
  return Number.isFinite(lng) && Number.isFinite(lat)
    && lng >= -180 && lng <= 180
    && lat >= -90 && lat <= 90
    && text(coordSys).toUpperCase().replace(/[\s_-]/g, '') === 'GCJ02';
}

function listRule(rules, ...keys) {
  for (const key of keys) {
    if (Array.isArray(rules?.[key])) return rules[key].map(text).filter(Boolean);
  }
  return [];
}

function ruleSet(values) {
  return new Set(values.map(value => text(value).toUpperCase()));
}

function recordIdentity(raw = {}) {
  return {
    orgCode: text(valueOf(raw, 'orgCode', 'org_code')),
    orgName: text(valueOf(raw, 'orgName', 'org_name')) || null,
    cityCode: text(valueOf(raw, 'cityCode', 'city_code')) || null,
    cityName: text(valueOf(raw, 'cityName', 'city_name')) || null,
    ownerOperatingOrgCode: text(valueOf(raw, 'ownerOperatingOrgCode', 'owner_operating_org_code')) || null,
    parentOrgCode: text(valueOf(raw, 'parentOrgCode', 'parent_org_code')) || null,
    operatingLevel: text(valueOf(raw, 'operatingLevel', 'operating_level')) || null,
    orgNature: text(valueOf(raw, 'orgNature', 'org_nature')) || null,
    locationSource: text(valueOf(raw, 'locationSource', 'location_source')) || null
  };
}

function normalizeRecord(raw = {}, contribution = null) {
  const identity = recordIdentity(raw);
  const active = flag(valueOf(raw, 'active', 'isActive', 'enabled', 'is_enabled', 'status'), true);
  const authorized = flag(valueOf(raw, 'authorized', 'isAuthorized', 'hasAccess', 'accessGranted',
    'permissionGranted', 'hasPermission', 'isPermitted', 'permission', 'access',
    'permissionStatus', 'authorizationStatus', 'authStatus'), true);
  const lng = number(valueOf(raw, 'lng', 'longitude'));
  const lat = number(valueOf(raw, 'lat', 'latitude'));
  const coordSys = text(valueOf(raw, 'coordSys', 'coord_sys')) || null;
  const locatedFlag = flag(valueOf(raw, 'located'), false).value;
  const located = locatedFlag && validCoordinate(lng, lat, coordSys);
  const metrics = {
    ...copyMetrics(valueOf(raw, 'metrics', 'metricValues', 'values')),
    ...copyMetrics(valueOf(contribution, 'metrics', 'metricValues', 'values'))
  };
  return {
    ...identity,
    lng: located ? lng : null,
    lat: located ? lat : null,
    coordSys,
    located,
    locationStatus: located ? 'LOCATED' : 'NO_COORDINATE',
    active: active.value,
    authorized: authorized.value,
    metrics,
    status: hasMetricValue(metrics) ? 'READY' : 'NO_METRIC',
    contributionStatus: hasMetricValue(metrics) ? 'READY' : 'UNKNOWN',
    aggregateAllowed: true,
    duplicateName: false
  };
}

function contributionEntries(value) {
  if (Array.isArray(value)) return value.map(item => ({ code: text(valueOf(item, 'orgCode', 'org_code')), item }));
  if (!object(value)) return [];
  return Object.entries(value).map(([code, item]) => ({ code: text(code), item: object(item) ? item : { metrics: item } }));
}

function addIssue(issues, code, orgCode, message) {
  issues.push({ code, orgCode: orgCode || undefined, message });
}

/**
 * 构造经营大屏机构展示集合。
 *
 * 机构身份只来自服务端授权目录；metrics 仅作为目录记录自身或同目录编码的
 * 已授权结果补充。展示过滤不会修改上级指标，也不会从名称或编码推断机构属性。
 */
export function buildInstitutionViewModel(input = [], rules = {}, options = {}) {
  let directory = input;
  let config = rules;
  let opts = options;
  if (object(input) && !Array.isArray(input)) {
    const directoryKey = ['authorizedDirectory', 'panoramaInstitutions', 'panorama_institutions', 'directory', 'institutions']
      .find(key => Object.prototype.hasOwnProperty.call(input, key));
    directory = directoryKey ? input[directoryKey] : null;
    // New runtime authorization is supplied by ScreenRenderRespDTO only.
    // The legacy navigationRules field must not silently become a display rule.
    config = input.institutionRules ?? rules ?? {};
    opts = { ...input, ...options };
  }
  const issues = [];
  if (!Array.isArray(directory)) {
    addIssue(issues, 'AUTHORIZED_DIRECTORY_REQUIRED', '', '机构展示必须使用服务端授权目录');
    return {
      status: 'UNCONFIRMED', source: 'SERVER_AUTHORIZED_DIRECTORY',
      displayInstitutions: [], contributionUnknown: [], issues
    };
  }

  config = object(config?.institutionRules)
    ? config.institutionRules
    : (object(config?.filters) ? config.filters : (object(config?.rules) ? config.rules : config));
  const allowedLevels = listRule(config, 'allowedOperatingLevels', 'allowed_operating_levels');
  const allowedNatures = listRule(config, 'allowedOrgNatures', 'allowed_org_natures');
  const levelRule = ruleSet(allowedLevels);
  const natureRule = ruleSet(allowedNatures);
  const hasRules = levelRule.size > 0 && natureRule.size > 0;
  const contributionUnknown = [];
  const unknownKeys = new Set();
  const addUnknown = (record, reason, message) => {
    const orgCode = text(record?.orgCode || valueOf(record, 'orgCode', 'org_code'));
    const key = `${orgCode}|${reason}`;
    if (unknownKeys.has(key)) return;
    unknownKeys.add(key);
    contributionUnknown.push({
      orgCode: orgCode || null,
      orgName: text(record?.orgName || valueOf(record, 'orgName', 'org_name')) || null,
      reason,
      message
    });
  };

  if (!hasRules) addIssue(issues, 'FILTER_RULES_UNCONFIRMED', '', '未提供允许的机构层级或机构性质白名单，拒绝猜测展示集合');

  const contributions = new Map();
  for (const entry of contributionEntries(opts.contributions ?? opts.metricsByOrgCode
    ?? opts.institutionMetrics ?? config.contributions ?? config.metricsByOrgCode)) {
    if (!entry.code) continue;
    const previous = contributions.get(entry.code);
    if (previous) addIssue(issues, 'DUPLICATE_CONTRIBUTION', entry.code, '同一机构存在重复指标记录，按稳定顺序合并');
    const currentMetrics = copyMetrics(valueOf(entry.item, 'metrics', 'metricValues', 'values'));
    contributions.set(entry.code, { ...(previous || {}), ...entry.item, metrics: { ...(previous?.metrics || {}), ...currentMetrics } });
  }

  const seenCodes = new Set();
  const displayInstitutions = [];
  const sourceByCode = new Map();
  for (const raw of directory) {
    const identity = recordIdentity(raw);
    if (!identity.orgCode) {
      addUnknown(raw, 'ORG_CODE_MISSING', '机构目录缺少机构编码，已拒绝展示');
      addIssue(issues, 'ORG_CODE_MISSING', '', '机构目录缺少机构编码');
      continue;
    }
    if (seenCodes.has(identity.orgCode)) {
      addIssue(issues, 'DUPLICATE_ORG_CODE', identity.orgCode, '机构目录存在重复机构编码，未生成重复展示节点');
      continue;
    }
    seenCodes.add(identity.orgCode);
    sourceByCode.set(identity.orgCode, raw);
    const contribution = contributions.get(identity.orgCode);
    const item = normalizeRecord(raw, contribution);

    if (!item.authorized) {
      addUnknown(item, 'UNAUTHORIZED', '机构未获当前屏授权');
      addIssue(issues, 'UNAUTHORIZED', item.orgCode, '机构未获当前屏授权');
      continue;
    }
    if (!item.active) {
      addUnknown(item, 'INACTIVE', '机构已停用');
      addIssue(issues, 'INACTIVE', item.orgCode, '机构已停用');
      continue;
    }
    if (!hasRules) {
      addUnknown(item, 'FILTER_RULES_UNCONFIRMED', '机构过滤规则未确认');
      continue;
    }
    if (levelRule.size > 0) {
      if (!item.operatingLevel) {
        addUnknown(item, 'OPERATING_LEVEL_MISSING', '机构缺少经营层级，无法按白名单过滤');
        addIssue(issues, 'OPERATING_LEVEL_MISSING', item.orgCode, '机构缺少经营层级');
        continue;
      }
      if (!levelRule.has(item.operatingLevel.toUpperCase())) {
        addUnknown(item, 'OPERATING_LEVEL_NOT_ALLOWED', '机构经营层级不在允许集合');
        addIssue(issues, 'OPERATING_LEVEL_NOT_ALLOWED', item.orgCode, '机构经营层级不在允许集合');
        continue;
      }
    }
    if (natureRule.size > 0) {
      if (!item.orgNature) {
        addUnknown(item, 'ORG_NATURE_MISSING', '机构缺少机构性质，无法按白名单过滤');
        addIssue(issues, 'ORG_NATURE_MISSING', item.orgCode, '机构缺少机构性质');
        continue;
      }
      if (!natureRule.has(item.orgNature.toUpperCase())) {
        addUnknown(item, 'ORG_NATURE_NOT_ALLOWED', '机构性质不在允许集合');
        addIssue(issues, 'ORG_NATURE_NOT_ALLOWED', item.orgCode, '机构性质不在允许集合');
        continue;
      }
    }
    displayInstitutions.push(item);
  }

  for (const [orgCode, contribution] of contributions.entries()) {
    if (!sourceByCode.has(orgCode)) {
      addIssue(issues, 'UNAUTHORIZED_CONTRIBUTION', orgCode, '指标记录不属于服务端授权目录，已拒绝');
    }
  }

  const nameCounts = new Map();
  for (const item of displayInstitutions) {
    if (item.orgName) nameCounts.set(item.orgName, (nameCounts.get(item.orgName) || 0) + 1);
  }
  for (const item of displayInstitutions) {
    item.duplicateName = Boolean(item.orgName && nameCounts.get(item.orgName) > 1);
  }

  const codeSet = new Set(displayInstitutions.map(item => item.orgCode));
  const overlapMap = new Map();
  const markOverlap = (orgCode, reason, message) => {
    const reasons = overlapMap.get(orgCode) || new Set();
    reasons.add(reason);
    overlapMap.set(orgCode, reasons);
    const item = displayInstitutions.find(candidate => candidate.orgCode === orgCode);
    if (item) {
      item.contributionStatus = 'CANNOT_AGGREGATE';
      item.aggregateAllowed = false;
      addUnknown(item, reason, message);
    }
  };
  for (const item of displayInstitutions) {
    const parent = text(item.parentOrgCode);
    if (parent && parent !== item.orgCode && codeSet.has(parent)) {
      markOverlap(item.orgCode, 'PARENT_CHILD_OVERLAP', '父子机构同时展示，不能现场汇总');
      markOverlap(parent, 'PARENT_CHILD_OVERLAP', '父子机构同时展示，不能现场汇总');
      addIssue(issues, 'PARENT_CHILD_OVERLAP', item.orgCode, `父机构 ${parent} 与子机构同时展示，不能现场汇总`);
    }
    const owner = text(item.ownerOperatingOrgCode);
    if (owner && owner !== item.orgCode && codeSet.has(owner)) {
      markOverlap(item.orgCode, 'OPERATING_OWNERSHIP_OVERLAP', '经营归属机构同时展示，不能现场汇总');
      markOverlap(owner, 'OPERATING_OWNERSHIP_OVERLAP', '经营归属机构同时展示，不能现场汇总');
      addIssue(issues, 'OPERATING_OWNERSHIP_OVERLAP', item.orgCode, `经营归属机构 ${owner} 与当前节点同时展示，不能现场汇总`);
    }
  }

  return {
    status: hasRules ? 'READY' : 'UNCONFIRMED',
    source: 'SERVER_AUTHORIZED_DIRECTORY',
    displayInstitutions,
    contributionUnknown,
    issues,
    filters: {
      allowedOperatingLevels: [...allowedLevels],
      allowedOrgNatures: [...allowedNatures]
    },
    counts: {
      authorized: displayInstitutions.length,
      withMetrics: displayInstitutions.filter(item => item.status === 'READY').length,
      missingMetrics: displayInstitutions.filter(item => item.status === 'NO_METRIC').length,
      contributionUnknown: contributionUnknown.length
    }
  };
}

export const buildInstitutionDisplayModel = buildInstitutionViewModel;
export const filterAuthorizedInstitutions = buildInstitutionViewModel;

export default buildInstitutionViewModel;
