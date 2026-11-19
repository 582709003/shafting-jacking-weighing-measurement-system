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
assert.match(styles, /@keyframes hydraulic-flow/);
assert.match(styles, /prefers-reduced-motion/);

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
