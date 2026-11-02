const API_BASE = "http://localhost:9091";

let map;
let vehicleMarker = null;
let vehicleData = null;
let ordersData = [];
let currentPos = null;
let currentAvail = null;
let remainingCap = 0;
let stepCount = 0;
let totalProfit = 0;

window.onload = function () {
    try {
        map = new AMap.Map("map", { zoom: 5, center: [104.06, 30.67] });
    } catch (e) {
        console.error("Map failed", e);
        return;
    }
    document.getElementById("init-demo-btn").onclick = initDemo;
    document.getElementById("step-btn").onclick = stepNF;
};

async function initDemo() {
    const btn = document.getElementById("init-demo-btn");
    btn.disabled = true;
    try {
        // 重置：1车8单
        await axios.post(`${API_BASE}/dispatch/reset-all?vehicles=1&orders=8`);
        stepCount = 0;
        totalProfit = 0;

        // 获取车辆和订单数据
        const [vRes, oRes] = await Promise.all([
            axios.get(`${API_BASE}/vehicles`),
            axios.get(`${API_BASE}/orders`)
        ]);
        vehicleData = vRes.data[0];
        ordersData = oRes.data.filter(o => o.status !== "DONE");

        if (!vehicleData) { alert("没有车辆"); return; }

        currentPos = vehicleData.currentLocation;
        currentAvail = new Date().toISOString();
        remainingCap = vehicleData.maxLoadTon;

        // 在地图上标记车辆
        const pos = parseCoord(currentPos);
        if (pos) {
            if (vehicleMarker) vehicleMarker.remove();
            vehicleMarker = new AMap.Marker({
                position: pos,
                anchor: "center",
                content: '<div class="vehicle-marker" style="background:#3b82f6">1</div>',
                map
            });
            map.setCenter(pos);
            map.setZoom(6);
        }

        updateUI();
        document.getElementById("step-btn").disabled = false;
        document.getElementById("step-list").innerHTML =
            '<div style="color:#94a3b8;text-align:center;padding:12px">已初始化，点击「逐步装车」开始 NF 装车</div>';
    } catch (e) {
        console.error(e);
        alert("初始化失败");
    } finally {
        btn.disabled = false;
    }
}

async function stepNF() {
    if (!vehicleData || ordersData.length === 0) return;
    if (remainingCap <= 0) {
        alert("车辆已装满！剩余载重为0");
        return;
    }

    const btn = document.getElementById("step-btn");
    btn.disabled = true;

    try {
        // 调用后端执行一轮单步调度
        const res = await axios.post(`${API_BASE}/dispatch/run-batch`);
        const tasks = res.data?.tasks || [];

        if (tasks.length === 0) {
            // 没有新任务，尝试手动找最佳订单
            await manualStep();
        } else {
            // 有任务被分配，展示
            for (const task of tasks) {
                await showTaskStep(task);
            }
        }

        // 刷新数据
        const [vRes, oRes] = await Promise.all([
            axios.get(`${API_BASE}/vehicles`),
            axios.get(`${API_BASE}/orders`)
        ]);
        vehicleData = vRes.data[0];
        ordersData = oRes.data.filter(o => o.status !== "DONE");

        if (vehicleData) {
            currentPos = vehicleData.currentLocation;
            remainingCap = vehicleData.maxLoadTon - (vehicleData.totalMileageKm || 0) * 0; // approximate
            // Recalculate remaining capacity from DB
            if (ordersData.length > 0) {
                // Update marker position
                const pos = parseCoord(currentPos);
                if (pos && vehicleMarker) vehicleMarker.setPosition(pos);
            }
        }
        updateUI();

        if (ordersData.length === 0) {
            document.getElementById("step-list").innerHTML +=
                '<div class="step-card" style="background:#dbeafe;border-color:#93c5fd">全部订单已完成！总净收益：' +
                totalProfit.toFixed(2) + '</div>';
            btn.disabled = true;
        }
    } catch (e) {
        console.error(e);
        alert("调度失败: " + (e.response?.data?.message || e.message));
    } finally {
        btn.disabled = ordersData.length === 0;
    }
}

