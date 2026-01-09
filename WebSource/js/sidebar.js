(() => {
    // 判断页面是否在 /html/ 目录下：用于自动生成正确的相对链接
    const inHtmlDir = location.pathname.includes("/html/");
    const toHtml = inHtmlDir ? "" : "html/";   // 从根页面跳去 html 子目录
    const toRoot = inHtmlDir ? "../" : "";    // 从 html 子目录返回根目录

    const pages = [
        { name: "首页", href: `${toRoot}index.html`, file: "index.html" },
        { name: "地图标记点展示", href: `${toHtml}showMap.html`, file: "showMap.html" },
        { name: "路径规划", href: `${toHtml}route.html`, file: "route.html" },
        { name: "小车模拟", href: `${toHtml}runCar.html`, file: "runCar.html" },
        { name: "循环物流模拟", href: `${toHtml}cycle.html`, file: "cycle.html" },
    ];

    // 当前文件名（处理 / 或空的情况）
    let current = location.pathname.split("/").pop();
    if (!current || current === "") current = "index.html";

    // ---- DOM: toggle button ----
    const btn = document.createElement("button");
    btn.className = "sidebar-toggle";
    btn.type = "button";
    btn.setAttribute("aria-label", "打开菜单");
    btn.innerHTML = "☰";

    // ---- DOM: sidebar ----
    const sidebar = document.createElement("aside");
    sidebar.className = "sidebar";
    sidebar.setAttribute("role", "navigation");
    sidebar.setAttribute("aria-label", "页面导航");

    const header = document.createElement("div");
    header.className = "sidebar-header";
    header.innerHTML = `
    <div class="sidebar-title">页面导航</div>
    <button class="sidebar-close" type="button" aria-label="关闭菜单">×</button>
  `;

    const nav = document.createElement("nav");
    nav.className = "sidebar-nav";

    pages.forEach(p => {
        const a = document.createElement("a");
        a.href = p.href;
        a.textContent = p.name;
        a.dataset.file = p.file;

        if (p.file === current) {
            a.classList.add("active");
            a.setAttribute("aria-current", "page");
        }
        nav.appendChild(a);
    });

    sidebar.appendChild(header);
    sidebar.appendChild(nav);

    // ---- overlay ----
    const overlay = document.createElement("div");
    overlay.className = "sidebar-overlay";

    // ---- mount ----
    document.body.prepend(overlay);
    document.body.prepend(sidebar);
    document.body.prepend(btn);

    // ---- behaviors ----
    const open = () => {
        sidebar.classList.add("open");
        btn.setAttribute("aria-label", "关闭菜单");
    };
    const close = () => {
        sidebar.classList.remove("open");
        btn.setAttribute("aria-label", "打开菜单");
    };
    const toggle = () => {
        sidebar.classList.contains("open") ? close() : open();
    };

    btn.addEventListener("click", toggle);
    overlay.addEventListener("click", close);

    const closeBtn = header.querySelector(".sidebar-close");
    closeBtn.addEventListener("click", close);

    // 点击侧栏链接后自动收起（体验更好）
    nav.addEventListener("click", (e) => {
        const a = e.target.closest("a");
        if (a) close();
    });

    // Esc 关闭
    window.addEventListener("keydown", (e) => {
        if (e.key === "Escape") close();
    });
})();
