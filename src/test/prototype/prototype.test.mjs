import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const testDir = path.dirname(fileURLToPath(import.meta.url));
const prototypeDir = path.resolve(testDir, "../../main/resources/static/prototype");
const templateDir = path.resolve(testDir, "../../main/resources/templates");
const require = createRequire(import.meta.url);
const curveMath = require(path.join(prototypeDir, "curve-math.js"));
const [html, css, js, loginHtml] = await Promise.all([
    readFile(path.join(prototypeDir, "index.html"), "utf8"),
    readFile(path.join(prototypeDir, "styles.css"), "utf8"),
    readFile(path.join(prototypeDir, "app.js"), "utf8"),
    readFile(path.join(templateDir, "login.html"), "utf8")
]);

for (const page of ["work", "settings", "values", "shaft-curves", "main-curves", "records", "alarms", "users"]) {
    assert.match(html, new RegExp(`data-page="${page}"`), `缺少页面：${page}`);
}

for (const requiredText of ["轴系工作界面", "轴系设置界面", "显示数值界面", "轴系曲线界面", "报警界面"]) {
    assert.ok(html.includes(requiredText), `缺少原需求模块文字：${requiredText}`);
}

assert.ok(js.includes("pointerdown") && js.includes("pointermove"), "曲线端点拖拽事件未实现");
assert.ok(js.includes("convert") || js.includes("xValue"), "拖拽坐标未转换为数据坐标");
assert.ok(css.includes("prefers-reduced-motion"), "缺少减少动态效果的可访问性处理");
assert.ok(!html.includes("http://") && !html.includes("https://"), "原型不应依赖外部资源");
assert.ok(js.includes('["总值", "kN", "totalValue", "0.01"]'), "每个通道应提供只记录的总值字段");
assert.ok(html.includes("仅管理员可调整"), "曲线页应明确只有管理员可调整");
assert.ok(html.includes("采样点平滑显示"), "曲线页应明确标识采样点之间采用平滑显示");
assert.ok(html.includes('data-role="ADMIN"'), "原型应显式标识当前管理员角色");
assert.ok(js.includes('dataset.role === "ADMIN"'), "曲线拖动必须经过管理员角色判断");
assert.ok(js.includes('apiFetch("/api/session")'), "页面应从后端读取登录角色");
assert.ok(js.includes('apiFetch("/api/configs/1")'), "设置页应从后端读取六点参数");
assert.ok(html.includes('id="settingsChannelTabs"') && html.includes('id="settingsForm"'), "设置页应按通道提供 Tab 和单通道表单");
assert.ok(!html.includes('id="settingsMatrix"') && !html.includes('id="settingsChannelSelector"'), "设置页不应继续显示六通道矩阵和重复选择器");
assert.ok(js.includes('/api/configs/1/points/'), "设置页应只保存当前通道参数");
assert.ok(js.includes('input.value.trim() === ""') && js.includes('不能为空。'),
        "设置页保存前必须拦截任一空参数");
assert.ok(html.includes('id="settingsNotice" role="status" aria-live="polite"'),
        "设置页保存反馈应支持无障碍状态播报");
assert.ok(js.includes('showSettingsNotice("success"') && js.includes('showSettingsNotice("error"')
        && css.includes(".notice-success") && css.includes(".notice-error"),
        "设置页应明确区分保存成功和失败状态");
assert.ok(js.includes("settingsSaving") && js.includes("finally"),
        "保存期间应阻止重复提交并在结束后恢复按钮");
assert.ok(!html.includes("设备参数未提供") && !html.includes("pending-panel"),
        "设置页不应继续展示开发阶段待确认说明");
assert.ok(js.includes('totalValue'), "设置页应记录每个通道的总值");
assert.ok(js.includes('apiFetch("/api/control/state")'), "工作页应读取后端权威控制状态");
assert.ok(js.includes('apiFetch("/api/control/commands"'), "工作页应通过统一接口发送控制命令");
assert.ok(js.includes("new EventSource"), "工作页应通过 SSE 接收实时状态");
assert.ok(js.includes('/api/points/${selected.pointCode}/events'),
        "无运行记录的手动操作应按通道订阅实时传感器数据");
assert.ok(html.includes('id="manualModeButton"') && html.includes('id="autoModeButton"'), "工作页应分离手动和自动模式");
assert.ok(html.includes('id="manualStartButton"'), "手动模式应提供独立启动按钮");
assert.ok(html.includes('id="saveManualTargetButton"') && js.includes('/manual-target'), "手动目标位移应支持按通道保存");
assert.ok(html.includes('id="manualPauseButton"') && html.includes('id="autoPauseButton"'), "手动和自动动作均应支持暂停继续");
assert.ok(html.includes('id="manualStopButton"') && html.includes('id="autoStopButton"'), "手动和自动动作均应支持停止");
assert.ok(!html.includes('data-reading="pressure"') && !html.includes("顶升压力"), "实时数据区不应显示顶升压力");
assert.ok(!js.includes('"maxPressure"') && !html.includes("压力上限"), "设置页和工作页不应提供压力上限参数");
assert.ok(js.includes('["力值间隔", "kN", "forceIntervalKn", "1"]')
        && js.includes('力值间隔必须是 1～32767 kN 的整数'),
"设置页应以 PLC INT 范围配置 kN 力值间隔");
assert.ok(js.includes("controlCommandPending") && js.includes("指令下发中，请等待 PLC 确认"),
        "控制命令等待 PLC 反馈期间应防止重复提交并显示等待状态");
