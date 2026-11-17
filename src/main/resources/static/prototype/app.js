const channels = ["尾轴", "中轴1", "中轴2", "飞轮", "1#", "2#"];
const pointCodes = ["TAIL_SHAFT", "INTERMEDIATE_1", "INTERMEDIATE_2", "FLYWHEEL", "MAIN_ENGINE_1", "MAIN_ENGINE_2"];
const shaftChannels = ["尾轴", "中轴1", "中轴2", "飞轮"];
const parameterRows = [
    ["标准值", "kN", "standardForce", "0.01"],
    ["总值", "kN", "totalValue", "0.01"],
    ["顶升系数", "", "jackingCoefficient", "0.0001"],
    ["自动目标位移", "mm", "maxDisplacement", "0.01"],
    ["顶升速度", "r/min", "motorRpm", "1"],
    ["力值间隔", "kN", "forceIntervalKn", "0.001"],
    ["时间间隔", "s", "holdSeconds", "1"]
];
let samples = [];
let curveMaximumDisplacement = 0;

const q = (selector, root = document) => root.querySelector(selector);
const qa = (selector, root = document) => [...root.querySelectorAll(selector)];
let sessionContext;
let currentConfigVersion = 1;
let activePointCode = pointCodes[0];
let activeSettingsPointCode = pointCodes[0];
let currentRun;
let currentCurveRun;
let currentValueResult;
let activeResultPointCode = pointCodes[0];
let realtimeEvents;
let recordRows = [];
let controlState;
let configByPoint = new Map();
let controlErrorMessage = "";
let controlErrorUntil = 0;
let manualTargetDirty = false;
let settingsSaving = false;
const curveTasksBySelect = new Map();
const curvePages = {
    shaftCurveTabs: {
        selectId: "shaftCurveTaskSelect", metaId: "shaftCurveTaskMeta", chartId: "shaftCurveChart",
        tableTitleId: "curveTableTitle", bodySelector: "#curveDataBody"
    },
    mainCurveTabs: {
        selectId: "mainCurveTaskSelect", metaId: "mainCurveTaskMeta", chartId: "mainCurveChart",
        tableTitleId: "mainCurveTableTitle", bodySelector: ".shared-data-body"
    }
};

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[character]);
}

function renderSettingsTabs() {
    const tabs = q("#settingsChannelTabs");
    channels.forEach((channel, index) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = pointCodes[index] === activeSettingsPointCode ? "active" : "";
        button.textContent = channel;
        button.addEventListener("click", () => {
            activeSettingsPointCode = pointCodes[index];
            qa("button", tabs).forEach((item, itemIndex) => item.classList.toggle("active", itemIndex === index));
            renderSettingsForm();
            showSettingsNotice("info", `正在编辑${channel}参数，保存只影响当前通道。`);
        });
        tabs.append(button);
    });
}

function showSettingsNotice(type, message) {
    const notice = q("#settingsNotice");
    notice.className = `notice notice-${type}`;
    notice.textContent = message;
}

function renderSettingsForm() {
    const form = q("#settingsForm");
    const channelIndex = pointCodes.indexOf(activeSettingsPointCode);
    const point = configByPoint.get(activeSettingsPointCode) || {};
    const defaults = { standardForce: 0, totalValue: 0, jackingCoefficient: 1,
        maxDisplacement: 0, motorRpm: 0, forceIntervalKn: 0.5, holdSeconds: 0 };
    form.innerHTML = `<div class="settings-form-head"><div><p>当前通道</p><h2>${channels[channelIndex]}</h2></div><span>单通道新增或更新</span></div>
        <div class="settings-field-grid">${parameterRows.map(([name, unit, field, step]) => `
            <label class="settings-field"><span>${name}</span><div><input type="number" min="0" step="${step}" data-field="${field}" aria-label="${channels[channelIndex]}${name}" value="${point[field] ?? defaults[field]}" required><small>${unit}</small></div></label>`).join("")}</div>`;
    const canEdit = sessionContext?.roles.some(role => role === "ADMIN" || role === "ENGINEER");
    qa("input", form).forEach(input => input.disabled = !canEdit);
}

async function loadSession() {
    const response = await fetch("/api/session");
    if (response.status === 401) {
        location.href = "/login";
        throw new Error("未登录");
    }
    if (!response.ok) throw new Error("读取登录信息失败");
    sessionContext = await response.json();
    const role = sessionContext.roles.includes("ADMIN") ? "ADMIN" : sessionContext.roles[0];
    document.body.dataset.role = role || "VIEWER";
    q("#logoutButton").textContent = `${sessionContext.username} · 退出`;
    q("#adjustmentCoefficientCard").hidden = !sessionContext.roles.includes("ADMIN");
}

async function loadSettingsConfig() {
    const response = await fetch("/api/configs/1");
    if (!response.ok) throw new Error("读取参数失败");
    const config = await response.json();
    currentConfigVersion = config.configVersion;
    configByPoint = new Map(config.points.map(point => [point.pointCode, point]));
    renderSettingsForm();
    const canEdit = sessionContext.roles.some(role => role === "ADMIN" || role === "ENGINEER");
    q("#saveSettingsButton").disabled = !canEdit;
    showSettingsNotice("info", canEdit
            ? `已加载“${config.configName}”第 ${config.configVersion} 版，保存后自动记录审计日志。`
            : `当前账号只读，正在查看“${config.configName}”第 ${config.configVersion} 版。`);
    loadManualTargetInput();
    renderChannelLimits();
}

function loadManualTargetInput() {
    const target = Number(configByPoint.get(activePointCode)?.manualTargetDisplacement || 0);
    q("#targetDisplacementInput").value = target.toFixed(2);
    manualTargetDirty = false;
}

async function saveSettingsConfig() {
    if (settingsSaving) return;
    const channel = channels[pointCodes.indexOf(activeSettingsPointCode)];
    const saveButton = q("#saveSettingsButton");
    const point = { pointCode: activeSettingsPointCode };
    for (const [name, , field] of parameterRows) {
        const input = q(`#settingsForm [data-field="${field}"]`);
        if (input.value.trim() === "") {
            input.focus();
            showSettingsNotice("error", `${channel}${name}不能为空。`);
            return;
        }
        point[field] = Number(input.value);
    }
    settingsSaving = true;
    saveButton.disabled = true;
    showSettingsNotice("info", `正在保存${channel}参数……`);
    try {
        const response = await fetch(`/api/configs/1/points/${activeSettingsPointCode}`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                [sessionContext.csrfHeaderName]: sessionContext.csrfToken
            },
            body: JSON.stringify(point)
        });
        if (!response.ok) throw new Error(await responseError(response, "参数保存失败，请稍后重试。"));
        const config = await response.json();
        currentConfigVersion = config.configVersion;
        configByPoint = new Map(config.points.map(item => [item.pointCode, item]));
        renderSettingsForm();
        showSettingsNotice("success",
                `${channel}参数保存成功，当前方案版本为第 ${currentConfigVersion} 版。`);
    } catch (error) {
        const message = error instanceof TypeError
                ? "参数保存失败，请检查网络连接后重试。"
                : (error.message || "参数保存失败，请稍后重试。");
        showSettingsNotice("error", message);
    } finally {
        settingsSaving = false;
        saveButton.disabled = !sessionContext.roles.some(role => role === "ADMIN" || role === "ENGINEER");
    }
}

