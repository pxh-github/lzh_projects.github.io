const API_BASE = window.SIMULATION_API_BASE || window.location.origin;

let map;
let timeRatio = 10;           // 模拟时间比
let vehicleMarkers = new Map();
let poiMap = new Map();
let poiLayer = null, poiVisible = false, poiLoading = false;
let cycleRunning = false;
let tickInFlight = false;
let animationGeneration = 0;
let animTimer = null;         // 动画/刷新定时器
let lastTaskId = 0;           // 已动画化的最后任务 ID
const playback = TaskPlayback.create();
let pageActive = true;
const activeRouteLines = new Set();
let eventVehicles = new Set();
let controlBusy = false;
let routeDisplayMode = 'curve';
let routePreviewing = false;
let animatingMarkers = new Map(); // vehicleId → count（引用计数，防并发动画）

window.onload = function () {
    // 1. 地图立即创建，不依赖任何后端
    try {
        map = new AMap.Map("map", {
            zoom: 5,
            center: [104.06, 30.67]
        });
    } catch (e) {
        console.error("地图加载失败", e);
        document.getElementById("map").textContent = "地图加载失败，请检查网络";
        return;
    }

    bindEvents();

    // 2. 数据异步加载，不阻塞地图
    initData();
};

async function initData() {
    const generation=animationGeneration;
    try {
        await alignPlayback();
        if(!pageActive||generation!==animationGeneration)return;
        await refreshAll();
        if(!pageActive||generation!==animationGeneration)return;
        await checkCycleStatus();
    } catch (e) { document.getElementById('route-motion-status').textContent='连接失败，正在重试…';setTimeout(()=>{if(pageActive)initData();},3000); }
}

async function alignPlayback() {
    const [status,tasks] = await Promise.all([
        axios.get(`${API_BASE}/dispatch/cycle/status`),
        axios.get(`${API_BASE}/dispatch/tasks`,{params:{limit:1,includeRoute:false}})
    ]);
    playback.enter(status.data.sceneId,Number(tasks.data?.[0]?.id || 0));
    lastTaskId=playback.cursor();
}
function cancelAnimations() {
    animationGeneration++;
    animatingMarkers.clear();
    activeRouteLines.forEach(line=>line.setMap(null));activeRouteLines.clear();
}