assert.ok(!js.includes('"压力间隔"'), "设置页不应继续使用 MPa 压力间隔");
assert.ok(html.includes('id="valueChannelStrip"'), "显示数值页应支持选择六个通道");
assert.ok(html.includes('id="valueTaskSelect"'), "显示数值页应提供已完成测量任务下拉框");
assert.ok(html.includes('id="saveResultButton"') && html.includes('id="deleteResultButton"'), "显示数值页应提供保存和删除操作");
assert.ok(!html.includes('id="resultEmptyState"'), "显示数值页不应重复保留大面积无结果占位区");
assert.ok(!html.includes('id="resultMetrics" hidden'), "六项计算结果应始终占据唯一的数据展示位置");
assert.ok(!js.includes('q("#resultEmptyState")'), "无结果状态应写入标题栏并清空结果卡片");
assert.ok(js.includes('/api/runs/${runId}/result'), "显示数值页应按所选运行读取结果");
assert.ok(js.includes('/result/save') && js.includes('method: "DELETE"'), "结果保存和逻辑删除接口未接入");
assert.ok(html.includes('id="adjustmentCoefficientCard" hidden')
        && html.includes("调整系数 · 仅管理员"), "调整系数必须默认隐藏并明确仅管理员可见");
assert.ok(js.includes('/result/adjustment-coefficient') && js.includes('method: "PATCH"'),
        "管理员调整系数修改接口未接入");
assert.ok(!html.includes("上升压力") && !html.includes("下降压力"), "曲线页表格不应重复展示压力数据");
assert.ok(html.includes('id="shaftCurveTaskSelect"') && html.includes('id="mainCurveTaskSelect"'), "两个曲线页均应提供已完成测量任务下拉框");
for (const searchId of ["valueTaskSearch", "shaftCurveTaskSearch", "mainCurveTaskSearch"]) {
    assert.ok(html.includes(`id="${searchId}"`), `任务选择区缺少模糊搜索框：${searchId}`);
}
assert.ok(js.includes('function filterTaskOptions(selectId, keyword)')
        && js.includes('taskSearchText(task).includes(normalizedKeyword)')
        && js.includes('input.addEventListener("input"'),
        "三个任务下拉列表应复用任务名称、编号和完成时间的包含匹配逻辑");
assert.ok(js.includes('/api/records/completed?pointCode='), "曲线页应按通道读取已完成测量任务");
assert.ok(js.includes('/api/runs/${runId}/curve-fit') && js.includes('container.setFitState = fit =>'),
        "曲线页重新选择任务时应回显已保存的人工拟合端点");
assert.ok((html.match(/data-chart-notice=/g) || []).length === 2
        && html.includes('class="notice notice-info curve-save-notice"'),
        "两个曲线页均应提供调整结果保存状态提示");
assert.ok(js.includes('showCurveSaveNotice(chartId, "success"')
        && js.includes('showCurveSaveNotice(chartId, "error"') && js.includes("finally"),
        "曲线调整保存应显示成功或失败信息并恢复按钮状态");
assert.ok(js.includes('row.state === "COMPLETED"') && js.includes('data-record-run='),
        "测量记录页只能为完整完成的任务提供查看按钮");
assert.ok(html.includes('id="recordPreviousPage"') && html.includes('id="recordNextPage"')
        && html.includes('id="recordPageInfo"'),
        "测量记录页应提供上一页、下一页和当前页信息");
assert.ok(js.includes('params.set("page", String(page))')
        && js.includes('params.set("size", String(RECORD_PAGE_SIZE))')
        && js.includes('params.set("keyword", keyword)'),
        "测量记录页应将页码、每页数量和搜索条件提交给后端分页接口");
for (const alarmTab of ["ACTIVE", "PROCESSING", "CLOSED"]) {
    assert.ok(html.includes(`data-alarm-group="${alarmTab}"`), `报警页缺少状态页签：${alarmTab}`);
}
for (const alarmFilter of ["alarmKeyword", "alarmLevel", "alarmStartTime", "alarmEndTime"]) {
    assert.ok(html.includes(`id="${alarmFilter}"`), `报警页缺少筛选条件：${alarmFilter}`);
}
assert.ok(html.includes('id="alarmPreviousPage"') && html.includes('id="alarmNextPage"')
        && html.includes('id="alarmPageInfo"'), "报警页应提供服务端分页控件");
