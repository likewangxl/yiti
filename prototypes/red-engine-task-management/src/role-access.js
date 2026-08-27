const ADMIN_MENUS = [
  "home",
  "materials",
  "records",
  "dashboard",
  "warning",
  "archive",
  "export",
  "org",
  "mapping",
  "management",
];

const REPORTER_MENUS = ["home", "materials", "records", "warning"];
const BRANCH_SECRETARY_MENUS = ["home", "review", "warning"];
const ORG_REVIEWER_MENUS = [
  "home",
  "dashboard",
  "warning",
  "workbench",
  "archive",
  "export",
  "org",
  "mapping",
  "management",
];

export const ROLE_OPTIONS = [
  { value: "admin", label: "组织管理员" },
  { value: "reporter", label: "报送员" },
  { value: "branchSecretary", label: "支部书记" },
  { value: "orgReviewer", label: "组织审核员" },
];

const ROLE_MENU_KEYS = {
  admin: ADMIN_MENUS,
  reporter: REPORTER_MENUS,
  branchSecretary: BRANCH_SECRETARY_MENUS,
  orgReviewer: ORG_REVIEWER_MENUS,
};

const ROLE_DEFAULT_VIEWS = {
  admin: "management",
  reporter: "home",
  branchSecretary: "review",
  orgReviewer: "workbench",
};

export function getVisibleMenuKeys(role) {
  return [...(ROLE_MENU_KEYS[role] || [])];
}

export function getDefaultView(role) {
  return ROLE_DEFAULT_VIEWS[role] || "home";
}

export function getRoleLabel(role) {
  return ROLE_OPTIONS.find((item) => item.value === role)?.label || "未知身份";
}