function bindEvents() {
    document.getElementById('toggle-poi').onclick=async()=>{
        if(poiLoading)return;
        poiVisible=!poiVisible;const button=document.getElementById('toggle-poi');
        button.setAttribute('aria-pressed',String(poiVisible));button.textContent=poiVisible?'隐藏 POI':'显示 POI';
        if(poiVisible && !poiLayer) {poiLoading=true;button.disabled=true;try{await loadPoiData();}catch(e){poiVisible=false;button.setAttribute('aria-pressed','false');button.textContent='显示 POI';document.getElementById('route-motion-status').textContent='POI 加载失败，请重试';}finally{poiLoading=false;button.disabled=false;}}
        if(poiLayer) {
            // MassMarks retains its map reference after setMap(null); hide it explicitly.
            if(poiVisible) {poiLayer.setMap(map);poiLayer.show();}
            else poiLayer.hide();
        }
    };
    document.getElementById('fit-vehicles').onclick=()=>{if(vehicleMarkers.size)map.setFitView([...vehicleMarkers.values()]);};
    document.getElementById('route-display-mode').onchange = function () {
        routeDisplayMode = this.value;
        document.getElementById('route-motion-status').textContent = routeDisplayMode === 'curve'
            ? '道路优先；缺少轨迹时使用曲线示意' : '原始路线（道路轨迹或端点连线）';
    };
    window._previewRoute = async () => {
        const button = document.getElementById('preview-route-btn');
        if (button.disabled) return;
        button.disabled = true;
        routePreviewing = true;
        document.getElementById('cycle-start-btn').disabled = true;
        const generation = animationGeneration;
        try {
            const state = await axios.get(`${API_BASE}/dispatch/cycle/status`);
            if (state.data.running) throw new Error('请先停止循环，再预览路线。');
            const [taskRes,vehicleRes] = await Promise.all([
                axios.get(`${API_BASE}/dispatch/tasks`, {params:{limit:1}}), axios.get(`${API_BASE}/vehicles`)
            ]);
            if (generation !== animationGeneration) return;
            const task = taskRes.data?.[0], vehicle = vehicleRes.data?.find(v=>v.id===task?.vehicleId);
            if (!task || !vehicle) throw new Error('暂无可预览的车辆任务。');
            ensureVehicleMarker(vehicle);
            const marker = vehicleMarkers.get(vehicle.id);
            if (!marker) throw new Error('车辆位置无效，暂无法预览。');
            try { await playVehicleContinuous(vehicle.id,[task],{preview:true,durationMs:6000}); }
            finally {
                if(generation===animationGeneration && vehicleMarkers.get(vehicle.id)===marker) marker.setPosition(parseCoord(vehicle.currentLocation));
            }
        } catch(e) { document.getElementById('route-motion-status').textContent = e.message; }
        finally {
            routePreviewing = false;
            button.disabled = cycleRunning;
            document.getElementById('cycle-start-btn').disabled = cycleRunning;
        }
    };
    window._startCycle = async () => {
        if(routePreviewing) return;
        try {
            await alignPlayback();
            await axios.post(`${API_BASE}/dispatch/cycle/start`);
            setCycleUI(true);
        } catch (err) { console.error(err); alert("启动失败"); }
    };
    window._stopCycle = async () => {
        try {
            await axios.post(`${API_BASE}/dispatch/cycle/stop`);
            setCycleUI(false);
            cancelAnimations();await refreshAll();
        } catch (err) { console.error(err); }
    };
    window._addVehicle = async () => {
        try { await axios.post(`${API_BASE}/vehicles/add`); await refreshAll(); } catch (err) { alert(err.response?.data?.message || "添加失败"); }
    };
    window._addOrder = async () => {
        try { await axios.post(`${API_BASE}/orders/add`); await refreshAll(); } catch (err) { alert(err.response?.data?.message || "添加失败"); }
    };
    window._resetAll = async () => {
        if (!confirm("确定重置？将清空所有数据并重新生成10辆车+20个订单。")) return;
        setCycleUI(false);
        try {
            await axios.post(`${API_BASE}/dispatch/reset-all`);
            cancelAnimations();await alignPlayback();
            vehicleMarkers.forEach(marker => marker.setMap(null)); vehicleMarkers.clear();
            window.dispatchEvent(new Event("simulation-reset"));
            await refreshAll();
        } catch (err) { alert("重置失败"); }
    };

    // ---- 时间比 ----
    document.getElementById("time-ratio-range").oninput = function () {
        timeRatio = Number(this.value);
        document.getElementById("time-ratio-value").textContent = timeRatio;
    };
    const controls={_startCycle:'cycle-start-btn',_stopCycle:'cycle-stop-btn',_addVehicle:'add-vehicle-btn',_addOrder:'add-order-btn',_resetAll:'reset-all-btn'};
    Object.entries(controls).forEach(([name,id])=>{
        const action=window[name];
        window[name]=async()=>{
            if(controlBusy)return;
            controlBusy=true;
            const button=document.getElementById(id),label=button.textContent;button.textContent='处理中…';
            Object.values(controls).forEach(buttonId=>document.getElementById(buttonId).disabled=true);
            document.getElementById('preview-route-btn').disabled=true;
            try{await action();}
            finally{controlBusy=false;button.textContent=label;Object.values(controls).forEach(buttonId=>document.getElementById(buttonId).disabled=false);setCycleUI(cycleRunning);}
        };
    });
}

// ---- 循环 UI ----
function setCycleUI(running) {
    cycleRunning = running;
    document.getElementById('preview-route-btn').disabled = running || routePreviewing || controlBusy;
    document.getElementById("cycle-start-btn").disabled = running || routePreviewing || controlBusy;
    document.getElementById("cycle-stop-btn").disabled = !running || controlBusy;
    const dot = document.querySelector(".status-dot");
    const text = document.getElementById("cycle-status-text");
    if (running) {
        dot.className = "status-dot on";
        text.textContent = "运行中";
        startCycleAnimationLoop();
    } else {
        dot.className = "status-dot off";
        text.textContent = "已暂停";
        stopCycleAnimationLoop();
    }
}

async function checkCycleStatus() {
    const res = await axios.get(`${API_BASE}/dispatch/cycle/status`);
    setCycleUI(!!res.data?.running);
}