async function saveManualTarget() {
    const targetDisplacementMm = Number(q("#targetDisplacementInput").value);
    if (targetDisplacementMm <= 0) throw new Error("手动目标位移必须大于 0 mm");
    const response = await fetch(`/api/configs/1/points/${activePointCode}/manual-target`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
            [sessionContext.csrfHeaderName]: sessionContext.csrfToken
        },
        body: JSON.stringify({ targetDisplacementMm })
    });
    if (!response.ok) throw new Error(await responseError(response, "保存手动目标位移失败"));
    const config = await response.json();
    configByPoint = new Map(config.points.map(point => [point.pointCode, point]));
    manualTargetDirty = false;
    loadManualTargetInput();
    renderControlState();
}

async function sendControlCommand(commandType, options = {}) {
    const commandNo = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
    const response = await fetch("/api/control/commands", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            [sessionContext.csrfHeaderName]: sessionContext.csrfToken
        },
        body: JSON.stringify({
            commandNo,
            commandType,
            pointCode: options.global ? null : activePointCode,
            targetDisplacementMm: options.targetDisplacementMm ?? null,
            configId: 1
        })
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.detail || error.message || `控制命令执行失败（${response.status}）`);
    }
    controlState = await response.json();
    syncCurrentRun();
    renderControlState();
    return controlState;
}

async function loadControlState() {
    const response = await fetch("/api/control/state");
    if (!response.ok) throw new Error("读取控制状态失败");
    controlState = await response.json();
    syncCurrentRun();
    renderControlState();
}

function selectedChannelState() {
    return controlState?.channels.find(channel => channel.pointCode === activePointCode);
}

function syncCurrentRun() {
    const selected = selectedChannelState();
    const manualAction = ["ASCENDING", "DESCENDING", "MANUAL_QUICK_RELEASE"].includes(selected?.action);
    const subscriptionKey = selected?.runId ? `run:${selected.runId}`
            : manualAction ? `point:${selected.pointCode}` : undefined;
    if (!subscriptionKey) {
        realtimeEvents?.close();
        realtimeEvents = undefined;
        currentRun = undefined;
        return;
    }
    const state = selected.paused ? "PAUSED" : "RUNNING";
    if (currentRun?.subscriptionKey !== subscriptionKey) {
        currentRun = { runId: selected.runId, pointCode: selected.pointCode,
            subscriptionKey, state, processStage: "WAITING" };
        subscribeRealtime(selected.runId
                ? `/api/runs/${selected.runId}/events`
                : `/api/points/${selected.pointCode}/events`);
    } else {
        currentRun.state = state;
    }
}

function subscribeRealtime(url) {
    realtimeEvents?.close();
    realtimeEvents = new EventSource(url);
    realtimeEvents.addEventListener("measurement", event => renderRealtime(JSON.parse(event.data)));
    realtimeEvents.onerror = () => {
        q("#runStateLabel").textContent = "实时连接中断，正在自动重连";
        q("#runStateLabel").classList.remove("live");
    };
}

function renderRealtime(snapshot) {
    q('[data-reading="displacement"]').textContent = Number(snapshot.displacementMm).toFixed(2);
    q('[data-reading="rpm"]').textContent = String(snapshot.motorRpm);
    q('[data-reading="force"]').textContent = Number(snapshot.forceKn).toFixed(1);
    q("#simulatorStatus").innerHTML = "<i></i>实时数据推送中";
    q("#runStateLabel").textContent = `${stateName(snapshot.state)} · ${new Date(snapshot.capturedAt).toLocaleTimeString("zh-CN", { hour12: false })}`;
    q("#runStateLabel").classList.add("live");
    renderProcessStage(snapshot.processStage);
    if (snapshot.state === "COMPLETED") {
        currentRun = { ...currentRun, state: "COMPLETED", processStage: snapshot.processStage };
        realtimeEvents?.close();
        realtimeEvents = undefined;
        loadControlState().catch(() => {});
        finalizeCompletedRun().catch(error => q("#runStateLabel").textContent = error.message);
    }
}

function renderProcessStage(processStage) {
    const stages = ["PRECHECK", "LIFTING", "HOLDING", "LOWERING"];
    const index = processStage === "COMPLETED" ? stages.length : Math.max(0, stages.indexOf(processStage));
    qa(".sequence [data-stage]").forEach((item, itemIndex) => {
        item.classList.toggle("current", itemIndex === Math.min(index, stages.length - 1));
    });
    q("#processStageCounter").textContent = `${index} / ${stages.length}`;
}

function stateName(state) {
    return { IDLE: "待启动", RUNNING: "运行中", PAUSED: "已暂停", STOPPED: "已停止", COMPLETED: "已完成" }[state] || state;
}

function renderRunState() {
    renderControlState();
}

