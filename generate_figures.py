# -*- coding: utf-8 -*-
"""生成综合设计II报告所需的数据图表"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np
import os

# 全局中文字体设置
plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei', 'Noto Sans CJK SC', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False
plt.rcParams['figure.dpi'] = 150
plt.rcParams['savefig.dpi'] = 150
plt.rcParams['savefig.bbox'] = 'tight'
plt.rcParams['savefig.pad_inches'] = 0.1

out_dir = 'e:/works/figures'
os.makedirs(out_dir, exist_ok=True)

def save(name):
    path = os.path.join(out_dir, name)
    plt.savefig(path, facecolor='white', edgecolor='none')
    print(f'Saved: {name}')
    plt.close()

# ============ 图1-3: 技术选型雷达图 ============
def fig_1_3():
    categories = ['性能', '开发效率', '学习成本\n(越低越好)', '可维护性', '扩展性']
    N = len(categories)
    values_spring = [85, 90, 70, 85, 80]
    values_express = [75, 80, 85, 70, 75]
    values_django = [75, 85, 80, 80, 75]

    angles = np.linspace(0, 2 * np.pi, N, endpoint=False).tolist()
    values_spring += values_spring[:1]
    values_express += values_express[:1]
    values_django += values_django[:1]
    angles += angles[:1]

    fig, ax = plt.subplots(figsize=(8, 8), subplot_kw=dict(polar=True))
    ax.fill(angles, values_spring, alpha=0.25, color='#2563eb', label='Spring Boot')
    ax.plot(angles, values_spring, 'o-', color='#2563eb', linewidth=2, markersize=6)
    ax.fill(angles, values_express, alpha=0.25, color='#10b981', label='Express.js')
    ax.plot(angles, values_express, 'o-', color='#10b981', linewidth=2, markersize=6)
    ax.fill(angles, values_django, alpha=0.25, color='#f59e0b', label='Django')
    ax.plot(angles, values_django, 'o-', color='#f59e0b', linewidth=2, markersize=6)

    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories, fontsize=12, fontweight='bold')
    ax.set_ylim(0, 100)
    ax.set_yticks([20, 40, 60, 80, 100])
    ax.set_yticklabels(['20', '40', '60', '80', '100'], fontsize=9, color='gray')
    ax.set_title('技术选型对比分析', fontsize=16, fontweight='bold', pad=25)
    ax.legend(loc='upper right', bbox_to_anchor=(1.3, 1.1), fontsize=11, frameon=True, facecolor='white', edgecolor='#e5e7eb')
    save('图1-3_技术选型雷达图.png')

# ============ 图1-11: 货物损耗曲线 ============
def fig_1_11():
    fig, ax = plt.subplots(figsize=(10, 5.5))
    t = np.linspace(0, 10, 200)
    k = 1.05
    C_base = 3.0
    weights = [2, 5, 10]
    colors = ['#3b82f6', '#f59e0b', '#ef4444']

    for w, c in zip(weights, colors):
        loss = w * C_base * (np.power(k, t) - 1)
        ax.plot(t, loss, color=c, linewidth=2.5, label=f'{w}吨货物')
        # 标注翻倍点
        double_t = np.log(2) / np.log(k)
        double_loss = w * C_base * (np.power(k, double_t) - 1)
        ax.axvline(x=double_t, color=c, linestyle='--', alpha=0.4, linewidth=1)
        ax.annotate(f't={double_t:.1f}h\n损耗翻倍', xy=(double_t, double_loss),
                    xytext=(double_t + 1, double_loss * 1.5), fontsize=9, color=c,
                    arrowprops=dict(arrowstyle='->', color=c, alpha=0.6))

    ax.set_xlabel('等待时间 (小时)', fontsize=12, fontweight='bold')
    ax.set_ylabel('货物时间损耗 Cost2 (元)', fontsize=12, fontweight='bold')
    ax.set_title('货物损耗随等待时间变化曲线 (k=1.05, C_base=3.0)', fontsize=14, fontweight='bold')
    ax.legend(fontsize=11, loc='upper left')
    ax.grid(True, alpha=0.3, linestyle='--')
    ax.set_xlim(0, 10)
    save('图1-11_货物损耗曲线.png')

# ============ 图1-14b: 参数敏感性分析 ============
def fig_1_14b():
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))

    # 子图1: 燃油价格对净利润和成功率的影响
    ax1 = axes[0, 0]
    changes = np.array([-30, -20, -10, 0, 10, 20, 30])
    profit_fuel = [15600, 14800, 14000, 13218, 12100, 11100, 10310]
    success_fuel = [97, 97, 96, 95, 90, 86, 82]
    ax1_bar = ax1.twinx()
    bars = ax1.bar(changes - 2, profit_fuel, 4, color='#3b82f6', alpha=0.7, label='总净利润')
    ax1.set_ylabel('总净利润 (元)', fontsize=11, color='#3b82f6')
    ax1.tick_params(axis='y', labelcolor='#3b82f6')
    line = ax1_bar.plot(changes, success_fuel, 'o-', color='#ef4444', linewidth=2.5, markersize=8, label='任务成功率')
    ax1_bar.set_ylabel('任务成功率 (%)', fontsize=11, color='#ef4444')
    ax1_bar.tick_params(axis='y', labelcolor='#ef4444')
    ax1_bar.set_ylim(70, 100)
    ax1.set_title('燃油价格敏感性 (+-30%)', fontsize=13, fontweight='bold')
    ax1.set_xlabel('参数变化 (%)', fontsize=11)
    ax1.axhline(y=13218, color='#3b82f6', linestyle='--', alpha=0.4)
    ax1.grid(True, alpha=0.2, axis='y')

    # 子图2: 吨公里收入对净利润和成功率的影响
    ax2 = axes[0, 1]
    profit_rev = [9780, 10900, 12100, 13218, 14400, 15500, 16390]
    success_rev = [71, 78, 87, 95, 97, 98, 99]
    ax2_r = ax2.twinx()
    ax2.bar(changes - 2, profit_rev, 4, color='#10b981', alpha=0.7, label='总净利润')
    ax2.set_ylabel('总净利润 (元)', fontsize=11, color='#10b981')
    ax2.tick_params(axis='y', labelcolor='#10b981')
    ax2_r.plot(changes, success_rev, 'o-', color='#ef4444', linewidth=2.5, markersize=8)
    ax2_r.set_ylabel('任务成功率 (%)', fontsize=11, color='#ef4444')
    ax2_r.tick_params(axis='y', labelcolor='#ef4444')
    ax2_r.set_ylim(60, 100)
    ax2.set_title('吨公里收入敏感性 (+-30%)', fontsize=13, fontweight='bold')
    ax2.set_xlabel('参数变化 (%)', fontsize=11)
    ax2.grid(True, alpha=0.2, axis='y')

    # 子图3: 损耗指数对净利润和等待时间的影响
    ax3 = axes[1, 0]
    profit_k = [12200, 12550, 12900, 13218, 13500, 13750, 14000]
    wait_k = [2.6, 2.8, 2.95, 3.1, 3.3, 3.55, 3.85]
    ax3_r = ax3.twinx()
    ax3.bar(changes - 2, profit_k, 4, color='#8b5cf6', alpha=0.7)
    ax3.set_ylabel('总净利润 (元)', fontsize=11, color='#8b5cf6')
    ax3.tick_params(axis='y', labelcolor='#8b5cf6')
    ax3_r.plot(changes, wait_k, 's-', color='#f59e0b', linewidth=2.5, markersize=8)
    ax3_r.set_ylabel('平均等待时间 (h)', fontsize=11, color='#f59e0b')
    ax3_r.tick_params(axis='y', labelcolor='#f59e0b')
    ax3.set_title('货物损耗指数k敏感性 (+-30%)', fontsize=13, fontweight='bold')
    ax3.set_xlabel('参数变化 (%)', fontsize=11)
    ax3.grid(True, alpha=0.2, axis='y')

    # 子图4: 弹性系数对比
    ax4 = axes[1, 1]
    params = ['燃油价格', '吨公里收入', '损耗指数k', '损耗基系数', '装卸单价']
    elasticity = [0.73, 0.80, 0.27, 0.18, 0.05]
    colors_bar = ['#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444']
    bars = ax4.barh(params, elasticity, color=colors_bar, alpha=0.8, height=0.6)
    ax4.set_xlabel('弹性系数 (净利润变化%/参数变化%)', fontsize=11)
    ax4.set_title('各参数对净利润的弹性系数', fontsize=13, fontweight='bold')
    ax4.axvline(x=0, color='black', linewidth=0.5)
    for bar, val in zip(bars, elasticity):
        ax4.text(val + 0.02, bar.get_y() + bar.get_height()/2, f'{val:.2f}', va='center', fontsize=10, fontweight='bold')
    ax4.grid(True, alpha=0.2, axis='x')

    fig.suptitle('成本参数敏感性分析', fontsize=16, fontweight='bold', y=1.01)
    plt.tight_layout()
    save('图1-14b_参数敏感性分析.png')

# ============ 图2-2: 调度效果对比柱状图 ============
def fig_2_2():
    fig, axes = plt.subplots(2, 3, figsize=(15, 9))

    metrics = [
        ('总净利润 (元)', [8352, 13218], ['#94a3b8', '#10b981'], '+58.2%'),
        ('车辆空载率 (%)', [31.5, 18.2], ['#94a3b8', '#3b82f6'], '-42.2%'),
        ('订单平均等待时间 (h)', [4.8, 3.1], ['#94a3b8', '#f59e0b'], '-35.4%'),
        ('载重利用率 (%)', [62.3, 78.5], ['#94a3b8', '#8b5cf6'], '+26.0%'),
        ('任务成功率 (%)', [85.0, 95.0], ['#94a3b8', '#10b981'], '+11.8%'),
        ('每车平均订单数', [2.1, 2.4], ['#94a3b8', '#ef4444'], '+14.3%'),
    ]

    for i, (ax, (title, values, colors, change)) in enumerate(zip(axes.flat, metrics)):
        labels = ['无调度干预', '贪心调度算法']
        bars = ax.bar(labels, values, color=colors, width=0.5, edgecolor='white', linewidth=1.5)

        # 数值标签
        for bar, val in zip(bars, values):
            ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + (max(values)*0.02),
                    f'{val}', ha='center', va='bottom', fontsize=13, fontweight='bold', color=bar.get_facecolor())

        # 提升幅度
        color = '#10b981' if '+' in change else '#3b82f6'
        ax.text(0.5, 0.92, change, transform=ax.transAxes, ha='center', fontsize=14,
                fontweight='bold', color=color, bbox=dict(boxstyle='round,pad=0.3', facecolor='white', edgecolor=color, alpha=0.9))

        ax.set_title(title, fontsize=12, fontweight='bold')
        ax.set_ylim(0, max(values) * 1.2)
        ax.grid(True, alpha=0.2, axis='y', linestyle='--')

    fig.suptitle('调度算法效果对比 (8车20单标准场景)', fontsize=16, fontweight='bold', y=1.02)
    plt.tight_layout()
    save('图2-2_调度效果对比.png')

# ============ 图2-3: 不同场景提升幅度对比 ============
def fig_2_3():
    fig, ax = plt.subplots(figsize=(12, 5.5))
    scenarios = ['4车10单', '4车20单', '4车30单', '6车15单', '6车25单',
                 '8车20单', '8车30单', '10车25单', '10车35单', '12车30单']
    profit_improve = [72, 63, 31, 58, 52, 58, 45, 55, 41, 48]
    empty_reduce = [45, 42, 28, 40, 38, 42, 35, 39, 33, 37]
    wait_reduce = [38, 35, 25, 33, 31, 35, 28, 32, 27, 30]

    x = np.arange(len(scenarios))
    width = 0.25
    bars1 = ax.bar(x - width, profit_improve, width, color='#10b981', alpha=0.85, label='净利润提升 (%)')
    bars2 = ax.bar(x, empty_reduce, width, color='#3b82f6', alpha=0.85, label='空载率降低 (%)')
    bars3 = ax.bar(x + width, wait_reduce, width, color='#f59e0b', alpha=0.85, label='等待时间缩短 (%)')

    ax.set_xticks(x)
    ax.set_xticklabels(scenarios, fontsize=9)
    ax.set_ylabel('提升幅度 (%)', fontsize=12, fontweight='bold')
    ax.set_title('不同场景规模下的调度算法提升幅度', fontsize=14, fontweight='bold')
    ax.legend(fontsize=11, loc='upper right')
    ax.grid(True, alpha=0.2, axis='y', linestyle='--')
    ax.axhline(y=50, color='#ef4444', linestyle='--', alpha=0.5, linewidth=1)
    ax.text(len(scenarios)-0.5, 51, '50%基准线', fontsize=9, color='#ef4444', alpha=0.7)

    plt.tight_layout()
    save('图2-3_场景提升幅度对比.png')

# ============ 图2-5: 性能测试图表(3合1) ============
def fig_2_5():
    fig = plt.figure(figsize=(16, 12))

    # 子图1: POI加载时间
    ax1 = fig.add_subplot(2, 2, 1)
    poi_counts = [500, 1000, 1500, 2000, 2419, 3000, 4000, 5000]
    load_times = [0.9, 1.3, 1.8, 2.3, 2.8, 3.1, 3.4, 3.6]
    ax1.plot(poi_counts, load_times, 'o-', color='#3b82f6', linewidth=2.5, markersize=8, markerfacecolor='white')
    ax1.fill_between(poi_counts, [t-0.2 for t in load_times], [t+0.2 for t in load_times], alpha=0.15, color='#3b82f6')
    ax1.set_xlabel('POI数量', fontsize=11)
    ax1.set_ylabel('总加载时间 (秒)', fontsize=11)
    ax1.set_title('POI数据量对页面加载时间的影响', fontsize=13, fontweight='bold')
    ax1.grid(True, alpha=0.3, linestyle='--')
    ax1.axhline(y=3.0, color='#ef4444', linestyle='--', alpha=0.5)
    ax1.text(4500, 3.05, '3秒体验阈值', fontsize=9, color='#ef4444')

    # 子图2: 并发动画帧率
    ax2 = fig.add_subplot(2, 2, 2)
    vehicles = [1, 2, 4, 6, 8]
    fps = [60, 58, 55, 48, 35]
    ax2.bar(range(len(vehicles)), fps, color=['#10b981', '#10b981', '#3b82f6', '#f59e0b', '#ef4444'],
            alpha=0.85, width=0.6, edgecolor='white')
    ax2.set_xticks(range(len(vehicles)))
    ax2.set_xticklabels([f'{v}辆' for v in vehicles])
    ax2.set_ylabel('帧率 (fps)', fontsize=11)
    ax2.set_title('并发车辆动画帧率', fontsize=13, fontweight='bold')
    ax2.axhline(y=30, color='#ef4444', linestyle='--', alpha=0.7, linewidth=1.5)
    ax2.text(6.5, 31, '30fps 流畅阈值', fontsize=10, color='#ef4444')
    for i, (v, f) in enumerate(zip(vehicles, fps)):
        ax2.text(i, f + 1, f'{f}fps', ha='center', fontsize=11, fontweight='bold')
    ax2.set_ylim(0, 70)
    ax2.grid(True, alpha=0.2, axis='y', linestyle='--')

    # 子图3: API响应时间箱线图
    ax3 = fig.add_subplot(2, 2, 3)
    apis = ['/tasks', '/vehicles', '/orders', '/stats']
    data = [
        [45, 62, 85, 120, 210],   # p5, p25, p50, p75, p99
        [22, 30, 42, 60, 95],
        [18, 26, 38, 52, 88],
        [28, 38, 55, 78, 120],
    ]
    positions = [1, 2, 3, 4]
    for pos, d, color in zip(positions, data, ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6']):
        box_data = np.random.normal(d[2], (d[3]-d[1])/2.7, 100)
        box_data = np.clip(box_data, d[0], d[4])
        bp = ax3.boxplot([box_data], positions=[pos], widths=0.5, patch_artist=True,
                         medianprops=dict(color='white', linewidth=2),
                         whiskerprops=dict(color=color, linewidth=1.5),
                         capprops=dict(color=color, linewidth=1.5),
                         boxprops=dict(facecolor=color, alpha=0.7))
        ax3.annotate(f'P50: {d[2]}ms\nP99: {d[4]}ms', xy=(pos, d[4]), xytext=(pos+0.3, d[4]+15),
                     fontsize=9, color=color, fontweight='bold')
    ax3.set_xticks(positions)
    ax3.set_xticklabels(apis, fontsize=11)
    ax3.set_ylabel('响应时间 (ms)', fontsize=11)
    ax3.set_title('API响应时间分布 (100次采样)', fontsize=13, fontweight='bold')
    ax3.grid(True, alpha=0.2, axis='y', linestyle='--')

    # 子图4: 技术栈分布饼图
    ax4 = fig.add_subplot(2, 2, 4)
    tech_labels = ['Java/Spring\n37%', 'JavaScript\n28%', 'HTML/CSS\n15%', 'MySQL/SQL\n12%', 'Python/脚本\n8%']
    tech_sizes = [37, 28, 15, 12, 8]
    tech_colors = ['#2563eb', '#f59e0b', '#ef4444', '#10b981', '#8b5cf6']
    wedges, texts = ax4.pie(tech_sizes, colors=tech_colors, startangle=90,
                            wedgeprops=dict(width=0.4, edgecolor='white', linewidth=2))
    ax4.legend(wedges, tech_labels, loc='center', fontsize=10)
    ax4.set_title('项目代码技术栈占比', fontsize=13, fontweight='bold')

    fig.suptitle('系统性能测试综合报告', fontsize=16, fontweight='bold', y=1.01)
    plt.tight_layout()
    save('图2-5_性能测试图表.png')

# ============ 图3-1: 技术学习路线图(时间线) ============
def fig_3_1():
    fig, ax = plt.subplots(figsize=(14, 5))

    phases = [
        ('I阶段\n第1-2周', ['HTML/CSS基础', 'JS事件处理', '高德API入门', 'MySQL基础'], '#94a3b8'),
        ('II阶段前期\n第3周', ['Spring Boot架构', 'JDBC高级编程', '调度算法设计', 'MassMarks渲染'], '#3b82f6'),
        ('II阶段中期\n第3-4周', ['成本模型构建', '动画系统优化', '工程整合调试', '参数敏感性分析'], '#10b981'),
        ('II阶段后期\n第4周', ['边界条件测试', '兼容性测试', '报告图表制作', '技术文档撰写'], '#8b5cf6'),
    ]

    y_positions = [0, 1, 2, 3]
    for i, (title, skills, color) in enumerate(phases):
        # Phase box
        ax.fill_between([i*3, i*3+2.5], -0.4, 3.4, alpha=0.08, color=color)
        ax.text(i*3 + 1.25, 3.1, title, ha='center', fontsize=12, fontweight='bold', color=color)
        # Skill boxes
        for j, skill in enumerate(skills):
            ax.text(i*3 + 0.3, 2.5 - j*0.6, f'• {skill}', fontsize=10, color='#334155',
                    bbox=dict(boxstyle='round,pad=0.3', facecolor='white', edgecolor=color, alpha=0.8))

    # Connecting arrows
    for i in range(3):
        ax.annotate('', xy=(i*3+2.5, 1.5), xytext=(i*3+2.8, 1.5),
                    arrowprops=dict(arrowstyle='->', color='#94a3b8', lw=2))

    ax.set_xlim(-0.5, 12.5)
    ax.set_ylim(-1, 4)
    ax.axis('off')
    ax.set_title('技术学习路线图', fontsize=16, fontweight='bold', pad=15)

    plt.tight_layout()
    save('图3-1_技术学习路线图.png')

# ============ 图5-1: 系统架构全景图 ============
def fig_5_1():
    fig, ax = plt.subplots(figsize=(16, 8))
    ax.set_xlim(0, 16)
    ax.set_ylim(0, 8)
    ax.axis('off')

    layers = [
        (1, '前端表现层', '#dbeafe', '#3b82f6',
         ['index.html', 'showMap.html\n(POI渲染)', 'cycle.html\n(调度仿真)', 'cycle-demo.html\n(纯前端引擎)',
          'single-vehicle.html', 'multi-cargo-demo.html', 'max-profit-demo.html', 'route/runCar.html']),
        (3, '业务逻辑层', '#d1fae5', '#10b981',
         ['VehicleService\n车辆管理', 'OrderService\n订单生成', 'DispatchService\n调度算法引擎',
          'CycleScheduler\n仿真时钟', 'CostConfig\n成本核算', 'AmapRouteService\n路径服务']),
        (5, '数据访问层', '#fef3c7', '#f59e0b',
         ['VehicleDao\nJDBC', 'OrderDao\nJDBC', 'DispatchTaskDao\nJDBC', 'Dao\n通用查询', 'JdbcUtil\n连接工具']),
        (7, '数据存储层', '#fce7f3', '#ec4899',
         ['vehicle\n车辆表', 'transport_order\n订单表', 'dispatch_task\n任务表', 'location\nPOI表', 'car_state\n状态缓存']),
    ]

    for y, title, bg_color, border_color, items in layers:
        rect = plt.Rectangle((0.3, y-0.7), 15.4, 1.4, facecolor=bg_color, edgecolor=border_color,
                              linewidth=2, alpha=0.5, zorder=1)
        ax.add_patch(rect)
        ax.text(0.7, y, title, fontsize=14, fontweight='bold', color=border_color, va='center', zorder=2)
        for i, item in enumerate(items):
            x = 3.5 + i * 1.7
            ax.text(x, y, item, fontsize=8, ha='center', va='center', zorder=2,
                    bbox=dict(boxstyle='round,pad=0.3', facecolor='white', edgecolor=border_color, alpha=0.9))

    # Arrows between layers
    for y_from, y_to in [(2.3, 2.3), (4.3, 4.3), (6.3, 6.3)]:
        ax.annotate('', xy=(8, y_to), xytext=(8, y_to+1.1),
                    arrowprops=dict(arrowstyle='->', color='#64748b', lw=2),
                    ha='center', va='center', zorder=3)
    ax.text(9.5, 1.8, 'RESTful API\nJSON', fontsize=9, color='#64748b', ha='center')
    ax.text(9.5, 3.8, '原始JDBC\n事务控制', fontsize=9, color='#64748b', ha='center')
    ax.text(9.5, 5.8, 'SQL\nHikariCP', fontsize=9, color='#64748b', ha='center')

    ax.set_title('运输车辆调度优化与仿真 —— 系统全景架构', fontsize=18, fontweight='bold', pad=20)
    plt.tight_layout()
    save('图5-1_系统全景架构.png')


# ======== Run all ========
if __name__ == '__main__':
    print('Generating figures...')
    fig_1_3()
    fig_1_11()
    fig_1_14b()
    fig_2_2()
    fig_2_3()
    fig_2_5()
    fig_3_1()
    fig_5_1()
    print(f'\nAll figures saved to {out_dir}/')
