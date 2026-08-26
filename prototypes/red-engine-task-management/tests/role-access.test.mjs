import test from "node:test";
import assert from "node:assert/strict";

import {
  ROLE_OPTIONS,
  getDefaultView,
  getVisibleMenuKeys,
} from "../src/role-access.js";

test("支部审核员合并为支部书记，演示身份中不再保留支部审核员", () => {
  const labels = ROLE_OPTIONS.map((role) => role.label);

  assert.equal(labels.includes("支部书记"), true);
  assert.equal(labels.includes("支部审核员"), false);
});

test("支部书记继承报送员权限并增加支部审核工作台", () => {
  const reporterMenus = getVisibleMenuKeys("reporter");
  const secretaryMenus = getVisibleMenuKeys("branchSecretary");

  assert.deepEqual(
    secretaryMenus,
    [...reporterMenus, "review"],
  );
  assert.equal(getDefaultView("branchSecretary"), "review");
  assert.equal(reporterMenus.includes("review"), false);
});

test("组织审核员继承组织管理员权限并增加工作台", () => {
  const adminMenus = getVisibleMenuKeys("admin");
  const reviewerMenus = getVisibleMenuKeys("orgReviewer");

  assert.deepEqual(
    reviewerMenus,
    [...adminMenus, "workbench"],
  );
  assert.equal(getDefaultView("orgReviewer"), "workbench");
  assert.equal(adminMenus.includes("workbench"), false);
});

test("原型使用报送员业务角色，首页待办入口与审核工作台分离", () => {
  const labels = ROLE_OPTIONS.map((role) => role.label);

  assert.equal(labels.includes("报送员"), true);
  assert.equal(labels.includes("一线员工"), false);
  assert.equal(getDefaultView("reporter"), "home");
  assert.equal(getVisibleMenuKeys("reporter").includes("home"), true);
  assert.equal(getVisibleMenuKeys("reporter").includes("review"), false);
});
