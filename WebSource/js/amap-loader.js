(function () {
    'use strict';
    const config = window.AMAP_CONFIG || {};
    if (!config.key || config.key === 'YOUR_AMAP_BROWSER_KEY') {
        console.error('请先将 amap-config.example.js 复制为 amap-config.local.js 并配置高德浏览器 Key。');
        return;
    }
    window._AMapSecurityConfig = { securityJsCode: config.securityJsCode || '' };
    // Parser-blocking loading preserves the existing pages' AMap initialization order.
    const source = 'https://webapi.amap.com/maps?v=2.0&plugin=AMap.Scale&key=' + encodeURIComponent(config.key);
    document.write('<script src="' + source + '"><\/script>');
}());