// ---- 循环动画循环 ----
function startCycleAnimationLoop() {
    if (animTimer) return;
    animTimer = setInterval(cycleAnimationTick, 1500);
}

function stopCycleAnimationLoop() {
    if (animTimer) { clearInterval(animTimer); animTimer = null; }
}

async function cycleAnimationTick() {
    if (tickInFlight || !pageActive) return;
    tickInFlight = true;
    const generation=animationGeneration;
    try {
        const state=await axios.get(`${API_BASE}/dispatch/cycle/status`);
        if(generation!==animationGeneration || !pageActive)return;
        if(state.data.sceneId!==playback.scene()) {
            cancelAnimations();await alignPlayback();await refreshAll();setCycleUI(!!state.data.running);return;
        }
        // 获取最新任务和车辆数据
        const [taskRes, vehicleRes, statsRes, cycleRes] = await Promise.all([
            axios.get(`${API_BASE}/dispatch/tasks`, { params: { afterId: lastTaskId, limit: 500 } }),
            axios.get(`${API_BASE}/vehicles`),
            axios.get(`${API_BASE}/stats/overview`),
            axios.get(`${API_BASE}/dispatch/cycle/status`).catch(() => ({ data: {} }))
        ]);

        const tasks = taskRes.data || [];
        const vehicles = vehicleRes.data || [];
        const stats = statsRes.data || {};
        const cycle = cycleRes.data || {};
        if(generation!==animationGeneration || !pageActive)return;
        if(cycle.sceneId!==playback.scene()){cancelAnimations();await alignPlayback();await refreshAll();return;}

        // 更新统计面板
        document.getElementById("vehicle-count").textContent = stats.vehicleCount ?? 0;
        document.getElementById("idle-count").textContent = stats.idleVehicleCount ?? 0;
        document.getElementById("order-count").textContent = stats.orderCount ?? 0;
        document.getElementById("done-order-count").textContent = stats.doneOrderCount ?? 0;
        document.getElementById("task-count").textContent = stats.taskCount ?? 0;
        document.getElementById("total-revenue").textContent = stats.totalRevenue ?? 0;
        document.getElementById("total-cost").textContent = stats.totalCost ?? 0;
        document.getElementById("total-profit").textContent = stats.totalProfit ?? 0;
        document.getElementById("sim-time").textContent = cycle.simTime || "-";

        document.getElementById("cycle-stats").textContent =
            `累计: ${cycle.totalTasksGenerated || 0} | 上轮: ${cycle.lastRoundTasks || 0}`;

        // 检测循环是否已停止
        if (!cycle.running && cycleRunning) {
            setCycleUI(false);
            return;
        }
        if (!cycle.running) return;

        // 车辆列表
        renderVehicles(vehicles);

        // 确保所有相关车辆标记已创建（特别是新加的车辆）
        const taskVehicleIds = new Set(vehicles.map(v => v.id));
        vehicles.forEach(v => ensureVehicleMarker(v));

        // 动画新任务：先标记已认领，防止下个 tick 重复取
        const newTasks = playback.take(cycle.sceneId,tasks);
        lastTaskId=playback.cursor();
        if (newTasks.length > 0) {
            for (const t of newTasks) {
                const v = vehicles.find(vh => vh.id === t.vehicleId);
                if (v) ensureVehicleMarker(v);
            }
            await animateTaskBatch(newTasks);
        }

        // 刷新订单列表
        const orderRes = await axios.get(`${API_BASE}/orders`);
        renderOrders(orderRes.data || []);

    } catch (e) {
        // 下一轮继续重试
    } finally { tickInFlight = false; }
}

// ---- 车辆标记 ----
function ensureVehicleMarker(v) {
    if (vehicleMarkers.has(v.id)) return;
    const pos = parseCoord(v.currentLocation);
    if (!pos) return;
    const color = getVehicleColor(v.vehicleType);
    const marker = new AMap.Marker({
        position: pos,
        anchor: "center",
        content: `<div class="vehicle-marker${eventVehicles.has(v.id)?' event-affected':''}" style="background:${color}">${v.id}</div>`,
        map
    });
    marker.on("click", () => window.DashboardDetails?.vehicle(v.id));
    vehicleMarkers.set(v.id, marker);
}

