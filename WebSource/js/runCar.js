/****************************************************
 *   高德小车随机路径模拟（多车、多路径、可删除）
 ****************************************************/
let multiCarCount = 100;

// -------------------- 地图初始化 --------------------
let map = new AMap.Map("map", {
    zoom: 5,
    center: [108.5525, 34.3227],
    resizeEnable: true
});

// -------------------- 全局变量 --------------------
let poiList = [];             // 后端返回的 POI 数组
let nextCarSpeed = 100;       // 下辆车速度（由滑动条调整）
let cars = [];                // 所有小车对象
let polylines = [];           // 所有路径对象
let carIdCounter = 1;         // 小车编号自增

// Web Service requests use the backend proxy.

// -------------------- 1. 拉取 POI 数据 --------------------
axios.get("http://localhost:9091/user").then(res => {
    poiList = res.data;
    console.log("POI 数据：", poiList);
});


// -------------------- 工具函数 --------------------
function randomColor() {
    return '#' + Math.floor(Math.random()*16777215).toString(16);
}

function randomTwoPOI() {
    const n = poiList.length;
    if (n < 2) return null;

    const i = Math.floor(Math.random() * n);
    let j = Math.floor(Math.random() * n);
    while (j === i) j = Math.floor(Math.random() * n);

    return [poiList[i], poiList[j]];
}

// 计算小车旋转角度
function getRotationAngle(p1, p2) {
    const dx = p2[0] - p1[0];
    const dy = p2[1] - p1[1];
    return (Math.atan2(dy, dx) * 180 / Math.PI);
}


// -------------------- 2. 调用路径规划 API --------------------
async function getRoutePoints(originLoc, destLoc) {
    const url =
        `/amap/driving?origin=${encodeURIComponent(originLoc)}&destination=${encodeURIComponent(destLoc)}`;

    const response = await axios.get(url);
    const res = { data: JSON.parse(response.data.raw) };
    const route = res.data.route;

    if (!route || !route.paths) return [];

    const steps = route.paths[0].steps;
    let path = [];

    steps.forEach(step => {
        if (step.tmcs) {
            step.tmcs.forEach(tmc => {
                if (tmc.polyline) {
                    tmc.polyline.split(";").forEach(pt => {
                        const arr = pt.split(",");
                        if (arr.length === 2) {
                            const lng = Number(arr[0]);
                            const lat = Number(arr[1]);
                            if (!isNaN(lng) && !isNaN(lat)) {
                                path.push([lng, lat]);
                            }
                        }
                    });
                }
            });
        } else if (step.polyline) {
            step.polyline.split(";").forEach(pt => {
                const arr = pt.split(",");
                if (arr.length === 2) {
                    const lng = Number(arr[0]);
                    const lat = Number(arr[1]);
                    if (!isNaN(lng) && !isNaN(lat)) {
                        path.push([lng, lat]);
                    }
                }
            });
        }
    });

    return path;
}


// -------------------- 3. 绘制随机颜色路径 --------------------
function drawRoute(path) {

    var color="#ffffff";
    while(color==="#ffffff"){
        color=randomColor();
    }
    const poly = new AMap.Polyline({
        path: path,
        strokeColor: color,
        strokeWeight: 6,
        strokeOpacity: 0.9
    });

    map.add(poly);
    polylines.push(poly);

    return poly;
}


function updateCarListUI() {
    const list = document.getElementById("car-list");
    list.innerHTML = "";

    if (cars.length === 0) {
        // ★ 显示列表为空提示
        const empty = document.createElement("div");
        empty.className = "empty";
        empty.textContent = "暂无小车";
        list.appendChild(empty);
        return;
    }

    cars.forEach(car => {
        const item = document.createElement("div");
        item.className = "car-item";

        item.innerHTML = `
            <span class="car-label">小车 #${car.id}</span>
            <button class="car-delete-btn" onclick="removeCar(${car.id})">删除</button>
        `;

        list.appendChild(item);
    });
}