function renderControlState() {
    if (!controlState || !sessionContext) return;
    const canControl = sessionContext.roles.some(role => ["ADMIN", "ENGINEER", "OPERATOR"].includes(role));
    const canEditManualTarget = sessionContext.roles.some(role => ["ADMIN", "ENGINEER"].includes(role));
    const channel = selectedChannelState();
    const globalQuickRelease = controlState.globalAction === "AUTO_QUICK_RELEASE";
    const anyAction = controlState.channels.some(item => item.action !== "IDLE");
    const manual = channel.mode === "MANUAL";
    const automatic = channel.mode === "AUTO";
    const idle = channel.action === "IDLE";
    const available = !controlState.alarmLocked;
    const globalPaused = controlState.globalPaused;
    const config = configByPoint.get(activePointCode);
    const target = Number(q("#targetDisplacementInput").value);
    const savedTarget = Number(config?.manualTargetDisplacement || 0);
    const automaticTargetConfigured = Number(config?.maxDisplacement) > 0;
    const automaticConfigured = automaticTargetConfigured && Number(config?.forceIntervalKn) > 0;
    const targetValid = target > 0;
    const targetSaved = !manualTargetDirty && target > 0 && target === savedTarget;

    setButtonState(q("#manualModeButton"), manual);
    q("#manualModeButton b").textContent = manual ? "断开手动" : "接通手动";
    q("#manualModeButton").disabled = !canControl || !available || globalQuickRelease || anyAction
            || (!manual && !idle);
    setButtonState(q("#autoModeButton"), automatic);
    q("#autoModeButton b").textContent = automatic ? "断开自动" : "接通自动";
    q("#autoModeButton").disabled = !canControl || !available || globalQuickRelease || anyAction
            || (!automatic && !idle);

    setButtonState(q("#manualStartButton"), channel.manualStarted);
    q("#manualStartButton b").textContent = channel.manualStarted ? "手动控制已启动" : "启动手动控制";
    q("#manualStartButton").disabled = !canControl || !available || !manual || !idle
            || anyAction || globalQuickRelease || channel.manualStarted;

    q("#targetDisplacementInput").disabled = !canEditManualTarget || !available || !manual || !idle || anyAction;
    setButtonState(q("#saveManualTargetButton"), targetSaved);
    q("#saveManualTargetButton b").textContent = targetSaved ? "已保存" : "保存";
    q("#saveManualTargetButton").disabled = !canEditManualTarget || !available || !manual || !idle
            || anyAction || globalQuickRelease || target <= 0 || targetSaved;
    q("#zeroDisplacementButton").disabled = !canControl || !available || channel.mode === "OFF"
            || !idle || globalQuickRelease;
    q("#zeroDisplacementButton").classList.toggle("zeroed", channel.zeroed);
    q("#zeroDisplacementButton").textContent = channel.zeroed ? "位移已清零" : "本通道位移清零";

    setButtonState(q("#manualAscendButton"), channel.action === "ASCENDING");
    setButtonState(q("#manualDescendButton"), channel.action === "DESCENDING");
    setButtonState(q("#manualQuickReleaseButton"), channel.action === "MANUAL_QUICK_RELEASE");
    const manualAction = ["ASCENDING", "DESCENDING", "MANUAL_QUICK_RELEASE"].includes(channel.action);
    const manualActionAllowed = canControl && available && manual && channel.manualStarted
            && idle && !anyAction && !globalQuickRelease;
    q("#manualAscendButton").disabled = !manualActionAllowed || !channel.zeroed
            || !targetValid || !targetSaved;
    q("#manualAscendButton").title = !targetSaved
            ? "请先由管理员或工程师保存大于 0 mm 的目标位移"
            : !channel.zeroed ? "请在手动模式下重新执行本通道位移清零" : "";
    q("#manualDescendButton").disabled = !manualActionAllowed;
    q("#manualQuickReleaseButton").disabled = !manualActionAllowed;
    setButtonState(q("#manualPauseButton"), manualAction && channel.paused);
    q("#manualPauseButton b").textContent = channel.paused ? "继续" : "暂停";
    q("#manualPauseButton").disabled = !canControl || !available || !manualAction;
    q("#manualStopButton").disabled = !canControl || !available || !manualAction;
    setButtonState(q("#manualStopButton"), false);

    const autoTestActive = channel.action === "AUTO_TEST";
    setButtonState(q("#startAutoTestButton"), autoTestActive);
    q("#startAutoTestButton").disabled = !canControl || !available || !automatic || !idle
            || anyAction || !channel.zeroed || globalQuickRelease || !automaticConfigured;
    q("#startAutoTestButton").title = !channel.zeroed
            ? "请在自动模式下重新执行本通道位移清零"
            : !automaticConfigured ? "请先配置自动目标位移和力值间隔" : "";
    setButtonState(q("#autoQuickReleaseButton"), globalQuickRelease);
    q("#autoQuickReleaseButton").disabled = !canControl || !available || !automatic
            || globalQuickRelease || anyAction;
    const automaticPaused = autoTestActive ? channel.paused : globalQuickRelease && globalPaused;
    setButtonState(q("#autoPauseButton"), automaticPaused);
    q("#autoPauseButton b").textContent = automaticPaused ? "继续" : "暂停";
    q("#autoPauseButton").disabled = !canControl || !available || (!autoTestActive && !globalQuickRelease);
    q("#autoStopButton").disabled = !canControl || !available || (!autoTestActive && !globalQuickRelease);
    setButtonState(q("#autoStopButton"), false);

    qa("#workChannelStrip button").forEach(button => button.disabled = anyAction || globalQuickRelease);
    qa(".shaft-node").forEach((node, index) => {
        const item = controlState.channels[index];
        const flowing = (globalQuickRelease && !globalPaused)
                || (!item.paused && ["ASCENDING", "DESCENDING", "MANUAL_QUICK_RELEASE", "AUTO_TEST"]
                .includes(item.action));
        node.classList.toggle("flowing", flowing);
    });
    q("#runStateLabel").textContent = Date.now() < controlErrorUntil ? controlErrorMessage
            : controlState.alarmLocked ? "报警锁定：请关闭全部报警后重新启用"
            : globalQuickRelease ? (globalPaused ? "六通道自动快泄已暂停" : "六通道自动快泄中")
            : channel.action !== "IDLE" ? (channel.paused ? `${actionName(channel.action)} · 已暂停` : actionName(channel.action))
            : channel.zeroed ? "位移已清零，等待启动" : `${modeName(channel.mode)} · 就绪`;
    q("#runStateLabel").classList.toggle("live", anyAction || globalQuickRelease);
    renderChannelLimits();
}

function setButtonState(button, active) {
    button.classList.toggle("is-on", active);
    button.classList.toggle("is-off", !active);
}

function actionName(action) {
    return { ASCENDING: "手动上升中", DESCENDING: "手动下降中", MANUAL_QUICK_RELEASE: "手动快泄中",
        AUTO_TEST: "自动测试运行中" }[action] || "就绪";
}

function modeName(mode) {
    return { OFF: "模式断开", MANUAL: "手动模式", AUTO: "自动模式" }[mode] || mode;
}

function renderChannelLimits() {
    const config = configByPoint.get(activePointCode);
    if (!config) return;
    const displacement = Number(config.maxDisplacement);
    const forceInterval = Number(config.forceIntervalKn);
    const missing = [displacement <= 0 ? "自动目标位移" : "",
        forceInterval <= 0 ? "力值间隔" : ""].filter(Boolean);
    q("#channelLimitText").textContent = missing.length > 0
            ? `${missing.join("、")}未配置，相关上升动作不可用`
            : `自动目标 ${displacement.toFixed(2)} mm · 力值间隔 ${forceInterval.toFixed(3)} kN`;
}

function renderValueChannelSelector() {
    const strip = q("#valueChannelStrip");
    channels.forEach((channel, index) => {
        const button = document.createElement("button");
        button.className = index === 0 ? "active" : "";
        button.innerHTML = `<span>${channel}</span><small>选择任务</small>`;
        button.addEventListener("click", () => {
            qa("button", strip).forEach(item => item.classList.remove("active"));
            button.classList.add("active");
            activeResultPointCode = pointCodes[index];
            currentValueResult = undefined;
            loadCompletedValueTasks(button).catch(showValueResultError);
        });
        strip.append(button);
    });
    q("#valueTaskSelect").addEventListener("change", event => {
        if (!event.target.value) {
            renderValueResultEmpty("请选择一条已完成测量任务");
            return;
        }
        loadValueResultByRun(Number(event.target.value)).catch(showValueResultError);
    });
}