function updateVehicleMarkers(vehicles) {
    const ids=new Set(vehicles.map(v=>v.id));
    vehicleMarkers.forEach((marker,id)=>{if(!ids.has(id)){marker.setMap(null);vehicleMarkers.delete(id);}});
    vehicles.forEach(v => {
        const pos = parseCoord(v.currentLocation);
        if (!pos) return;
        ensureVehicleMarker(v);
        const marker = vehicleMarkers.get(v.id);
        if (!marker) return;

        // 动画中的标记不干预，让 moveAlongPath 独占控制权
        if ((animatingMarkers.get(v.id) || 0) > 0) return;

        // 直接用 setPosition，不做平滑（平滑交给任务动画）
        marker.setPosition(pos);
    });
}

// ---- 任务动画 ----
async function animateTaskBatch(tasks) {
    const generation=animationGeneration;
    const grouped = new Map();
    tasks.forEach(t => {
        if (!grouped.has(t.vehicleId)) grouped.set(t.vehicleId, []);
        grouped.get(t.vehicleId).push(t);
    });
    grouped.forEach(list => list.sort((a, b) => (a.id || 0) - (b.id || 0)));

    const jobs = [];
    grouped.forEach((list, vid) => {
        jobs.push((async () => {
            while ((animatingMarkers.get(vid) || 0) > 0 && generation===animationGeneration && pageActive) {
                await new Promise(r => setTimeout(r, 100));
            }
            if(generation!==animationGeneration || !pageActive)return;
            await playVehicleContinuous(vid, list);
        })());
    });
    await Promise.all(jobs);
}