/**********************
 *   创建小车
 **********************/
function createCar(path, color, speed) {
    if (path.length === 0) return;

    const id = carIdCounter++;

    // ★ 为该小车创建路径 polyline
    const polyline = drawRoute(path);

    const marker = new AMap.Marker({
        position: path[0],
        map: map,
        offset: new AMap.Pixel(-10, -10),
        content: `<div style="width:20px;height:20px;background:${color};border-radius:50%;"></div>`,
        angle: 0
    });

    let idx = 0;

    let step = speed / 150; // 自定义比例，speed 越大，step 越大

    const timer = setInterval(() => {
        // 如果超出路径长度，删除
        if (idx >= path.length - 1) {
            clearInterval(timer);
            removeCar(id);
            return;
        }

        const now = path[Math.floor(idx)];
        const next = path[Math.floor(idx) + 1];

        marker.setPosition(now);

        marker.setAngle(getRotationAngle(now, next));

        idx += step;  // ★ 改成"步长"移动
    }, 20);  // 固定25 FPS 左右


    // ★ 记录小车与路径
    cars.push({
        id,
        marker,
        timer,
        polyline
    });

    updateCarListUI();
    return id;
}


function removeCar(carId) {
    const idx = cars.findIndex(c => c.id === carId);
    if (idx === -1) return false;

    const car = cars[idx];

    // 删除定时器
    clearInterval(car.timer);

    // 删除 marker
    map.remove(car.marker);

    // ★ 删除对应路径 polyline
    if (car.polyline) {
        map.remove(car.polyline);
    }

    cars.splice(idx, 1);

    updateCarListUI();
    return true;
}

/**********************
 *   清空所有小车与路径
 **********************/
function clearAll() {
    cars.forEach(c => {
        clearInterval(c.timer);
        map.remove(c.marker);
    });
    cars = [];

    polylines.forEach(p => map.remove(p));
    polylines = [];

    updateCarListUI();  // ★ 清空 UI
}

/**********************
 *   清空按钮事件
 **********************/
document.getElementById("clear-all-btn").onclick = clearAll;



// -------------------- 7. 添加随机小车按钮 --------------------
document.getElementById("add-car-btn").onclick = async () => {
    const two = randomTwoPOI();
    if (!two) return alert("POI 数量不足");

    const [start, end] = two;

    const path = await getRoutePoints(start.location, end.location);
    if (path.length === 0) return alert("无法获取路线");

    const color = randomColor();
    createCar(path, color, nextCarSpeed);
};


// -------------------- 8. 速度滑动条 --------------------
const speedRange = document.getElementById("speed-range");
const speedValue = document.getElementById("speed-value");

speedRange.oninput = () => {
    nextCarSpeed = Number(speedRange.value);
    speedValue.textContent = nextCarSpeed;
};
const multiRange = document.getElementById("multi-count-range");
const multiValue = document.getElementById("multi-count-value");

multiRange.oninput = () => {
    multiCarCount = Number(multiRange.value);
    multiValue.textContent = multiCarCount;
};

document.getElementById("add-multi-car-btn").onclick = async () => {
    if (poiList.length < 2) {
        alert("POI 数据不足");
        return;
    }

    let added = 0;

    const interval = setInterval(async () => {
        if (added >= multiCarCount) {
            clearInterval(interval);
            console.log(`批量添加完成，共 ${multiCarCount} 辆`);
            return;
        }

        const two = randomTwoPOI();
        if (!two) return;

        const [start, end] = two;

        const path = await getRoutePoints(start.location, end.location);
        if (path.length === 0) return;

        const color = randomColor();
        createCar(path, color, nextCarSpeed);

        added++;

    }, 5);  // ★ 控制每辆生成速度，防止主线程卡死
};



// -------------------- 9. 暴露删除与清空方法给全局 --------------------
window.removeCar = removeCar;
window.clearAll = clearAll;