async function loadCompletedValueTasks(button) {
    const response = await fetch(`/api/records/completed?pointCode=${encodeURIComponent(activeResultPointCode)}`);
    if (!response.ok) throw new Error(await responseError(response, "读取已完成测量任务失败"));
    const tasks = await response.json();
    const select = q("#valueTaskSelect");
    curveTasksBySelect.set("valueTaskSelect", tasks);
    select.disabled = tasks.length === 0;
    select.innerHTML = `<option value="">${tasks.length ? "请选择测量任务" : "该通道暂无已完成任务"}</option>`
            + tasks.map(task => `<option value="${task.runId}">${escapeHtml(task.taskName)}｜${escapeHtml(task.taskNo)}｜${formatCurveTaskTime(task.completedAt)}</option>`).join("");
    button.querySelector("small").textContent = tasks.length ? `${tasks.length} 条已完成` : "暂无完整测量";
    q("#valueTaskMeta").textContent = tasks.length
            ? `共 ${tasks.length} 条，按完成时间从新到旧排列` : "需完整执行上升和下降流程后才会出现在这里";
    renderValueResultEmpty(tasks.length ? "请选择一条已完成测量任务" : "该通道暂无完整测量数据");
}

async function loadValueResultByRun(runId) {
    q("#valueResultNotice").textContent = "正在读取所选任务的计算结果。";
    const response = await fetch(`/api/runs/${runId}/result`);
    if (!response.ok) throw new Error(await responseError(response, "所选任务尚未生成计算结果"));
    currentValueResult = await response.json();
    renderValueResult(currentValueResult);
}

function renderValueResult(pageResult) {
    q("#resultPointName").textContent = pageResult.pointName;
    q("#resultTaskNo").textContent = pageResult.taskNo;
    q("#resultSavedAt").textContent = pageResult.savedAt
            ? `保存于 ${new Date(pageResult.savedAt).toLocaleString("zh-CN", { hour12: false })}`
            : "计算完成，等待保存确认";
    const status = q("#valueResultStatus");
    const saved = pageResult.resultStatus === "SAVED";
    status.textContent = saved ? "已保存" : "待保存";
    status.className = `result-status ${saved ? "saved" : "draft"}`;
    renderMeasuredResult(pageResult.result);
    const canSave = sessionContext.roles.some(role => ["ADMIN", "ENGINEER", "OPERATOR"].includes(role));
    q("#saveResultButton").disabled = !canSave || saved;
    q("#deleteResultButton").disabled = !sessionContext.roles.includes("ADMIN");
    q("#saveAdjustmentCoefficientButton").disabled = !sessionContext.roles.includes("ADMIN");
    q("#valueResultNotice").textContent = saved
            ? "该任务结果已保存。"
            : "请确认所选任务的标准值、测量值和偏差后保存。";
}

function renderValueResultEmpty(message) {
    q("#resultPointName").textContent = channels[pointCodes.indexOf(activeResultPointCode)];
    q("#resultTaskNo").textContent = "--";
    q("#resultSavedAt").textContent = message;
    renderMeasuredResult();
    q("#valueResultStatus").textContent = "无结果";
    q("#valueResultStatus").className = "result-status draft";
    q("#saveResultButton").disabled = true;
    q("#deleteResultButton").disabled = true;
    q("#saveAdjustmentCoefficientButton").disabled = true;
    q("#valueResultNotice").textContent = message;
}

function showValueResultError(error) {
    if (!currentValueResult) renderValueResultEmpty(error.message);
    else q("#valueResultNotice").textContent = error.message;
}

async function saveCurrentValueResult() {
    if (!currentValueResult) throw new Error("当前通道没有可保存结果");
    const response = await fetch(`/api/runs/${currentValueResult.runId}/result/save`, {
        method: "POST",
        headers: { [sessionContext.csrfHeaderName]: sessionContext.csrfToken }
    });
    if (!response.ok) throw new Error(await responseError(response, "保存结果失败"));
    currentValueResult = await response.json();
    renderValueResult(currentValueResult);
}

async function deleteCurrentValueResult() {
    if (!currentValueResult) throw new Error("当前通道没有可删除结果");
    if (!window.confirm(`确认删除${currentValueResult.pointName}本次结果和原始采样？该操作会写入审计日志。`)) return;
    const response = await fetch(`/api/runs/${currentValueResult.runId}/result`, {
        method: "DELETE",
        headers: { [sessionContext.csrfHeaderName]: sessionContext.csrfToken }
    });
    if (!response.ok) throw new Error(await responseError(response, "删除结果失败"));
    currentValueResult = undefined;
    const activeButton = q("#valueChannelStrip button.active");
    await loadCompletedValueTasks(activeButton);
    renderValueResultEmpty("本次结果和原始采样已逻辑删除，任务、运行和曲线修订仍保留。");
}

async function saveAdjustmentCoefficient() {
    if (!sessionContext.roles.includes("ADMIN")) throw new Error("仅管理员可修改调整系数");
    if (!currentValueResult) throw new Error("当前通道没有可修改的测量结果");
    const coefficient = Number(q("#adjustmentCoefficientResult").value);
    if (!Number.isFinite(coefficient) || coefficient <= 0) throw new Error("调整系数必须大于 0");
    const response = await fetch(
            `/api/runs/${currentValueResult.runId}/result/adjustment-coefficient`, {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json",
                    [sessionContext.csrfHeaderName]: sessionContext.csrfToken
                },
                body: JSON.stringify({ adjustmentCoefficient: coefficient })
            });
    if (!response.ok) throw new Error(await responseError(response, "保存调整系数失败"));
    currentValueResult = await response.json();
    renderValueResult(currentValueResult);
}

async function responseError(response, fallback) {
    const error = await response.json().catch(() => ({}));
    return error.detail || error.message || fallback;
}

function renderChannelSelectors() {
    const workStrip = q("#workChannelStrip");
    const shaftVisual = q("#shaftVisual");
    channels.forEach((channel, index) => {
        const button = document.createElement("button");
        button.className = index === 0 ? "active" : "";
        button.innerHTML = `<span>${channel}</span><small>就绪</small>`;
        button.addEventListener("click", () => {
            if (currentRun && ["RUNNING", "PAUSED"].includes(currentRun.state)) return;
            qa("button", workStrip).forEach(item => item.classList.remove("active"));
            button.classList.add("active");
            activePointCode = pointCodes[index];
            loadManualTargetInput();
            q("#activeChannelLabel").textContent = channel;
            q(".readings-panel h2").textContent = `${channel}实时数据`;
            qa(".shaft-node", shaftVisual).forEach((item, itemIndex) => item.classList.toggle("active", itemIndex === index));
            renderControlState();
            renderChannelLimits();
        });
        workStrip.append(button);

        const node = document.createElement("div");
        node.className = `shaft-node ${index === 0 ? "active" : ""}`;
        node.innerHTML = `<b><i class="flow-arrow">↑</i></b><span>${channel}</span><small>在线</small>`;
        shaftVisual.append(node);
    });
}

function sparkline() {
    return `<svg viewBox="0 0 80 45" aria-hidden="true"><polyline points="2,39 15,34 28,29 38,16 52,9 66,8 78,4" fill="none" stroke="#23a6a8" stroke-width="2"/><polyline points="2,40 18,37 32,34 46,27 60,14 78,5" fill="none" stroke="#176b87" stroke-width="2"/></svg>`;
}