async function playVehicleContinuous(vehicleId, tasks, options = {}) {
    const marker = vehicleMarkers.get(vehicleId);
    if (!marker) return;

    // 将所有任务的路线拼接成一条连续路径
    const fullPath = [];
    let illustration = false;
    let totalTravelMin = 0;
    for (const task of tasks) {
        const geometry = RouteGeometry.prepare(task.routePolyline, routeDisplayMode);
        const segPath = geometry.path;
        illustration ||= geometry.illustration;
        if (segPath.length < 2) continue;
        // 去重：跳过与前一段终点相同的起点
        if (fullPath.length > 0) {
            const last = fullPath[fullPath.length - 1];
            const first = segPath[0];
            if (Math.abs(last[0] - first[0]) < 0.0001 && Math.abs(last[1] - first[1]) < 0.0001) {
                // 去掉重复的起点，追加剩余点
                for (let k = 1; k < segPath.length; k++) fullPath.push(segPath[k]);
            } else {
                for (const p of segPath) fullPath.push(p);
            }
        } else {
            for (const p of segPath) fullPath.push(p);
        }
        totalTravelMin += Number(task.pickupTimeH || 0) + Number(task.transportTimeH || 0);
    }

    if (fullPath.length < 2) {
        if(options.preview) throw new Error('任务没有有效路线，暂无法预览。');
        return;
    }

    animatingMarkers.set(vehicleId, (animatingMarkers.get(vehicleId) || 0) + 1);

    // 绘制路径折线
    const vMarker = vehicleMarkers.get(vehicleId);
    let lineColor = '#3b82f6';
    if (vMarker) {
        try {
            const c = vMarker.getContent();
            const str = typeof c === 'string' ? c : (c?.innerHTML || c?.toString?.() || '');
            const m = str.match(/#[0-9a-fA-F]{6}/);
            if (m) lineColor = m[0];
        } catch(e) { console.warn('color extract fail', e); }
    }
    const routeLine = new AMap.Polyline({
        path: fullPath.map(p => new AMap.LngLat(p[0], p[1])),
        strokeColor: lineColor,
        strokeWeight: 4,
        strokeOpacity: 0.85,
        strokeStyle: illustration ? 'dashed' : 'solid',
        map: map,
        zIndex: 80,
        lineJoin: 'round'
    });
    activeRouteLines.add(routeLine);

    document.getElementById('route-motion-status').textContent = `${options.preview?'路线预览':'车辆行驶'}：${illustration?'曲线示意（虚线，非实际道路）':routeDisplayMode==='original'?'原始路线':'道路轨迹'}`;
    const generation = animationGeneration;
    const durationMs = options.durationMs ?? Math.max(totalTravelMin * 500 / timeRatio, 300);
    try {
        if(options.preview) map.setFitView([routeLine]);
        marker.setPosition(fullPath[0]);
        await moveAlongPath(marker, fullPath, durationMs);
    } finally {
        if(generation !== animationGeneration) {routeLine.setMap(null);activeRouteLines.delete(routeLine);return;}
        setTimeout(() => {routeLine.setMap(null);activeRouteLines.delete(routeLine);}, 2000);
        const cnt = (animatingMarkers.get(vehicleId) || 1) - 1;
        if (cnt <= 0) animatingMarkers.delete(vehicleId);
        else animatingMarkers.set(vehicleId, cnt);
    }
}

function moveAlongPath(marker, path, durationMs) {
    return new Promise(resolve => {
        const route = RouteGeometry.measure(path || []);
        if (route.path.length < 2 || route.total === 0) { resolve(); return; }

        const generation = animationGeneration;
        const start = performance.now();

        const anim = () => {
            if (generation !== animationGeneration) { resolve(); return; }
            const elapsed = performance.now() - start;
            const t = Math.min(elapsed / Math.max(1,durationMs), 1.0);
            marker.setPosition(RouteGeometry.position(route,t));

            if (t < 1) {
                requestAnimationFrame(anim);
            } else {
                marker.setPosition(path[path.length - 1]);
                resolve();
            }
        };
        requestAnimationFrame(anim);
    });
}

// ---- 数据展示 ----
async function refreshAll() {
    try {
        const [vehiclesRes, ordersRes, statsRes, cycleRes] = await Promise.all([
            axios.get(`${API_BASE}/vehicles`),
            axios.get(`${API_BASE}/orders`),
            axios.get(`${API_BASE}/stats/overview`),
            axios.get(`${API_BASE}/dispatch/cycle/status`).catch(() => ({ data: {} }))
        ]);
        const vehicles = vehiclesRes.data || [];
        const orders = ordersRes.data || [];
        const stats = statsRes.data || {};
        const cycle = cycleRes.data || {};

        document.getElementById("vehicle-count").textContent = stats.vehicleCount ?? 0;
        document.getElementById("idle-count").textContent = stats.idleVehicleCount ?? 0;
        document.getElementById("order-count").textContent = stats.orderCount ?? 0;
        document.getElementById("done-order-count").textContent = stats.doneOrderCount ?? 0;
        document.getElementById("task-count").textContent = stats.taskCount ?? 0;
        document.getElementById("total-revenue").textContent = stats.totalRevenue ?? 0;
        document.getElementById("total-cost").textContent = stats.totalCost ?? 0;
        document.getElementById("total-profit").textContent = stats.totalProfit ?? 0;
        document.getElementById("sim-time").textContent = cycle.simTime || "-";

        renderVehicles(vehicles);
        renderOrders(orders);
        updateVehicleMarkers(vehicles);
    } catch (e) { /* ignore */ }
}

function renderVehicles(list) {
    const box = document.getElementById("car-list");
    box.innerHTML = "";
    list.forEach(v => {
        const div = document.createElement("div");
        div.className = "car-item";
        const b = v.status === "IDLE" ? "badge-idle" : "badge-busy";
        div.innerHTML = `<div><strong>${v.vehicleCode}</strong><span class="badge ${b}">${v.status}</span></div>
            <div>车型：${v.vehicleType} | 载重：${v.maxLoadTon}T</div>
            <div>速度：${v.actualSpeedKmh} km/h</div>
            <div>位置：${v.currentLocationName || "-"}</div>
            <div>累计净收益：${v.totalProfit || 0}</div>`;
        div.onclick = () => window.DashboardDetails?.vehicle(v.id);
        box.appendChild(div);
    });
}

function renderOrders(list) {
    const box = document.getElementById("order-list");
    box.innerHTML = "";
    // 过滤掉已完成的订单，只显示未完成的
    const activeOrders = list.filter(o => o.status !== "DONE");
    if (activeOrders.length === 0) {
        box.innerHTML = '<div style="padding:12px;color:#94a3b8;text-align:center">全部完成</div>';
        return;
    }
    activeOrders.forEach(o => {
        let c = "badge-pending";
        if (o.status === "PARTIAL" || o.status === "ASSIGNED") c = "badge-busy";
        const div = document.createElement("div");
        div.className = "order-item";
        div.innerHTML = `<div><strong>${o.orderCode}</strong><span class="badge ${c}">${o.status}</span></div>
            <div>货物：${o.cargoType} | 总量：${o.cargoTotalWeight}T</div>
            <div>已分配：${o.allocatedWeight}T | 待处理：${o.pendingWeight}T</div>
            <div>${o.pickupLocationName} → ${o.deliveryLocationName}</div>
            <div>路程：${o.routeDistanceKm} km</div>`;
        div.onclick = () => window.DashboardDetails?.order(o.id);
        box.appendChild(div);
    });
}

// ---- 工具 ----
function parseCoord(loc) {
    if (!loc || typeof loc !== "string") return null;
    const parts = loc.split(",");
    if (parts.length < 2) return null;
    const lng = Number(parts[0]), lat = Number(parts[1]);
    if (isNaN(lng) || isNaN(lat)) return null;
    if (lng === 0 && lat === 0) return null;
    // 经度范围 -180~180，纬度范围 -90~90
    if (lng < -180 || lng > 180 || lat < -90 || lat > 90) return null;
    return [lng, lat];
}

function isValidCoord(lng, lat) {
    return !isNaN(lng) && !isNaN(lat)
        && lng >= -180 && lng <= 180
        && lat >= -90 && lat <= 90;
}

function parsePolyline(polyline) {
    if (!polyline) return [];
    const pts = [];
    polyline.split(";").forEach(item => {
        const p = parseCoord(item);
        if (p) pts.push(p);
    });
    return pts;
}

async function loadPoiData() {
        const res = await axios.get(`${API_BASE}/user`);
        (res.data || []).forEach(p => poiMap.set(String(p.id), p));
        // Reuse the existing AMap MassMarks POI approach and local icon assets.
        const types = ["010100", "030100", "170205", "170209", "170300", "170402"];
        const styles = types.map(type => ({ url: `./icons/A/${type}.svg`, anchor: new AMap.Pixel(12, 12), size: new AMap.Size(24, 24) }));
        const points = (res.data || []).map(p => ({ lnglat: parseCoord(p.location), name: p.location_name || p.name || String(p.id), style: Math.max(0, types.indexOf(p.type === '170404' ? '170402' : p.type)) })).filter(p => p.lnglat);
        poiLayer = new AMap.MassMarks(points, { zIndex: 70, style: styles, opacity: 0.85 });
        poiLayer.on('click', e => { const content = document.createElement('div'); content.textContent = e.data.name; new AMap.InfoWindow({ content }).open(map, e.data.lnglat); });
}

window.updateLiveEvents = events => {
    eventVehicles=new Set(events.filter(e=>e.status==='ACTIVE').flatMap(e=>e.affectedVehicleIds));
    vehicleMarkers.forEach((marker,id)=>{const content=marker.getContent();if(typeof content==='string')marker.setContent(content.replace(/class="vehicle-marker(?: event-affected)?"/,`class="vehicle-marker${eventVehicles.has(id)?' event-affected':''}"`));});
};
window.applyMonitorSnapshot = snapshot => {
    if(!pageActive || !playback.scene())return;
    if(snapshot.sceneId!==playback.scene()) {
        cancelAnimations();playback.enter(snapshot.sceneId,Math.max(0,...snapshot.tasks.map(t=>Number(t.id)||0)));lastTaskId=playback.cursor();
    }
    renderVehicles(snapshot.vehicles);renderOrders(snapshot.orders);updateVehicleMarkers(snapshot.vehicles);
    if(cycleRunning!==!!snapshot.cycle.running)setCycleUI(!!snapshot.cycle.running);
};
window.addEventListener('pagehide',()=>{pageActive=false;stopCycleAnimationLoop();cancelAnimations();});
window.addEventListener('pageshow',e=>{if(e.persisted){pageActive=true;initData();}});
window.addEventListener('live-event-created',()=>{cancelAnimations();refreshAll();});
document.addEventListener('visibilitychange',()=>{
    if(document.hidden){pageActive=false;stopCycleAnimationLoop();cancelAnimations();}
    else if(!pageActive){pageActive=true;initData();}
});

function getVehicleColor(type) {
    switch (type) {
        case "SMALL_1_5T": return "#10b981";
        case "MEDIUM_6T": return "#3b82f6";
        case "LARGE_15T": return "#f59e0b";
        case "HEAVY_40T": return "#ef4444";
        default: return "#6b7280";
    }
}