assert.ok(html.includes('id="alarmNotification"') && html.includes('aria-live="assertive"'),
        "新报警应使用高优先级红色实时提示");
assert.ok(js.includes('new EventSource("/api/alarms/events")')
        && js.includes('addEventListener("alarm"') && js.includes("playAlarmSound"),
        "页面应通过 SSE 接收新报警并播放一次声音");
const initSource = js.slice(js.indexOf("async function init()"), js.indexOf("init().catch"));
assert.ok(initSource.indexOf("connectAlarmEvents();") < initSource.indexOf("await loadSession();"),
        "报警 SSE 应在其他初始化请求之前连接，不能因列表或控制状态加载失败而失效");
assert.ok(js.includes('params.set("statusGroup", alarmGroup)')
        && js.includes('params.set("alarmLevel"') && js.includes('params.set("startTime"')
        && js.includes('params.set("endTime"'), "报警查询应提交状态、级别和发生时间范围");
assert.ok(!js.includes('setInterval(() => loadAlarms'), "报警列表不得定时自动刷新");
assert.ok(html.includes('id="userManagementNavButton" hidden')
        && html.includes('id="changePasswordButton"'), "用户管理入口应仅向管理员显示，所有用户应能打开修改密码对话框");
for (const dialogId of ["createUserDialog", "editUserRolesDialog", "resetUserPasswordDialog", "changePasswordDialog"]) {
    assert.ok(html.includes(`id="${dialogId}"`), `缺少用户管理对话框：${dialogId}`);
}
assert.ok(js.includes('sessionContext.roles.includes("ADMIN")')
        && js.includes('q("#userManagementNavButton").hidden'), "用户管理入口必须根据登录角色控制显示");
for (const endpoint of ['/api/users"', '/api/users/roles', '/roles`', '/password`', '/api/users/me/password']) {
    assert.ok(js.includes(endpoint), `用户管理页面缺少接口调用：${endpoint}`);
}
assert.ok(js.includes('newPassword !== confirmPassword')
        && js.includes('location.href = "/login?passwordChanged"'), "密码表单应校验二次输入并在本人改密后重新登录");
assert.ok(js.includes("async function apiFetch") && js.includes("response.status === 401")
        && js.includes('location.replace("/login?expired")'), "任一 API 返回 401 时应统一退出并跳转登录页");
assert.ok(loginHtml.includes('th:if="${param.expired}"') && loginHtml.includes("登录已过期"),
        "登录页应明确提示会话已过期");
assert.ok(js.includes('const yValue = pixel =>')
        && js.includes('index === 0 ? state[kind][index][1] : yValue(pixelY)'),
        "拟合线上端点应支持上下左右拖动，下端点应继续锁定在横轴");
assert.ok(html.includes("上端点可上下左右拖动 · 下端点仅可左右拖动"),
        "曲线页应明确提示上下端点不同的拖动范围");
assert.ok(html.includes('id="exportCurveCsvButton"')
        && js.includes('q("#exportCurveCsvButton").addEventListener("click", exportCurveCsv)')
        && js.includes('new Blob(["\\uFEFF", csv]'),
        "曲线页导出按钮应将当前任务采样生成带 UTF-8 BOM 的 CSV 文件");
assert.ok(js.includes('state.down = JSON.parse(JSON.stringify(defaults.down))')
        && js.includes('state.up = JSON.parse(JSON.stringify(defaults.up))'),
        "恢复自动拟合应使用当前测量记录生成的原始端点");
assert.ok(!js.includes('autoFitCurrentRun().then(() => q(`#${button.dataset.chart}`).resetFit?.())'),
        "恢复自动拟合不应新增后端拟合修订，只撤销当前页面的端点拖动");
assert.ok(js.includes("processStage"), "工作页应显示模拟自动流程阶段");
assert.ok(!js.includes("? 3 : 1"), "位移表格不应继续显示 0.001 mm 精度");

const slopeSamples = [
    [1, 10, 0.01],
    [2, 20, 0.03],
    [3, 25, 0.08],
    [4, 50, 0.10]
];
assert.deepEqual(curveMath.findMaxSlopeSegment(slopeSamples, 1, 2), [[20, 0.03], [25, 0.08]], "应选择斜率最大的相邻两点");
assert.equal(curveMath.quantizeDisplacement(0.076), 0.08, "位移应按 0.01 mm 分度取值");
assert.deepEqual(curveMath.anchorSegment([[40, 0.05], [60, 0.15]], 0.2), [[30, 0], [70, 0.2]], "拟合线应延长到横轴和最高位移");

const smoothPath = curveMath.buildSmoothPath([[0, 10], [10, 0], [20, 5]]);
assert.match(smoothPath, /^M 0 10 C /, "平滑曲线应使用三次贝塞尔路径");
assert.ok(smoothPath.endsWith("20 5"), "平滑曲线必须经过最后一个采样点");
assert.ok(!smoothPath.includes(" L "), "采样点之间不应继续使用折线连接");

console.log("Prototype checks passed.");
