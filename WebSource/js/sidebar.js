(() => {
    if (!document.body) {
        return;
    }

    const inHtmlDir = location.pathname.includes("/html/");
    const toHtml = inHtmlDir ? "" : "html/";
    const toRoot = inHtmlDir ? "../" : "";

    const pages = [
        { name: "首页", href: `${toRoot}index.html`, file: "index.html" },
        { name: "地图标记点展示", href: `${toHtml}showMap.html`, file: "showMap.html" },
        { name: "路径规划", href: `${toHtml}route.html`, file: "route.html" },
        { name: "小车模拟", href: `${toHtml}runCar.html`, file: "runCar.html" },
        { name: "数据实验沙箱", href: `${toHtml}sandbox.html`, file: "sandbox.html" },
        { name: "中期汇报 / 图表讲解", href: `${toHtml}midterm.html`, file: "midterm.html" },
        { name: "实时监控 / 循环仿真", href: `${toHtml}cycle.html`, file: "cycle.html" },
        { name: "最大利润算法", href: `${toHtml}max-profit-demo.html`, file: "max-profit-demo.html" },
        { name: "一车多货算法", href: `${toHtml}multi-cargo-demo.html`, file: "multi-cargo-demo.html" },
        { name: "调度模式对比", href: `${toHtml}compare-modes.html`, file: "compare-modes.html" }
    ];

    let current = location.pathname.split("/").pop();
    if (!current) {
        current = "index.html";
    }

    const btn = document.createElement("button");
    btn.className = "sidebar-toggle";
    btn.type = "button";
    btn.setAttribute("aria-label", "打开菜单");
    btn.innerHTML = "☰";

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

    pages.forEach((page) => {
        const link = document.createElement("a");
        link.href = page.href;
        link.textContent = page.name;
        link.dataset.file = page.file;
        if (page.file === current) {
            link.classList.add("active");
            link.setAttribute("aria-current", "page");
        }
        nav.appendChild(link);
    });

    sidebar.appendChild(header);
    sidebar.appendChild(nav);

    const overlay = document.createElement("div");
    overlay.className = "sidebar-overlay";

    document.body.prepend(overlay);
    document.body.prepend(sidebar);
    document.body.prepend(btn);

    const open = () => {
        if (!sidebar || !btn) {
            return;
        }
        sidebar.classList.add("open");
        btn.setAttribute("aria-label", "关闭菜单");
    };

    const close = () => {
        if (!sidebar || !btn) {
            return;
        }
        sidebar.classList.remove("open");
        btn.setAttribute("aria-label", "打开菜单");
    };

    const toggle = () => {
        if (!sidebar) {
            return;
        }
        if (sidebar.classList.contains("open")) {
            close();
        } else {
            open();
        }
    };

    btn.addEventListener("click", toggle);
    overlay.addEventListener("click", close);

    const closeBtn = header.querySelector(".sidebar-close");
    if (closeBtn) {
        closeBtn.addEventListener("click", close);
    }

    nav.addEventListener("click", (event) => {
        const link = event.target.closest("a");
        if (link) {
            close();
        }
    });

    window.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            close();
        }
    });
})();