function renderCurveTabs() {
    const render = (container, list) => {
        const page = curvePages[container.id];
        list.forEach((channel, index) => {
            const button = document.createElement("button");
            button.className = `curve-tab ${index === 0 ? "active" : ""}`;
            button.innerHTML = `<span>${channel}</span><strong>--</strong><small>选择已完成测量任务</small>${sparkline()}`;
            button.addEventListener("click", () => {
                qa(".curve-tab", container).forEach(item => item.classList.remove("active"));
                button.classList.add("active");
                q(`#${page.tableTitleId}`).textContent = `${channel}升降数据`;
                loadCompletedCurveTasks(pointCodes[channels.indexOf(channel)], page, button)
                        .catch(error => showCurveLoadError(error, page));
            });
            container.append(button);
        });
        q(`#${page.selectId}`).addEventListener("change", event => {
            const task = (curveTasksBySelect.get(page.selectId) || [])
                    .find(item => item.runId === Number(event.target.value));
            if (!task) {
                clearCurvePage(page, "请选择一条已完成测量任务");
                return;
            }
            loadCurveTask(task, page).catch(error => showCurveLoadError(error, page));
        });
    };
    render(q("#shaftCurveTabs"), shaftChannels);
    render(q("#mainCurveTabs"), ["1#", "2#"]);
}

function renderSampleTable(page) {
    const rows = samples.map(row => `<tr><td>${row[0]}</td><td>${row[5].toFixed(2)}</td><td>${row[2].toFixed(2)}</td><td>${row[1].toFixed(1)}</td><td>${row[6].toFixed(2)}</td><td>${row[4].toFixed(2)}</td><td>${row[3].toFixed(1)}</td></tr>`).join("");
    q(page.bodySelector).innerHTML = rows;
}

async function loadCompletedCurveTasks(pointCode, page, button) {
    const response = await fetch(`/api/records/completed?pointCode=${encodeURIComponent(pointCode)}`);
    if (!response.ok) throw new Error(await responseError(response, "读取已完成测量任务失败"));
    const tasks = await response.json();
    const select = q(`#${page.selectId}`);
    curveTasksBySelect.set(page.selectId, tasks);
    select.dataset.pointCode = pointCode;
    select.disabled = tasks.length === 0;
    select.innerHTML = `<option value="">${tasks.length ? "请选择测量任务" : "该通道暂无已完成任务"}</option>`
            + tasks.map(task => `<option value="${task.runId}">${escapeHtml(task.taskName)}｜${escapeHtml(task.taskNo)}｜${formatCurveTaskTime(task.completedAt)}</option>`).join("");
    button.querySelector("strong").textContent = String(tasks.length);
    button.querySelector("small").textContent = tasks.length ? "条已完成任务" : "暂无完整测量";
    q(`#${page.metaId}`).textContent = tasks.length
            ? `共 ${tasks.length} 条，按完成时间从新到旧排列` : "需完整执行上升和下降流程后才会出现在这里";
    clearCurvePage(page, tasks.length ? "请选择一条已完成测量任务" : "该通道暂无完整测量数据");
}

function formatCurveTaskTime(value) {
    return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "完成时间未知";
}

async function loadCurveTask(task, page) {
    currentCurveRun = task;
    q(`[data-chart-notice="${page.chartId}"]`).hidden = true;
    await loadRunSamples(task.runId, page);
    await loadSavedCurveFit(task.runId, page.chartId);
    q(`#${page.metaId}`).textContent = `${task.taskNo}｜完成于 ${formatCurveTaskTime(task.completedAt)}`;
}

async function loadSavedCurveFit(runId, chartId) {
    const response = await fetch(`/api/runs/${runId}/curve-fit`);
    if (response.status === 404) return;
    if (!response.ok) throw new Error(await responseError(response, "读取已保存拟合结果失败"));
    q(`#${chartId}`).setFitState?.(await response.json());
}

function showCurveLoadError(error, page) {
    currentCurveRun = undefined;
    const emptyRow = `<tr><td colspan="7">${escapeHtml(error.message)}</td></tr>`;
    q(page.bodySelector).innerHTML = emptyRow;
    q(`#${page.chartId}`).innerHTML = `<div class="curve-empty">${escapeHtml(error.message)}</div>`;
    q("#runStateLabel").textContent = error.message;
}

function clearCurvePage(page, message) {
    currentCurveRun = undefined;
    samples = [];
    curveMaximumDisplacement = 0;
    q(page.bodySelector).innerHTML = `<tr><td colspan="7">${escapeHtml(message)}</td></tr>`;
    q(`#${page.chartId}`).innerHTML = `<div class="curve-empty">${escapeHtml(message)}</div>`;
    const panel = q(`#${page.chartId}`).closest(".chart-panel");
    qa("[data-fit]", panel).forEach(value => { value.textContent = "--"; });
}

async function loadRunSamples(runId, page) {
    const response = await fetch(`/api/runs/${runId}/samples`);
    if (!response.ok) throw new Error(await responseError(response, "读取采样数据失败"));
    const raw = await response.json();
    const lifting = raw.filter(item => item.stage === "LIFTING");
    const lowering = raw.filter(item => item.stage === "LOWERING");
    const size = Math.min(lifting.length, lowering.length);
    if (size < 2) throw new Error("升降阶段有效采样点不足");
    samples = Array.from({ length: size }, (_, index) => [
        index + 1,
        Number(lifting[index].forceKn), Number(lifting[index].displacementMm),
        Number(lowering[index].forceKn), Number(lowering[index].displacementMm),
        Number(lifting[index].pressureMpa), Number(lowering[index].pressureMpa)
    ]);
    curveMaximumDisplacement = Math.max(...raw.map(item => Number(item.displacementMm)));
    if (!(curveMaximumDisplacement > 0)) throw new Error("本次任务没有有效位移数据");
    renderSampleTable(page);
    rebuildCurveChart(page.chartId);
}

function rebuildCurveChart(chartId) {
    const chart = q(`#${chartId}`);
    chart.innerHTML = "";
    createCurveChart(chart);
}

function svgElement(tag, attrs = {}) {
    const element = document.createElementNS("http://www.w3.org/2000/svg", tag);
    Object.entries(attrs).forEach(([name, value]) => element.setAttribute(name, value));
    return element;
}

