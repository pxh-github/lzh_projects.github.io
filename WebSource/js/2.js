const allPosition=[];
const allMarkers = [];
let massMarks = null;   // ★ 全局变量
// 两个属于同类的类型
const SAME_GROUP_TYPES = ["170402", "170404"];

// 为该组设置一个固定颜色
const SAME_GROUP_COLOR = "#FF5722";   // 橙色（你可以换）

// 自动颜色生成器（给其它 type）
function generateColor(type) {
    // 每种 type 都有独立颜色
    let hash = 0;
    for (let i = 0; i < type.length; i++) {
        hash = type.charCodeAt(i) + ((hash << 5) - hash);
    }
    // 生成 HSL 颜色，更好看
    const hue = Math.abs(hash) % 360;
    return `hsl(${hue}, 75%, 55%)`;
}

// 最终：根据 type 返回颜色
function getColorByType(type) {
    if (SAME_GROUP_TYPES.includes(type)) {
        return SAME_GROUP_COLOR;  // 这两类使用同色
    }
    return generateColor(type);   // 其他每个 type 使用独立颜色
}


var map = new AMap.Map("container", {
    zoom: 5,
    center: [108.5525, 34.3227],
    resizeEnable: true
});
axios({
    url: 'http://localhost:9091/user'
}).then((result) => {
    poiList = result.data.map(item => {
        const loc = item.location.split(",").map(Number);
        return {
            lnglat: loc,
            id: item.id,
            name: item.name
        };
    });

    // 如果默认勾选“显示图标”，则直接加载
    const overviewCheckbox = document.getElementById('overview');
    if (overviewCheckbox && overviewCheckbox.checked) {
        addMassMarkers();
    }
})

var scale = new AMap.Scale({
        visible: false
    }),
    toolBar = new AMap.ToolBar({
        visible: false,
        position: {
            top: '110px',
            right: '40px'
        }
    }),
    controlBar = new AMap.ControlBar({
        visible: false,
        position: {
            top: '10px',
            right: '10px'
        }
    }),
    overView = new AMap.HawkEye({
        visible: true
    });
map.addControl(scale);
map.addControl(toolBar);
map.addControl(controlBar);
function addMassMarkers() {

    // 若已存在，先清除
    if (massMarks) {
        massMarks.setMap(null);
        massMarks = null;
    }

    const style = [{
        url: "../png/img.png",         // ★ 使用你的图片
        anchor: new AMap.Pixel(12, 34),
        size: new AMap.Size(24, 34)    // ★ 自定义图标大小
    }];

    massMarks = new AMap.MassMarks(poiList, {
        zIndex: 120,
        style: style,
        opacity: 1,
        cursor: 'pointer'
    });

    massMarks.setMap(map);

    // 可选：点击事件
    massMarks.on("click", (e) => {
        console.log("点击点：", e.data);
    });
}


// 3. 移除海量点
function removeMassMarkers() {
    if (massMarks) {
        massMarks.setMap(null);
        massMarks = null;
    }
}
function toggleScale(checkbox) {
    checkbox.checked ? scale.show() : scale.hide();
}

function toggleToolBar(checkbox) {
    checkbox.checked ? toolBar.show() : toolBar.hide();
}

function toggleControlBar(checkbox) {
    checkbox.checked ? controlBar.show() : controlBar.hide();
}

// ★ 改成使用海量点开关
function toggleOverViewShow(checkbox) {
    checkbox.checked ? addMassMarkers() : removeMassMarkers();
}
