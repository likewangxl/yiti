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

test("支部书记仅保留首页、支部审核工作台和预警池", () => {
  const reporterMenus = getVisibleMenuKeys("reporter");
  const secretaryMenus = getVisibleMenuKeys("branchSecretary");

  assert.deepEqual(
    secretaryMenus,
    ["home", "review", "warning"],
  );
  assert.equal(getDefaultView("branchSecretary"), "review");
  assert.equal(reporterMenus.includes("review"), false);
  assert.equal(secretaryMenus.includes("materials"), false);
  assert.equal(secretaryMenus.includes("records"), false);
  assert.equal(secretaryMenus.includes("processing"), false);
});

test("组织审核员保留组织管理能力并增加工作台，但不展示材料与上报信息", () => {
  const adminMenus = getVisibleMenuKeys("admin");
  const reviewerMenus = getVisibleMenuKeys("orgReviewer");

  assert.equal(reviewerMenus.includes("workbench"), true);
  assert.equal(reviewerMenus.includes("management"), true);
  assert.equal(reviewerMenus.includes("materials"), false);
  assert.equal(reviewerMenus.includes("records"), false);
  assert.equal(reviewerMenus.includes("processing"), false);
  assert.equal(reviewerMenus.includes("archive"), false);
  assert.equal(reviewerMenus.includes("export"), false);
  assert.equal(reviewerMenus.includes("warning"), true);
  assert.equal(getDefaultView("orgReviewer"), "workbench");
  assert.equal(adminMenus.includes("workbench"), false);
  assert.equal(adminMenus.includes("archive"), false);
  assert.equal(adminMenus.includes("export"), false);
  assert.equal(adminMenus.includes("management"), true);
});

test("原型使用报送员业务角色，首页待办入口与审核工作台分离", () => {
  const labels = ROLE_OPTIONS.map((role) => role.label);

  assert.equal(labels.includes("报送员"), true);
  assert.equal(labels.includes("一线员工"), false);
  assert.equal(getDefaultView("reporter"), "home");
  assert.equal(getVisibleMenuKeys("reporter").includes("home"), true);
  assert.equal(getVisibleMenuKeys("reporter").includes("review"), false);
  assert.deepEqual(getVisibleMenuKeys("reporter"), ["home", "materials", "records", "warning"]);
  assert.equal(getVisibleMenuKeys("admin").includes("processing"), false);
});