async function manualStep() {
    // 手动计算：对每个剩余订单，计算成本
    const candidates = [];
    for (const order of ordersData) {
        if (!order.pendingWeight || order.pendingWeight <= 0) continue;
        const result = calcTaskCost(order);
        if (result) candidates.push({ order, ...result });
    }

    if (candidates.length === 0) {
        document.getElementById("step-list").innerHTML +=
            '<div class="step-card" style="background:#fef2f2;border-color:#fecaca">无可接订单（距离过远或不划算）</div>';
        return;
    }

    // 按 Score 排序
    candidates.sort((a, b) => b.score - a.score);
    const best = candidates[0];

    // 显示所有候选订单的得分
    let html = '<div class="step-card"><b>第 ' + (++stepCount) + ' 步：评估所有剩余订单</b>';
    html += '<table><tr><th>订单</th><th>重量(T)</th><th>取货(km)</th><th>运输(km)</th><th>收益</th><th>成本</th><th>净利</th><th>Score</th></tr>';
    for (const c of candidates) {
        const npClass = c.netProfit > 0 ? 'profit-positive' : 'profit-negative';
        html += `<tr>
            <td>${c.order.orderCode}</td>
            <td>${c.order.pendingWeight}</td>
            <td>${c.pickupKm.toFixed(0)}</td>
            <td>${c.order.routeDistanceKm}</td>
            <td>${c.revenue.toFixed(1)}</td>
            <td>${c.totalCost.toFixed(1)}</td>
            <td class="${npClass}">${c.netProfit.toFixed(1)}</td>
            <td>${c.score.toFixed(1)}</td>
        </tr>`;
    }
    html += '</table>';

    // 选中最佳
    html += `<p>✅ 选择 <b>${best.order.orderCode}</b>：净收益=${best.netProfit.toFixed(2)}，Score=${best.score.toFixed(2)}</p>`;
    html += `<p style="font-size:11px;color:#64748b">
        Cost1(运输)=${best.cost1.toFixed(1)} | Cost2(损耗)=${best.cost2.toFixed(1)} | Cost3(装卸)=${best.cost3.toFixed(1)}</p>`;

    html += '</div>';
    document.getElementById("step-list").innerHTML += html;

    totalProfit += best.netProfit;
}

// ---- 成本计算（与后端 buildTask 一致） ----
function calcTaskCost(order) {
    const vehicleSpeed = vehicleData.actualSpeedKmh || 80;
    const fuelCostPerKm = vehicleData.fuelCostPerKm || 0.72;
    const wearCostPerHour = vehicleData.wearCostPerHour || 18;

    const straight = calcStraightDeg(currentPos, order.pickupLocation);
    if (straight < 0) return null;

    const pickupKm = straight * 111 * 1.25;
    if (pickupKm > 500) return null;

    const transportKm = Number(order.routeDistanceKm) || 0;
    if (transportKm <= 0) return null;

    const pickupTimeH = pickupKm / vehicleSpeed;
    const transportTimeH = transportKm / vehicleSpeed;

    const assignW = Math.min(remainingCap, Number(order.pendingWeight));

    // Cost1
    const cost1 = (pickupTimeH + transportTimeH) * wearCostPerHour + (pickupKm + transportKm) * fuelCostPerKm;

    // Cost2
    const cargoWaitH = pickupTimeH; // simplified: since order just created
    const cargoLossExpK = 1.05;
    const cargoLossBase = 3.0;
    const cost2 = assignW * cargoLossBase * (Math.pow(cargoLossExpK, cargoWaitH) - 1);

    // Cost3
    const cost3 = assignW * 8;

    const revenue = assignW * transportKm * 1.5;
    const totalCost = cost1 + cost2 + cost3;
    const netProfit = revenue - totalCost;
    const score = netProfit + Math.min(cargoWaitH * 20, 300) + assignW * cargoWaitH * 5;

    return {
        pickupKm, pickupTimeH, transportTimeH,
        cost1, cost2, cost3, totalCost, revenue, netProfit, score
    };
}

async function showTaskStep(task) {
    stepCount++;
    const npClass = task.netProfit > 0 ? 'profit-positive' : 'profit-negative';
    let html = `<div class="step-card"><b>第 ${stepCount} 步：分配任务</b>`;
    html += `<table><tr><th>订单</th><th>载重(T)</th><th>取货(km)</th><th>运输(km)</th><th>收益</th><th>成本</th><th>净利</th><th>Score</th></tr>`;
    html += `<tr>
        <td>#${task.orderId}</td>
        <td>${task.assignedWeight}</td>
        <td>${task.pickupDistanceKm}</td>
        <td>${task.transportDistanceKm}</td>
        <td>${task.totalRevenue}</td>
        <td>${task.totalCost}</td>
        <td class="${npClass}">${task.netProfit}</td>
        <td>${task.score}</td>
    </tr></table>`;
    html += `<p style="font-size:11px;color:#64748b">
        Cost1(运输)=${task.cost1} | Cost2(损耗)=${task.cost2} | Cost3(装卸)=${task.cost3}</p>`;
    html += '</div>';
    document.getElementById("step-list").innerHTML += html;

    // 动画
    if (vehicleMarker && task.routePolyline) {
        const path = parsePolyline(task.routePolyline);
        if (path.length >= 2) {
            const travelMin = (Number(task.pickupTimeH) + Number(task.transportTimeH)) * 60;
            const dur = Math.max(travelMin * 4, 40);
            await moveAlongPath(vehicleMarker, path, dur);
        }
    }

    totalProfit += Number(task.netProfit || 0);
    remainingCap -= Number(task.assignedWeight || 0);
    currentPos = task.routePolyline ? task.routePolyline.split(";").pop() : currentPos;
}

