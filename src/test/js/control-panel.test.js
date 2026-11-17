const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

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
assert.match(script, /function syncCurrentRun\(\)[\s\S]*?\(manualAction \|\| globalQuickRelease\)[\s\S]*?subscribeRealtime/);
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

console.log("控制面板原型结构与状态样式检查通过");