function createCurveChart(container) {
    if (samples.length < 2) {
        container.innerHTML = '<div class="curve-empty">请选择一条已完成测量任务</div>';
        return;
    }
    const canEditCurve = document.body.dataset.role === "ADMIN";
    const width = 720;
    const height = 420;
    const plot = { left: 64, top: 20, width: 620, height: 330 };
    const maxX = 200;
    const maximumDisplacement = curveMaximumDisplacement;
    const maxY = Math.max(0.05, Math.ceil(maximumDisplacement * 20) / 20);
    const defaults = {
        down: CurveMath.anchorSegment(CurveMath.findMaxSlopeSegment(samples, 3, 4), maximumDisplacement),
        up: CurveMath.anchorSegment(CurveMath.findMaxSlopeSegment(samples, 1, 2), maximumDisplacement)
    };
    const state = JSON.parse(JSON.stringify(defaults));
    const colors = { down: "#7b4d9d", up: "#118b8d", mean: "#172f45" };
    const svg = svgElement("svg", { viewBox: `0 0 ${width} ${height}`, role: "img", "aria-label": "顶升力与位移交互曲线" });
    container.append(svg);

    const xPixel = value => plot.left + value / maxX * plot.width;
    const yPixel = value => plot.top + plot.height - value / maxY * plot.height;
    const xValue = pixel => Math.max(0, Math.min(maxX, (pixel - plot.left) / plot.width * maxX));

    const grid = svgElement("g", { stroke: "#dfe6eb", "stroke-width": "1" });
    for (let i = 0; i <= 10; i++) {
        const x = plot.left + i * plot.width / 10;
        grid.append(svgElement("line", { x1: x, y1: plot.top, x2: x, y2: plot.top + plot.height }));
        const label = svgElement("text", { x, y: plot.top + plot.height + 24, "text-anchor": "middle", fill: "#6b7c8f", "font-size": "11" });
        label.textContent = String(i * 20);
        svg.append(label);
    }
    for (let i = 0; i <= 8; i++) {
        const y = plot.top + i * plot.height / 8;
        grid.append(svgElement("line", { x1: plot.left, y1: y, x2: plot.left + plot.width, y2: y }));
        const label = svgElement("text", { x: plot.left - 10, y: y + 4, "text-anchor": "end", fill: "#6b7c8f", "font-size": "11" });
        label.textContent = (maxY - i * maxY / 8).toFixed(2);
        svg.append(label);
    }
    svg.append(grid);
    svg.append(svgElement("line", { x1: plot.left, y1: plot.top + plot.height, x2: plot.left + plot.width + 8, y2: plot.top + plot.height, stroke: "#102a43", "stroke-width": "1.5" }));
    svg.append(svgElement("line", { x1: plot.left, y1: plot.top - 4, x2: plot.left, y2: plot.top + plot.height, stroke: "#102a43", "stroke-width": "1.5" }));
    const xTitle = svgElement("text", { x: plot.left + plot.width, y: height - 12, "text-anchor": "end", fill: "#334e68", "font-size": "12" });
    xTitle.textContent = "顶升力 / kN";
    svg.append(xTitle);
    const yTitle = svgElement("text", { x: 18, y: 16, fill: "#334e68", "font-size": "12" });
    yTitle.textContent = "位移 / mm";
    svg.append(yTitle);

    const curveLayer = svgElement("g");
    const risePoints = samples.map(row => [xPixel(row[1]), yPixel(row[2])]);
    const fallPoints = samples.map(row => [xPixel(row[3]), yPixel(row[4])]);
    curveLayer.append(svgElement("path", { d: CurveMath.buildSmoothPath(risePoints), fill: "none", stroke: "#176b87", "stroke-width": "3", "stroke-linecap": "round" }));
    curveLayer.append(svgElement("path", { d: CurveMath.buildSmoothPath(fallPoints), fill: "none", stroke: "#23a6a8", "stroke-width": "3", "stroke-linecap": "round" }));
    samples.forEach(row => {
        curveLayer.append(svgElement("circle", { cx: xPixel(row[1]), cy: yPixel(row[2]), r: 3.5, fill: "#176b87" }));
        curveLayer.append(svgElement("circle", { cx: xPixel(row[3]), cy: yPixel(row[4]), r: 3.5, fill: "#23a6a8" }));
    });
    svg.append(curveLayer);

    const lines = {
        down: svgElement("line", { stroke: colors.down, "stroke-width": "3" }),
        up: svgElement("line", { stroke: colors.up, "stroke-width": "3" }),
        mean: svgElement("line", { stroke: colors.mean, "stroke-width": "3", "stroke-dasharray": "9 7" })
    };
    svg.append(lines.down, lines.up, lines.mean);

    const handles = [];
    ["down", "up"].forEach(kind => {
        state[kind].forEach((point, index) => {
            const endpointName = index === 0 ? "横轴端点" : "最高位移端点";
            const handle = svgElement("circle", { r: 8, fill: "#fff", stroke: colors[kind], "stroke-width": "4", tabindex: canEditCurve ? "0" : "-1", role: "slider", "aria-disabled": String(!canEditCurve), "aria-label": `${kind === "up" ? "上升" : "下降"}拟合线${endpointName}` });
            handle.style.cursor = canEditCurve ? "ew-resize" : "not-allowed";
            handle.addEventListener("pointerdown", event => {
                if (!canEditCurve) return;
                handle.setPointerCapture(event.pointerId);
            });
            handle.addEventListener("pointermove", event => {
                if (!handle.hasPointerCapture(event.pointerId)) return;
                const rect = svg.getBoundingClientRect();
                const pixelX = (event.clientX - rect.left) * width / rect.width;
                // 两个端点的纵坐标分别锁定为 0 和本次最高位移，只允许左右修正力值。
                state[kind][index] = [xValue(pixelX), state[kind][index][1]];
                update();
            });
            handle.addEventListener("pointerup", event => {
                handle.releasePointerCapture(event.pointerId);
            });
            svg.append(handle);
            handles.push({ kind, index, handle });
        });
    });

    const meanPoints = () => CurveMath.meanPoints(state.down, state.up);
    const setLine = (line, points) => {
        const [[x1, y1], [x2, y2]] = points;
        line.setAttribute("x1", xPixel(x1));
        line.setAttribute("y1", yPixel(y1));
        line.setAttribute("x2", xPixel(x2));
        line.setAttribute("y2", yPixel(y2));
    };
    function update() {
        setLine(lines.down, state.down);
        setLine(lines.up, state.up);
        setLine(lines.mean, meanPoints());
        handles.forEach(({ kind, index, handle }) => {
            handle.setAttribute("cx", xPixel(state[kind][index][0]));
            handle.setAttribute("cy", yPixel(state[kind][index][1]));
            handle.setAttribute("aria-valuetext", `${state[kind][index][0].toFixed(1)} kN, ${state[kind][index][1].toFixed(2)} mm`);
        });
        const panel = container.closest(".chart-panel");
        q('[data-fit="down"]', panel).textContent = CurveMath.lineIntercept(state.down).toFixed(1);
        q('[data-fit="up"]', panel).textContent = CurveMath.lineIntercept(state.up).toFixed(1);
        q('[data-fit="mean"]', panel).textContent = CurveMath.lineIntercept(meanPoints()).toFixed(1);
    }
    function reset() {
        state.down = JSON.parse(JSON.stringify(defaults.down));
        state.up = JSON.parse(JSON.stringify(defaults.up));
        update();
    }
    container.resetFit = reset;
    container.setFitState = fit => {
        const segmentPoints = segment => [segment.first, segment.second]
                .map(item => [Number(item.forceKn), Number(item.displacementMm)]);
        state.down = segmentPoints(fit.lowering);
        state.up = segmentPoints(fit.lifting);
        // 中间虚线由升、降拟合线端点计算，避免保存一份可能不一致的冗余数据。
        update();
    };
    container.getFitState = () => ({
        lifting: { first: point(state.up[0]), second: point(state.up[1]) },
        lowering: { first: point(state.down[0]), second: point(state.down[1]) }
    });
    update();

    function point(value) {
        return { forceKn: Number(value[0].toFixed(2)), displacementMm: Number(value[1].toFixed(2)) };
    }
}