function updateUI() {
    if (vehicleData) {
        document.getElementById("veh-info").textContent =
            `${vehicleData.vehicleCode} (${vehicleData.vehicleType})`;
        document.getElementById("remain-cap").textContent =
            (vehicleData.maxLoadTon - (vehicleData.totalMileageKm || 0) * 0).toFixed(1);
        document.getElementById("current-loc").textContent =
            vehicleData.currentLocationName || "-";
    }
    document.getElementById("total-profit").textContent = totalProfit.toFixed(2);

    // 更新剩余载重
    if (vehicleData) {
        // 从后端获取更准确的剩余载重
        document.getElementById("remain-cap").textContent = remainingCap.toFixed(1);
    }

    // 渲染剩余订单
    renderRemainingOrders();
}

function renderRemainingOrders() {
    const box = document.getElementById("order-list");
    box.innerHTML = "";
    if (ordersData.length === 0) {
        box.innerHTML = '<div style="text-align:center;color:#10b981;padding:12px">全部完成！</div>';
        return;
    }
    ordersData.forEach(o => {
        const div = document.createElement("div");
        div.className = "order-item";
        div.innerHTML = `<div><strong>${o.orderCode}</strong></div>
            <div>货物：${o.cargoType} | 待处理：${o.pendingWeight}T</div>
            <div>${o.pickupLocationName} → ${o.deliveryLocationName}</div>
            <div>路程：${o.routeDistanceKm} km</div>`;
        box.appendChild(div);
    });
}

// ---- 工具 ----
function parseCoord(loc) {
    if (!loc) return null;
    const p = loc.split(",");
    if (p.length < 2) return null;
    const lng = Number(p[0]), lat = Number(p[1]);
    if (isNaN(lng) || isNaN(lat)) return null;
    if (lng < -180 || lng > 180 || lat < -90 || lat > 90) return null;
    return [lng, lat];
}

function calcStraightDeg(p1, p2) {
    try {
        const a = p1.split(","), b = p2.split(",");
        return Math.sqrt(Math.pow(Number(a[0]) - Number(b[0]), 2) + Math.pow(Number(a[1]) - Number(b[1]), 2));
    } catch (e) { return -1; }
}

function parsePolyline(s) {
    if (!s) return [];
    const pts = [];
    s.split(";").forEach(item => {
        const p = parseCoord(item);
        if (p) pts.push(p);
    });
    return pts;
}

function moveAlongPath(marker, path, dur) {
    return new Promise(resolve => {
        if (path.length < 2) { resolve(); return; }
        const segs = [];
        let total = 0;
        for (let i = 1; i < path.length; i++) {
            const d = Math.sqrt(Math.pow(path[i][0]-path[i-1][0],2) + Math.pow(path[i][1]-path[i-1][1],2));
            segs.push(d); total += d;
        }
        if (total === 0) { resolve(); return; }
        const start = performance.now();
        const anim = () => {
            const t = Math.min((performance.now() - start) / dur, 1);
            let target = t * total, accum = 0, si = 0;
            for (let i = 0; i < segs.length; i++) {
                if (accum + segs[i] >= target) { si = i; break; }
                accum += segs[i]; si = i;
            }
            const st = segs[si] > 0 ? (target - accum) / segs[si] : 0;
            const ct = Math.max(0, Math.min(1, st));
            const p1 = path[si], p2 = path[Math.min(si+1, path.length-1)];
            marker.setPosition([p1[0] + (p2[0]-p1[0])*ct, p1[1] + (p2[1]-p1[1])*ct]);
            if (t < 1) requestAnimationFrame(anim);
            else { marker.setPosition(path[path.length-1]); resolve(); }
        };
        requestAnimationFrame(anim);
    });
}
