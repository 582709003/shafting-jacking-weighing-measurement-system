const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.resolve(__dirname, "../../..");
const html = fs.readFileSync(path.join(root, "src/main/resources/static/prototype/index.html"), "utf8");
const script = fs.readFileSync(path.join(root, "src/main/resources/static/prototype/app.js"), "utf8");
const styles = fs.readFileSync(path.join(root, "src/main/resources/static/prototype/styles.css"), "utf8");

[
    "manualModeButton", "autoModeButton", "manualStartButton", "targetDisplacementInput", "saveManualTargetButton",
    "manualAscendButton", "manualDescendButton", "manualQuickReleaseButton", "manualPauseButton",
    "manualStopButton", "startAutoTestButton", "autoQuickReleaseButton", "autoPauseButton",
    "autoStopButton", "zeroDisplacementButton"
].forEach(id => assert.match(html, new RegExp(`id="${id}"`), `缺少控制组件 ${id}`));

assert.match(script, /\/api\/control\/state/);
assert.match(script, /\/api\/control\/commands/);
assert.match(script, /required><small>/);
assert.match(script, /不能为空/);
assert.doesNotMatch(script, /maxPressure|压力上限/);
assert.match(script, /MANUAL_ENABLE/);
assert.match(script, /\/manual-target/);
assert.match(script, /const canEditManualTarget = sessionContext\.roles\.some\(role => \["ADMIN", "ENGINEER"\]\.includes\(role\)\)/);
assert.match(script, /MANUAL_PAUSE/);
assert.match(script, /manualAscendButton"\)\.disabled = !manualActionAllowed \|\| !channel\.zeroed/,
        "当前通道未清零时，上升按钮必须灰显");
assert.match(script, /manualAscendButton"\)\.title =[\s\S]*?请在手动模式下重新执行本通道位移清零/,
        "上升按钮灰显时应提示先重新清零");
assert.match(script, /\/api\/points\/\$\{selected\.pointCode\}\/events/);
assert.match(script, /function syncCurrentRun\(\)[\s\S]*?`point:\$\{selected\.pointCode\}`[\s\S]*?subscribeRealtime/);
assert.match(script, /controlCommandPending/);
assert.match(script, /指令下发中，请等待 PLC 确认/);
assert.doesNotMatch(script, /manualStopButton"\)\.disabled[^;]+channel\.runId/);
assert.doesNotMatch(script, /target <= Number\(config\?\.maxDisplacement\)/);
assert.match(script, /AUTO_QUICK_RELEASE/);
assert.match(script, /AUTO_STOP/);
assert.doesNotMatch(html, /masterEnableButton/);
assert.doesNotMatch(html, /data-reading="pressure"/);
assert.doesNotMatch(html, /顶升压力/);
assert.match(styles, /\.state-button\.is-on/);
assert.match(styles, /\.state-button:disabled/);
assert.match(html, /class="ship-schematic"/);
assert.match(html, /船尾/);
assert.match(html, /机舱/);
assert.match(html, /船首方向/);
assert.match(html, /示意图非比例/);
assert.match(script, /const schematicPoints = \[\s*\{ pointCode: "TAIL_SHAFT"[\s\S]*?\{ pointCode: "INTERMEDIATE_2"[\s\S]*?\{ pointCode: "INTERMEDIATE_1"[\s\S]*?\{ pointCode: "FLYWHEEL"[\s\S]*?\{ pointCode: "MAIN_ENGINE_1"[\s\S]*?\{ pointCode: "MAIN_ENGINE_2"/,
        "船体示意图必须按尾轴、中轴2、中轴1、飞轮、1#、2#的物理顺序绘制");
assert.match(styles, /@keyframes hydraulic-rise/);
assert.match(styles, /prefers-reduced-motion/);

const liftingStart = script.indexOf("function isChannelLifting(");
const liftingEnd = script.indexOf("function renderShaftVisual", liftingStart);
assert.ok(liftingStart >= 0 && liftingEnd > liftingStart, "缺少独立的顶升动画状态判断");
const liftingSource = script.slice(liftingStart, liftingEnd);

function isLifting(action, { paused = false, processStage = "WAITING", state = "RUNNING",
    pointCode = "TAIL_SHAFT", runPointCode = "TAIL_SHAFT" } = {}) {
    const context = {
        channel: { pointCode, action, paused },
        run: { pointCode: runPointCode, processStage, state },
        result: false
    };
    vm.runInNewContext(`${liftingSource}\nresult = isChannelLifting(channel, run);`, context);
    return context.result;
}

assert.equal(isLifting("ASCENDING"), true, "手动上升应立即显示顶升流动动画");
assert.equal(isLifting("ASCENDING", { paused: true }), false, "手动上升暂停后应停止动画");
assert.equal(isLifting("DESCENDING"), false, "手动下降不得显示向上流动动画");
assert.equal(isLifting("MANUAL_QUICK_RELEASE"), false, "快泄不得显示向上流动动画");
assert.equal(isLifting("AUTO_TEST", { processStage: "LIFTING" }), true,
        "自动测量仅在顶升阶段显示动画");
for (const processStage of ["PRECHECK", "HOLDING", "LOWERING", "COMPLETED"]) {
    assert.equal(isLifting("AUTO_TEST", { processStage }), false,
            `自动测量 ${processStage} 阶段不得显示顶升动画`);
}
assert.equal(isLifting("AUTO_TEST", { processStage: "LIFTING", state: "PAUSED" }), false,
        "自动顶升暂停后应停止动画");
assert.equal(isLifting("AUTO_TEST", { processStage: "LIFTING", runPointCode: "INTERMEDIATE_2" }), false,
        "自动顶升动画只能出现在对应通道");
assert.match(script, /currentRun\.processStage = snapshot\.processStage/,
        "SSE 自动阶段必须同步到当前运行状态后刷新示意图");

const syncStart = script.indexOf("function syncCurrentRun()");
const syncEnd = script.indexOf("function subscribeRealtime", syncStart);
const syncSource = script.slice(syncStart, syncEnd);

function syncRunFor(action, runId = null) {
    const subscribedUrls = [];
    const context = {
        controlState: {
            globalAction: "IDLE",
            globalPaused: false,
            channels: [{ pointCode: "TAIL_SHAFT", action, runId, paused: false }]
        },
        activePointCode: "TAIL_SHAFT",
        currentRun: undefined,
        realtimeEvents: undefined,
        subscribeRealtime: url => subscribedUrls.push(url)
    };
    vm.runInNewContext(`
        function selectedChannelState() {
            return controlState.channels.find(channel => channel.pointCode === activePointCode);
        }
        ${syncSource}
        syncCurrentRun();
    `, context);
    return { currentRun: context.currentRun, subscribedUrls };
}

assert.equal(syncRunFor("IDLE").currentRun, undefined, "待机通道不应被误判为运行中，否则无法切换通道");
assert.deepEqual(syncRunFor("ASCENDING").subscribedUrls, ["/api/points/TAIL_SHAFT/events"],
        "手动动作期间应继续订阅当前通道实时数据");
assert.deepEqual(syncRunFor("AUTO_TEST", 9).subscribedUrls, ["/api/runs/9/events"],
        "自动测量期间应按运行记录订阅实时数据");

console.log("控制面板原型结构与状态样式检查通过");
