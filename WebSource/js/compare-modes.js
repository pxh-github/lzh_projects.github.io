const els = {
    vehicles: document.getElementById("vehicles"),
    orders: document.getElementById("orders"),
    btnCompareCurrent: document.getElementById("btn-compare-current"),
    btnGenerateCompare: document.getElementById("btn-generate-compare"),
    btnRefreshOverview: document.getElementById("btn-refresh-overview"),
    summary: document.getElementById("summary"),
    baselineMetrics: document.getElementById("baseline-metrics"),
    optimizedMetrics: document.getElementById("optimized-metrics"),
    compareNote: document.getElementById("compare-note"),
    status: document.getElementById("status")
};

const requiredKeys = [
    "vehicles",
    "orders",
    "btnCompareCurrent",
    "btnGenerateCompare",
    "btnRefreshOverview",
    "summary",
    "baselineMetrics",
    "optimizedMetrics",
    "compareNote",
    "status"
];

const hasRequiredDom = requiredKeys.every((key) => els[key]);

const METRICS = [
    ["vehicleCount", "车辆数", (v) => `${v ?? 0}`],
    ["orderCount", "订单数", (v) => `${v ?? 0}`],
    ["doneOrderCount", "完成订单", (v) => `${v ?? 0}`],
    ["taskCount", "派发任务", (v) => `${v ?? 0}`],
    ["profitPerHour", "每小时利润", (v) => `${num(v)} 元/小时`],
    ["totalProfit", "总利润", (v) => `${num(v)} 元`],
    ["totalTaskHours", "总运输时长", (v) => `${num(v)} 小时`],
    ["totalRevenue", "总收入", (v) => `${num(v)} 元`],
    ["totalCost", "总成本", (v) => `${num(v)} 元`],
    ["totalPendingWeight", "剩余待运重量", (v) => `${num(v)} 吨`],
    ["totalEmptyMileageKm", "空驶里程", (v) => `${num(v)} km`],
    ["totalLoadedMileageKm", "载货里程", (v) => `${num(v)} km`],
    ["emptyMileageRate", "空驶率", (v) => `${num(v)} %`],
    ["avgLoadUtilization", "平均装载率", (v) => `${num(v)} %`],
    ["totalCargoWaitHours", "货物等待总时长", (v) => `${num(v)} 小时`]
];

function num(value) {
    const n = Number(value ?? 0);
    return Number.isFinite(n) ? n.toFixed(2) : "0.00";
}

function setLoading(loading, text) {
    if (!hasRequiredDom) {
        return;
    }
    els.btnCompareCurrent.disabled = loading;
    els.btnGenerateCompare.disabled = loading;
    els.btnRefreshOverview.disabled = loading;
    els.status.textContent = text || "";
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...options
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    return response.json();
}

function metricRow(label, value, klass = "neutral") {
    return `
        <div class="metric-row">
            <span class="metric-label">${label}</span>
            <span class="metric-value ${klass}">${value}</span>
        </div>
    `;
}

function metricClass(key, stats) {
    if (key === "profitPerHour" || key === "totalProfit") {
        return Number(stats[key] ?? 0) >= 0 ? "good" : "bad";
    }
    return "neutral";
}

function renderStats(container, stats) {
    if (!container || !stats) {
        return;
    }
    container.innerHTML = METRICS.map(([key, label, formatter]) => {
        return metricRow(label, formatter(stats[key]), metricClass(key, stats));
    }).join("");
}