async function autoFitCurrentRun() {
    if (!currentRun) throw new Error("请先选择已完成的测量任务");
    const response = await fetch(`/api/runs/${currentRun.runId}/curve-fit/auto`, {
        method: "POST",
        headers: { [sessionContext.csrfHeaderName]: sessionContext.csrfToken }
    });
    if (!response.ok) throw new Error("自动拟合失败");
    const result = await response.json();
    renderMeasuredResult(result);
    return result;
}

function showCurveSaveNotice(chartId, type, message) {
    const notice = q(`[data-chart-notice="${chartId}"]`);
    notice.className = `notice notice-${type} curve-save-notice`;
    notice.textContent = message;
    notice.hidden = false;
}

async function saveManualFit(button) {
    const chartId = button.dataset.chart;
    button.disabled = true;
    showCurveSaveNotice(chartId, "info", "正在保存调整结果……");
    try {
        if (document.body.dataset.role !== "ADMIN") throw new Error("仅管理员可保存人工修订");
        const run = currentCurveRun || currentRun;
        if (!run) throw new Error("请先选择测量任务");
        const response = await fetch(`/api/runs/${run.runId}/curve-fit/manual`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                [sessionContext.csrfHeaderName]: sessionContext.csrfToken
            },
            body: JSON.stringify(q(`#${chartId}`).getFitState())
        });
        if (!response.ok) throw new Error(await responseError(response, "保存调整结果失败，请稍后重试。"));
        renderMeasuredResult(await response.json());
        showCurveSaveNotice(chartId, "success", "调整结果保存成功，三条拟合线位置已记录。");
    } catch (error) {
        showCurveSaveNotice(chartId, "error", error.message);
    } finally {
        button.disabled = document.body.dataset.role !== "ADMIN";
    }
}

async function finalizeCompletedRun() {
    await autoFitCurrentRun();
    await loadRecords();
}

function renderMeasuredResult(result) {
    if (!result) {
        ["#measuredResult", "#standardResult", "#deviationResult", "#deviationRateResult",
            "#adjustmentAmountResult"]
                .forEach(selector => { q(selector).textContent = "--"; });
        q("#adjustmentCoefficientResult").value = "";
        return;
    }
    q("#measuredResult").textContent = Number(result.measuredForceKn).toFixed(2);
    q("#standardResult").textContent = Number(result.standardForceKn).toFixed(2);
    q("#deviationResult").textContent = Number(result.deviationKn).toFixed(2);
    q("#deviationRateResult").textContent = result.deviationRate == null
            ? "--" : Number(result.deviationRate).toFixed(2);
    if (sessionContext.roles.includes("ADMIN") && result.adjustmentCoefficient != null) {
        q("#adjustmentCoefficientResult").value = Number(result.adjustmentCoefficient).toFixed(4);
    }
    q("#adjustmentAmountResult").textContent = result.adjustmentAmountMm == null
            ? "待确认" : Number(result.adjustmentAmountMm).toFixed(2);
}

async function loadCurrentResult() {
    const runId = Number(q("#valueTaskSelect").value);
    if (runId) {
        await loadValueResultByRun(runId);
        return;
    }
    await loadCompletedValueTasks(q("#valueChannelStrip button.active"));
}

async function loadRecords() {
    const response = await fetch("/api/records");
    if (!response.ok) throw new Error("读取测量记录失败");
    recordRows = await response.json();
    renderRecords(recordRows);
}

function renderRecords(rows) {
    q("#recordCount").textContent = `${rows.length} 条任务`;
    q("#recordTableBody").innerHTML = rows.map(row => `<tr>
        <td>${escapeHtml(row.taskNo)}</td><td>${escapeHtml(row.taskName)}</td>
        <td>${escapeHtml(row.pointName)}</td>
        <td>${row.measuredForceKn == null ? "--" : `${Number(row.measuredForceKn).toFixed(2)} kN`}</td>
        <td><span class="table-status ${row.state === "COMPLETED" ? "saved" : "draft"}">${escapeHtml(stateName(row.state))}</span></td>
        <td>${escapeHtml(row.startedAt ? new Date(row.startedAt).toLocaleString("zh-CN", { hour12: false }) : "--")}</td>
        <td>${row.state === "COMPLETED"
            ? `<button data-record-run="${row.runId}" data-record-task="${row.taskId}">查看</button>`
            : ""}</td>
    </tr>`).join("") || '<tr><td colspan="7">暂无测量记录</td></tr>';
    qa("[data-record-run]").forEach(button => button.addEventListener("click", async () => {
        const row = recordRows.find(item => item.runId === Number(button.dataset.recordRun));
        try {
            if (row.state !== "COMPLETED") throw new Error("该任务尚未完成上升和下降流程");
            const pointIndex = pointCodes.indexOf(row.pointCode);
            const shaftPage = pointIndex >= 0 && pointIndex < shaftChannels.length;
            const container = q(shaftPage ? "#shaftCurveTabs" : "#mainCurveTabs");
            const page = curvePages[container.id];
            const tabIndex = shaftPage ? pointIndex : pointIndex - shaftChannels.length;
            const tab = qa(".curve-tab", container)[tabIndex];
            qa(".curve-tab", container).forEach(item => item.classList.toggle("active", item === tab));
            q(`.module-nav button[data-view="${shaftPage ? "shaft-curves" : "main-curves"}"]`).click();
            await loadCompletedCurveTasks(row.pointCode, page, tab);
            q(`#${page.selectId}`).value = String(row.runId);
            await loadCurveTask(row, page);
        } catch (error) {
            q("#recordCount").textContent = error.message;
        }
    }));
}

async function loadAlarms() {
    const response = await fetch("/api/alarms");
    if (!response.ok) throw new Error("读取报警失败");
    const alarms = await response.json();
    q("#totalAlarmCount").textContent = alarms.length;
    q("#activeAlarmCount").textContent = alarms.filter(item => ["ACTIVE", "ACKNOWLEDGED"].includes(item.status)).length;
    q("#alarmTableBody").innerHTML = alarms.map(alarm => {
        const action = { ACTIVE: "acknowledge", ACKNOWLEDGED: "recover", RECOVERED: "close" }[alarm.status];
        const label = { acknowledge: "确认", recover: "标记恢复", close: "关闭" }[action];
        return `<tr><td><span class="alarm-level ${alarm.alarmLevel === "CRITICAL" ? "high" : "medium"}">${escapeHtml(alarm.alarmLevel)}</span></td>
            <td>${escapeHtml(new Date(alarm.occurredAt).toLocaleString("zh-CN", { hour12: false }))}</td>
            <td>${escapeHtml(alarm.sourceName)}</td><td>${escapeHtml(alarm.message)}</td>
            <td>${escapeHtml(alarm.status)}</td><td>${action ? `<button data-alarm-id="${alarm.id}" data-alarm-action="${action}">${label}</button>` : "已结束"}</td></tr>`;
    }).join("") || '<tr><td colspan="6">暂无报警</td></tr>';
    const canHandle = document.body.dataset.role === "ADMIN";
    qa("[data-alarm-action]").forEach(button => {
        button.disabled = !canHandle;
        button.title = canHandle ? "" : "仅管理员可处理报警";
        button.addEventListener("click", () => handleAlarm(button.dataset.alarmId, button.dataset.alarmAction));
    });
}

