/******************************************************
 * 高德物流多车循环仿真（JSAPI 2.0）
 ******************************************************/

let map = new AMap.Map("map", {
    zoom: 4,
    center: [116.38, 39.9]
});

// ---------------------- 全局数据 ----------------------
let poiList = [];
let carList = [];
let carIdCounter = 1;

// 小车速度（越大越快）
let speedFactor = 50;

// ---------------------- 读取POI数据 ----------------------
axios.get("http://localhost:9090/user").then(res => {
    poiList = res.data;
    console.log("POI 数据加载成功:", poiList);
});


// ----------------------------------------------------------
// 规则：type → 可前往的类型列表
// ----------------------------------------------------------
const nextTypeMap = {
    "170402": ["170300", "030100", "010100"],
    "170404": ["170300", "030100", "010100"],
    "170300": ["170402", "170404", "030100", "010100"],
    "030100": ["170209", "170205", "170300", "170402", "170404"],
    "010100": ["170209", "170205", "170300", "170402", "170404"],
    "170209": ["030100", "170205", "010100"],
    "170205": ["170209", "030100", "010100"]
};


// ----------------------------------------------------------
// 状态栏规则
// ----------------------------------------------------------
function getStatusByType(type, isStop) {

    if (isStop) {
        if (type === "030100") return "维修中";
        if (type === "010100") return "加油中";
        return "休息中";
    }

    if (["170402", "170404", "170209"].includes(type)) return "取货";
    if (["170205", "170300"].includes(type)) return "送货";
    if (type === "030100") return "去维修";
    if (type === "010100") return "去加油";

    return "未知";
}


// ----------------------------------------------------------
// 按规则选择下一个POI
// ----------------------------------------------------------
function chooseNextPOI(currentPOI) {

    const types = nextTypeMap[currentPOI.type];
    if (!types) return null;

    const possible = poiList.filter(p => types.includes(p.type));
    if (possible.length === 0) return null;
    return possible[Math.floor(Math.random() * possible.length)];

}


// ----------------------------------------------------------
// 路径规划（高德 Web 服务 newroute）
// ----------------------------------------------------------
async function getRoute(startLoc, endLoc) {
    const res = await axios.get("http://localhost:9090/amap/driving", {
        params: { origin: startLoc, destination: endLoc }
    });

    // 兼容两种后端返回：
    // 1) { ok:true, raw:"{...高德JSON...}" }
    // 2) 直接把高德JSON原样返回（对象）
    let data = res.data?.raw ? JSON.parse(res.data.raw) : res.data;

    const route = data?.route;
    const steps = route?.paths?.[0]?.steps;
    if (!steps || steps.length === 0) return [];

    const pts = [];
    steps.forEach(step => {
        const pl = step.polyline || "";
        pl.split(";").forEach(pt => {
            const [lng, lat] = pt.split(",");
            if (lng && lat) pts.push([Number(lng), Number(lat)]);
        });
    });

    return pts; // ✅ 一定是数组
}



// ----------------------------------------------------------
// 小车对象创建与循环行驶
// ----------------------------------------------------------
function createCar() {

    const id = carIdCounter++;

    // 随机起点
    let start = poiList[Math.floor(Math.random() * poiList.length)];

    // 初始状态栏
    const item = document.createElement("div");
    item.className = "car-item";
    item.id = `car-${id}`;
    item.innerHTML = `
        🚗 小车 #${id}
        <button class="delete-btn" onclick="deleteCar(${id})">删除</button>
        <div id="status-${id}">初始化中...</div>
    `;
    document.getElementById("car-list").appendChild(item);
    let color="#ffffff";
    // console.log(color.length)
    while(color==="#ffffff"||color.length!==7){color=randomColor()}
    // ★★★ 修复 position 必须使用坐标数组 ★★★
    const marker = new AMap.Marker({
        position: start.location.split(",").map(Number),
        anchor: 'center',
        content: `
        <div style="
            width:24px;
            height:24px;
            border-radius:50%;
            background:${color};
            display:flex;
            justify-content:center;
            align-items:center;
            color:#fff;
            font-size:12px;
            font-weight:bold;
        ">
            ${id}
        </div>
    `,
        map: map
    });

    const car = {
        id,
        marker,
        currentPOI: start,
        running: true
    };
    carList.push(car);

    runLoop(car);
}

// 无限循环任务（异步）