function renderSummary(compare) {
    if (!els.summary || !compare) {
        return;
    }

    const hourlyDelta = Number(compare.hourlyProfitDelta ?? 0);
    const hourlyRate = Number(compare.hourlyProfitDeltaRate ?? 0);
    const totalProfitDelta = Number(compare.profitDelta ?? 0);
    const emptyDelta = Number(compare.emptyMileageRateDelta ?? 0);
    const waitDelta = Number(compare.waitHoursDelta ?? 0);

    const cards = [
        {
            label: "每小时利润差值",
            value: `${num(compare.hourlyProfitDelta)} 元/小时`,
            klass: hourlyDelta >= 0 ? "good" : "bad"
        },
        {
            label: "每小时利润提升率",
            value: `${num(compare.hourlyProfitDeltaRate)} %`,
            klass: hourlyRate >= 0 ? "good" : "bad"
        },
        {
            label: "总利润差值",
            value: `${num(compare.profitDelta)} 元`,
            klass: totalProfitDelta >= 0 ? "good" : "bad"
        },
        {
            label: "空驶率变化",
            value: `${num(compare.emptyMileageRateDelta)} %`,
            klass: emptyDelta <= 0 ? "good" : "bad"
        },
        {
            label: "等待时长变化",
            value: `${num(compare.waitHoursDelta)} 小时`,
            klass: waitDelta <= 0 ? "good" : "bad"
        }
    ];

    els.summary.innerHTML = cards.map((item) => `
        <div class="summary-card">
            <span class="label">${item.label}</span>
            <div class="value ${item.klass}">${item.value}</div>
        </div>
    `).join("");
}

function renderCompare(compare) {
    if (!compare) {
        return;
    }

    renderSummary(compare);
    renderStats(els.baselineMetrics, compare.baseline);
    renderStats(els.optimizedMetrics, compare.optimized);

    if (!els.compareNote) {
        return;
    }

    const guardText = compare.guardApplied
        ? "优化结果低于基线时，系统已自动回退到基线结果。"
        : "优化结果满足不低于基线的约束。";
    const hourlyBetter = Number(compare.hourlyProfitDelta ?? 0) >= 0 ? "不低于" : "低于";

    els.compareNote.innerHTML = `
        对比时间：${compare.comparedAt || "-"}<br>
        当前场景：${compare.vehicleCount ?? 0} 辆车，${compare.orderCount ?? 0} 个订单。<br>
        主目标指标：${compare.objectiveMetric || "PROFIT_PER_HOUR"}。优化模式每小时利润${hourlyBetter}基线。<br>
        完成订单差值：${compare.doneOrderDelta ?? 0} 单。${guardText}
    `;
}

async function loadCompareCurrent() {
    setLoading(true, "正在基于当前数据库场景进行对比...");
    try {
        const compare = await requestJson("/stats/compare");
        renderCompare(compare);
        setLoading(false, "当前场景对比已完成。");
    } catch (error) {
        setLoading(false, `对比失败：${error.message}`);
    }
}

async function generateAndCompare() {
    const vehicles = Number(els.vehicles?.value || 10);
    const orders = Number(els.orders?.value || 20);
    setLoading(true, "正在生成新场景并执行后端对比...");
    try {
        const result = await requestJson(`/stats/compare?vehicles=${vehicles}&orders=${orders}`, {
            method: "POST"
        });
        renderCompare(result.compare);
        setLoading(false, `已生成 ${result.vehicles} 辆车、${result.orders} 个订单，并完成模式对比。`);
    } catch (error) {
        setLoading(false, `生成并对比失败：${error.message}`);
    }
}

async function refreshOverview() {
    setLoading(true, "正在刷新总览数据...");
    try {
        const overview = await requestJson("/stats/overview");
        els.status.textContent =
            `当前总览：${overview.vehicleCount ?? 0} 辆车，${overview.orderCount ?? 0} 个订单，` +
            `完成 ${overview.doneOrderCount ?? 0} 单，每小时利润 ${num(overview.profitPerHour)} 元/小时。`;
        els.btnCompareCurrent.disabled = false;
        els.btnGenerateCompare.disabled = false;
        els.btnRefreshOverview.disabled = false;
    } catch (error) {
        setLoading(false, `刷新总览失败：${error.message}`);
    }
}

if (!hasRequiredDom) {
    console.warn("compare-modes.js: required DOM nodes are missing, skipped initialization");
} else {
    els.btnCompareCurrent.addEventListener("click", loadCompareCurrent);
    els.btnGenerateCompare.addEventListener("click", generateAndCompare);
    els.btnRefreshOverview.addEventListener("click", refreshOverview);
    loadCompareCurrent();
}