async function handleAlarm(id, action) {
    const response = await fetch(`/api/alarms/${id}/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", [sessionContext.csrfHeaderName]: sessionContext.csrfToken },
        body: JSON.stringify({ remark: "Web 界面处理" })
    });
    if (!response.ok) throw new Error("报警状态更新失败");
    await loadAlarms();
}

function setupNavigation() {
    qa(".module-nav button").forEach(button => {
        button.addEventListener("click", () => {
            qa(".module-nav button").forEach(item => item.classList.remove("active"));
            button.classList.add("active");
            qa(".page").forEach(page => page.classList.toggle("active", page.dataset.page === button.dataset.view));
            if (button.dataset.view === "records") loadRecords().catch(() => {});
            if (button.dataset.view === "alarms") loadAlarms().catch(() => {});
            if (button.dataset.view === "values") q("#valueChannelStrip button.active")?.click();
            if (button.dataset.view === "shaft-curves") q("#shaftCurveTabs .curve-tab.active")?.click();
            if (button.dataset.view === "main-curves") q("#mainCurveTabs .curve-tab.active")?.click();
        });
    });
    const requestedView = new URLSearchParams(location.search).get("view");
    const requestedButton = requestedView && q(`.module-nav button[data-view="${requestedView}"]`);
    requestedButton?.click();
}

function setupAuthenticatedActions() {
    q("#saveSettingsButton").addEventListener("click", saveSettingsConfig);
    q("#logoutButton").addEventListener("click", async () => {
        await fetch("/logout", {
            method: "POST",
            headers: { [sessionContext.csrfHeaderName]: sessionContext.csrfToken }
        });
        location.href = "/login?logout";
    });
    q("#manualModeButton").addEventListener("click", () => sendControlCommand(
            selectedChannelState().mode === "MANUAL" ? "MANUAL_DISCONNECT" : "MANUAL_CONNECT")
            .catch(showControlError));
    q("#autoModeButton").addEventListener("click", () => sendControlCommand(
            selectedChannelState().mode === "AUTO" ? "AUTO_DISCONNECT" : "AUTO_CONNECT")
            .catch(showControlError));
    q("#zeroDisplacementButton").addEventListener("click", () => sendControlCommand("ZERO_DISPLACEMENT")
            .then(() => q('[data-reading="displacement"]').textContent = "0.00")
            .catch(showControlError));
    q("#manualStartButton").addEventListener("click", () => sendControlCommand("MANUAL_ENABLE")
            .catch(showControlError));
    q("#manualAscendButton").addEventListener("click", () => sendControlCommand("MANUAL_ASCEND", {
        targetDisplacementMm: null
    }).catch(showControlError));
    q("#targetDisplacementInput").addEventListener("input", () => {
        manualTargetDirty = true;
        renderControlState();
    });
    q("#saveManualTargetButton").addEventListener("click", () => saveManualTarget().catch(showControlError));
    q("#manualDescendButton").addEventListener("click", () => sendControlCommand("MANUAL_DESCEND")
            .catch(showControlError));
    q("#manualQuickReleaseButton").addEventListener("click", () => sendControlCommand("MANUAL_QUICK_RELEASE")
            .catch(showControlError));
    q("#manualPauseButton").addEventListener("click", () => sendControlCommand(
            selectedChannelState().paused ? "MANUAL_RESUME" : "MANUAL_PAUSE").catch(showControlError));
    q("#manualStopButton").addEventListener("click", () => sendControlCommand("MANUAL_STOP")
            .catch(showControlError));
    q("#startAutoTestButton").addEventListener("click", () => sendControlCommand("AUTO_TEST_START")
            .catch(showControlError));
    q("#autoQuickReleaseButton").addEventListener("click", () => sendControlCommand("AUTO_QUICK_RELEASE")
            .catch(showControlError));
    q("#autoPauseButton").addEventListener("click", () => sendControlCommand(
            (selectedChannelState().paused || controlState.globalPaused) ? "AUTO_RESUME" : "AUTO_PAUSE")
            .catch(showControlError));
    q("#autoStopButton").addEventListener("click", () => sendControlCommand("AUTO_STOP")
            .catch(showControlError));
    q("#refreshResultButton").addEventListener("click", () => loadCurrentResult()
            .catch(showValueResultError));
    q("#saveResultButton").addEventListener("click", () => saveCurrentValueResult()
            .catch(showValueResultError));
    q("#deleteResultButton").addEventListener("click", () => deleteCurrentValueResult()
            .catch(showValueResultError));
    q("#saveAdjustmentCoefficientButton").addEventListener("click", () => saveAdjustmentCoefficient()
            .catch(showValueResultError));
    q("#exportRecordsButton").addEventListener("click", () => { location.href = "/api/records/export"; });
    q("#newTaskFromRecordsButton").addEventListener("click", () => q('.module-nav button[data-view="work"]').click());
    q("#recordSearch").addEventListener("input", event => {
        const keyword = event.target.value.trim().toLowerCase();
        renderRecords(recordRows.filter(row => `${row.taskNo} ${row.taskName} ${row.pointName}`.toLowerCase().includes(keyword)));
    });
    q("#refreshAlarmsButton").addEventListener("click", () => loadAlarms().catch(() => {}));
    qa(".save-fit").forEach(button => {
        button.disabled = document.body.dataset.role !== "ADMIN";
        button.addEventListener("click", () => saveManualFit(button));
    });
    renderRunState();
}

function showControlError(error) {
    controlErrorMessage = error.message;
    controlErrorUntil = Date.now() + 4000;
    loadControlState().catch(() => {
        q("#runStateLabel").textContent = error.message;
    });
}

function startClock() {
    const render = () => q("#clock").textContent = new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false }).format(new Date());
    render();
    setInterval(render, 1000);
}

async function init() {
    renderChannelSelectors();
    renderSettingsTabs();
    renderSettingsForm();
    renderValueChannelSelector();
    renderCurveTabs();
    clearCurvePage(curvePages.shaftCurveTabs, "请选择通道和已完成测量任务");
    clearCurvePage(curvePages.mainCurveTabs, "请选择通道和已完成测量任务");
    await loadSession();
    await loadSettingsConfig();
    await loadControlState();
    const canEditCurve = document.body.dataset.role === "ADMIN";
    qa(".reset-fit").forEach(button => button.addEventListener("click", () => {
        if (!canEditCurve) return;
        // 只撤销当前页面未保存的拖动，原始端点已由所选任务的测量记录计算得出。
        q(`#${button.dataset.chart}`).resetFit?.();
    }));
    setupNavigation();
    setupAuthenticatedActions();
    await Promise.all([loadRecords(), loadAlarms()]);
    setInterval(() => loadControlState().catch(() => {}), 1000);
    startClock();
}

init().catch(error => {
    const notice = q("#settingsNotice");
    if (notice) showSettingsNotice("error", error.message);
});
