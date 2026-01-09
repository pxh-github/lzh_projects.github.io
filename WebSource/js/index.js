let width = document.documentElement.clientWidth//获取网页宽度
let prev = document.querySelector('.prev');//获取第一个.prev元素
let next = document.querySelector('.next');//获取第一个.next元素
let dot = document.getElementById('dots').getElementsByTagName('li');//获取#dots里的li标签元素
let dotlist = document.querySelector('#dots');//获取第一个#dots元素
function dotchange(p) {
    for (i = 0; i < dot.length; i++) {
        dot[i].style.backgroundColor = '';//通过循环清除所有dot元素的背景颜色
    }
    if (p < dot.length) {
        dot[p].style.backgroundColor = 'red';//设置当前图片对应的dot元素的背景颜色为red
    }
}//定义dotchange函数
if (width > 800) {
    var left = 0;//定义left=0(初始化)
    var timer;//定义timer计时器
    run()//使用run函数
    function run() {
        if (left <= -2400) {
            left = 0;
        }//如果轮播图播完,重置left=0,使之回到第一张图片
        var t = Math.floor(-left / 800);//t为当前图片序号
        imagelist.style.marginLeft = left + "px";//imagelist的左边距和'left'的关系,实现轮播效果
        var n = (left % 800 === 0) ? n = 2000 : n = 15;//如果n能被800整除，n=2000，反之n=15(比if简单10000倍)
        /* 原先设置n的值的方法
        if (left % 800 === 0) {
            var n = 2000;
        }
        else { var n = 15; }*/
        left -= 25;//每次判断后，left的数值-25；
        timer = setTimeout(run, n);//执行一次run函数停留n毫秒
        dotchange(t);
    }//定义run函数
    function change(x) {
        let y = -x * 800;//y=-(dot序号*800)
        imagelist.style.marginLeft = y + "px";
        left = y;//将left设置为y,使后面的轮播能够正常进行
    }//定义change函数
    var num = Math.floor(-left / 800);
    prev.onclick = function () {
        var z = Math.floor((-left / 800) - 1);//z就是当前图片的上一张图片的序号
        if (z == -1) {
            z = 2;
        }//特别的,z=-1时(即left在(-800,0]中时), 上一张图片的序号为2
        change(z);//切换到序号为z的图片
    }//如果prev被点击,执行虚拟函数:切换到上一张图片
    next.onclick = function () {
        let z = Math.floor((-left / 800) + 1);
        if (z == 4) {
            z = 0;
        }
        change(z);
    }
}
/*响应式设计,当网页宽度<=800px时*/
//其余注释同上
if (width <= 800) {
    var left = 0;
    var timer;
    run()
    function run() {
        if (left <= -1500) {
            left = 0;
        }
        var t = Math.floor(-left / 500);
        imagelist.style.marginLeft = left + "px";
        var n = (left % 800 === 0) ? n = 2000 : n = 15;
        left -= 25;
        timer = setTimeout(run, n);
        dotchange(t);
    }
    function change(x) {
        var y = -x * 500;
        imagelist.style.marginLeft = y + "px";
        left = y;
    }
    prev.onclick = function () {
        var z = Math.floor((-left / 800) - 1);
        if (z == -1) {
            z = 2;
        }
        change(z);
    }
    next.onclick = function () {
        var z = Math.floor((-left / 800) + 1);
        if (z == 4) {
            z = 0;
        }
        change(z);
    }
}