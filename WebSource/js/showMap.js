/******************************************************
 *   MassMarks + 专业POI图标（最终修复版）
 ******************************************************/

/********** 0. 专业 POI 图标映射 **********/
const ICON_MAP = {
    "010100": "./icons/A/010100.svg",
    "030100": "./icons/A/030100.svg",
    "170205": "./icons/A/170205.svg",
    "170209": "./icons/A/170209.svg",
    "170300": "./icons/A/170300.svg",

    // 农场类型公用同一个图标
    "170402": "./icons/A/170402.svg",
    "170404": "./icons/A/170402.svg",
};

const ICON_MAP2 = {
    "010100": "加油站",
    "030100": "维修站",
    "170205": "冶金化工",
    "170209": "矿业公司",
    "170300": "工厂",

    // 农场类型公用同一个图标
    "170402": "原材料厂",
    "170404": "原材料厂",
};

// 默认图标
const DEFAULT_ICON = "./icons/A/010100.svg";

/******************************************************
 *   MassMarks + POI type 分图标渲染
 ******************************************************/

let poiList = [];
let massMarks = null;

// 两个 type 同类
const SAME_GROUP_TYPES = ["170402", "170404"];

// type → styleIndex
const typeStyleMap = new Map();

// MassMarks 样式数组
const styles = [];

/********** 获取某 type 的 styleIndex **********/
function getStyleIndex(type) {
    let icon = ICON_MAP[type] || DEFAULT_ICON;

    // 170402/170404 共用
    if (SAME_GROUP_TYPES.includes(type)) {
        if (!typeStyleMap.has("GROUP_A")) {
            styles.push({
                url: icon,
                anchor: new AMap.Pixel(16, 16),
                size: new AMap.Size(32, 32)
            });
            typeStyleMap.set("GROUP_A", styles.length - 1);
        }
        return typeStyleMap.get("GROUP_A");
    }

    if (!typeStyleMap.has(type)) {
        styles.push({
            url: icon,
            anchor: new AMap.Pixel(16, 16),
            size: new AMap.Size(32, 32)
        });
        typeStyleMap.set(type, styles.length - 1);
    }

    return typeStyleMap.get(type);
}

/******************************************************
 * 初始化地图
 ******************************************************/
var map = new AMap.Map("container", {
    zoom: 4,
    center: [108.5525, 34.3227],
    resizeEnable: true
});

/******************************************************
 * 拉取 POI 数据
 ******************************************************/
axios({
    url: "http://localhost:9091/user"
}).then(res => {

    poiList = res.data.map(item => {
        const loc = item.location.split(",").map(Number);
        return {
            lnglat: loc,
            id: item.id,
            name: item.name,
            type: item.type,
            style: getStyleIndex(item.type)
        };
    });

    const overviewCheckbox = document.getElementById("overview");
    if (overviewCheckbox && overviewCheckbox.checked) {
        addMassMarkers();
    }
});

/******************************************************
 * 海量点渲染 MassMarks
 ******************************************************/
function addMassMarkers() {
    if (poiList.length === 0) return;
    removeMassMarkers();
    // 克隆数据避免 MassMarks 内部修改影响重建
    const data = poiList.map(item => ({
        lnglat: item.lnglat.slice(),
        id: item.id,
        name: item.name,
        type: item.type,
        style: item.style
    }));
    massMarks = new AMap.MassMarks(data, {
        zIndex: 200,
        style: styles,
        opacity: 1,
        cursor: "pointer"
    });
    massMarks.setMap(map);
    massMarks.on("click", e => {
        console.log("点击：", e.data);
    });
}

function removeMassMarkers() {
    if (massMarks) {
        massMarks.clear();
        massMarks.setMap(null);
        massMarks = null;
    }
}

/******************************************************
 *      自动生成右侧图例（Legend）
 ******************************************************/
function buildLegend() {
    const legend = document.getElementById("poi-legend");
    legend.innerHTML = `<h4>图例</h4>`;

    const uniqueTypes = [...new Set(Object.keys(ICON_MAP))];

    uniqueTypes.forEach(type => {
        const icon = ICON_MAP[type];
        const item = document.createElement("div");
        item.className = "legend-item";

        item.innerHTML = `
            <img src="${icon}" width="20" height="20">
            <span>${ICON_MAP2[type]}</span>
        `;
        legend.appendChild(item);
    });
}

buildLegend();

/******************************************************
 * 控件开关
 ******************************************************/
var scale = new AMap.Scale({ visible: false });
map.addControl(scale);

function toggleScale(ck) { ck.checked ? scale.show() : scale.hide(); }
function toggleOverViewShow(ck) {
    if (ck.checked) {
        addMassMarkers();
    } else {
        removeMassMarkers();
    }
}
