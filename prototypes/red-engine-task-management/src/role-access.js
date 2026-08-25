const ALL_STANDARD_MENUS = [
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
  "processing",
];

export const ROLE_OPTIONS = [
  { value: "admin", label: "组织管理员" },
  { value: "employee", label: "一线员工" },
  { value: "branchSecretary", label: "支部书记" },
  { value: "orgReviewer", label: "组织审核员" },
];

const ROLE_MENU_KEYS = {
  admin: ALL_STANDARD_MENUS,
  employee: ["home", "materials", "records", "processing"],
  branchSecretary: ["review"],
  orgReviewer: ["workbench"],
};

const ROLE_DEFAULT_VIEWS = {
  admin: "management",
  employee: "home",
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