async function runLoop(car) {

    while (car.running) {

        // 从上一轮终点开始
        const start = car.currentPOI;
        // console.log(start);
        // 按类型选择下一目的地
        const next = chooseNextPOI(start);
        // console.log(next);
        if (!next) continue;

        // 更新“行驶中”状态
        updateStatus(car.id, getStatusByType(next.type, false));
        const goStatus = getStatusByType(next.type, false);
        updateStatus(car.id, goStatus);
        await saveCarStateToDB(car.id, goStatus, start, next);
        //获取路线
        const path = await getRoute(start.location, next.location);

        // 路径为空重新选点
        if (!path || path.lendgth < 2) continue;

        //按路径行驶
        await moveAlong(car, path);

        // 到达时更新状态为“休息中 / 维修中”
        updateStatus(car.id, getStatusByType(next.type, true));
        const stopStatus = getStatusByType(next.type, true);
        updateStatus(car.id, stopStatus);
        await saveCarStateToDB(car.id, stopStatus, start, next);
        const waitTime = next.type === "030100" ? 5000 : 1000;

        car.currentPOI = next;
    }
}


function moveAlong(car, originalPath) {

    return new Promise(resolve => {

        // 深度复制 path（避免多车共享）
        const path = originalPath.map(p => [p[0], p[1]]);

        if (path.length < 2) return resolve();

        // 轨迹颜色（不能用白色）
        let color = "#ffffff";
        while (color === "#ffffff"||color.length!==7) color = randomColor();
        // console.log(color)
        // 删除旧轨迹
        if (car.polyline) {
            map.remove(car.polyline);
        }

        // ★ 一次性绘制轨迹（不会随小车更新）
        const polyline = new AMap.Polyline({
            path: path,
            strokeColor: color,
            strokeWeight: 6,
            strokeOpacity: 0.9
        });

        car.polyline = polyline;
        map.add(polyline);

        // ★ 移动逻辑（仿 runCar.js）
        let idx = 0;
        let step = speedFactor / 150; // speed 越大越快

        const timer = setInterval(() => {

            if (!car.running) {
                clearInterval(timer);
                return resolve();
            }

            if (idx >= path.length - 1) {
                car.marker.setPosition(path[path.length - 1]);
                clearInterval(timer);
                return resolve();
            }

            const p1 = path[Math.floor(idx)];
            const p2 = path[Math.floor(idx) + 1];

            car.marker.setPosition(p1);

            idx += step;

        }, 20);

        car.timer = timer;
    });
}



// ----------------------------------------------------------
function sleep(ms) {
    return new Promise(res => setTimeout(res, ms));
}

function randomColor() {
    return "#" + Math.floor(Math.random()*16777215).toString(16);
}

function updateStatus(id, text) {
    const el = document.getElementById(`status-${id}`);
    if (el) el.innerHTML = text;
}
function saveCarStateToDB(carId, status, startPOI, destPOI) {
    return axios.post("http://localhost:9090/carState", {
        carId,
        status,
        startId: startPOI?.id ?? "",
        startName: startPOI?.name ?? "",
        startLocation: startPOI?.location ?? "",
        destId: destPOI?.id ?? "",
        destName: destPOI?.name ?? "",
        destLocation: destPOI?.location ?? ""
    }).catch(err => console.error("保存车辆状态失败:", err));
}

function clearCarStateTableOnExit() {
    const url = "http://localhost:9090/carState/clear";

    // 最推荐：关闭页面时更可靠
    if (navigator.sendBeacon) {
        const blob = new Blob([], { type: "text/plain" });
        navigator.sendBeacon(url, blob);
        return;
    }

    // 兜底：部分浏览器支持 keepalive
    fetch(url, {
        method: "POST",
        mode: "cors",
        keepalive: true
    }).catch(() => {});
}

// 页面被关闭、刷新、或切换到别的页面时触发（比 unload 更可靠）
window.addEventListener("pagehide", clearCarStateTableOnExit);
// 兜底
window.addEventListener("beforeunload", clearCarStateTableOnExit);


function deleteCar(id) {
    const idx = carList.findIndex(c => c.id === id);
    if (idx === -1) return;

    const car = carList[idx];

    // 停止运行
    car.running = false;

    // 删除小车 marker
    map.remove(car.marker);

    // ★ 删除路线轨迹（重要！）
    if (car.polyline) {
        map.remove(car.polyline);
    }

    // 从列表移除小车
    carList.splice(idx, 1);

    // 删除 UI 行
    const el = document.getElementById(`car-${id}`);
    if (el) el.remove();
    axios.delete(`http://localhost:9090/carState/${id}`).catch(()=>{});

}



// ----------------------------------------------------------
// 绑定 UI 控件
// ----------------------------------------------------------
document.getElementById("add-car-btn").onclick = createCar;

document.getElementById("speed-range").oninput = function() {
    speedFactor = Number(this.value); // 越大越快
    document.getElementById("speed-value").textContent = speedFactor;
};
