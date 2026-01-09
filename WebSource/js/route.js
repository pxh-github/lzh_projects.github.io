/************** 全局变量 **************/
let map = new AMap.Map("map", {
    zoom: 11,
    center: [116.397026, 39.918058]
});
let pxh=1;
let poiData =[];
let selectedPOIs = [];

let routeLine = null;
let carMarker = null;
let pathPoints = [];
let animIndex = 0;
let animTimer = null;

const AMapKey = "1d830d8718ed7ad1452d6bbdfe3f447a"; // ← 记得换成 Web 服务 Key


document.getElementById("btn-load-poi").onclick = () => {
    axios.get("http://localhost:9090/user").then(res => {
        if(pxh===1){
            poiData.push(res.data[0]);
            poiData.push(res.data[5]);
            poiData.push(res.data[10]);
            pxh=0;
        }
        console.log(poiData);
        showPOIList();
    });
};

function showPOIList() {
    let listDiv = document.getElementById("poi-list");
    listDiv.innerHTML = "";

    poiData.forEach(poi => {
        const item = document.createElement("div");
        item.innerHTML = `
            <label>
                <input type="checkbox" value="${poi.id}">
                ${poi.name}
            </label>
        `;
        listDiv.appendChild(item);
    });
}


/************** 2. 驾车路径规划（使用坐标） **************/
document.getElementById("btn-plan").onclick = async () => {
    selectedPOIs = [...document.querySelectorAll("#poi-list input:checked")]
        .map(c => c.value);

    if (selectedPOIs.length !== 2) {
        alert("请选择两个 POI（起点+终点）");
        return;
    }

    const poi1 = poiData.find(p => p.id === selectedPOIs[0]);
    const poi2 = poiData.find(p => p.id === selectedPOIs[1]);

    const origin = poi1.location;
    const destination = poi2.location;
    const origin_id=poi1.id;
    const destination_id=poi2.id
    // 驾车路线，必须一行
    const url =`https://restapi.amap.com/v5/direction/driving?origin=${origin}&destination=${destination}&origin_id=${origin_id}&destination=${destination_id}&show_fields=polyline&extensions=all&key=${AMapKey}`;

    console.log("请求：", url);

    const res = await axios.get(url);
    console.log("返回：", res.data);
    console.log(JSON.stringify(res.data.route.paths[0], null, 2));



    if (!res.data.route || !res.data.route.paths) {
        alert("无法获取路线，请检查经纬度或 key");
        return;
    }

    const steps = res.data.route.paths[0].steps;

    pathPoints = [];

    // 🚗 正确解析 v5 driving polyline（重点修复）
    pathPoints = [];

    steps.forEach(step => {

        // 情况 1：step.tmcs 存在 (v5 驾车多数情况)
        if (step.tmcs) {
            step.tmcs.forEach(tmc => {
                if (tmc.polyline) {
                    tmc.polyline.split(";").forEach(pt => {
                        const arr = pt.split(",");
                        if (arr.length === 2) {
                            const lng = Number(arr[0]);
                            const lat = Number(arr[1]);
                            if (!isNaN(lng) && !isNaN(lat)) {
                                pathPoints.push([lng, lat]);
                            }
                        }
                    });
                }
            });
        }

        // 情况 2：step.polyline 存在（个别步骤会出现）
        else if (step.polyline) {
            step.polyline.split(";").forEach(pt => {
                const arr = pt.split(",");
                if (arr.length === 2) {
                    const lng = Number(arr[0]);
                    const lat = Number(arr[1]);
                    if (!isNaN(lng) && !isNaN(lat)) {
                        pathPoints.push([lng, lat]);
                    }
                }
            });
        }

        // 情况 3：某些线路有 segments (如高速路段)
        else if (step.segments) {
            step.segments.forEach(seg => {
                if (seg.polyline) {
                    seg.polyline.split(";").forEach(pt => {
                        const arr = pt.split(",");
                        if (arr.length === 2) {
                            const lng = Number(arr[0]);
                            const lat = Number(arr[1]);
                            if (!isNaN(lng) && !isNaN(lat)) {
                                pathPoints.push([lng, lat]);
                            }
                        }
                    });
                }
            });
        }

    });

    console.log("最终 pathPoints =", pathPoints);

    drawRoute();
};


/************** 3. 绘制路线 **************/
function drawRoute() {
    if (routeLine) map.remove(routeLine);

    routeLine = new AMap.Polyline({
        path: pathPoints,
        strokeColor: "#3366FF",
        strokeWeight: 6
    });
    map.add(routeLine);
    map.setFitView([routeLine]);

    if (carMarker) map.remove(carMarker);

    carMarker = new AMap.Marker({
        position: pathPoints[0],
        icon: "https://webapi.amap.com/images/car.png",
        offset: new AMap.Pixel(-13, -26)
    });

    map.add(carMarker);
    animIndex = 0;
}


/************** 4. 小车动画 **************/
document.getElementById("btn-start").onclick = () => {
    if (pathPoints.length === 0) {
        alert("请先规划路径！");
        return;
    }
    animIndex = 0;
    startAnim();
};

document.getElementById("btn-pause").onclick = () => clearInterval(animTimer);
document.getElementById("btn-resume").onclick = () => startAnim();
document.getElementById("btn-stop").onclick = () => {
    clearInterval(animTimer);
    if (carMarker) carMarker.setPosition(pathPoints[0]);
};

function startAnim() {
    clearInterval(animTimer);

    animTimer = setInterval(() => {
        if (animIndex >= pathPoints.length) {
            clearInterval(animTimer);
            return;
        }

        carMarker.setPosition(pathPoints[animIndex]);
        animIndex++;

    }, 100);
}
