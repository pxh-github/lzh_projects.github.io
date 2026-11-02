# -*- coding: utf-8 -*-
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Arc, Wedge, Rectangle, Circle
import numpy as np
import os

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei']
plt.rcParams['axes.unicode_minus'] = False

out_dir = 'e:/works/figures'
os.makedirs(out_dir, exist_ok=True)

# ============================================================
# 图1-11: 三种调度策略对比分析表
# ============================================================
def fig_1_11():
    fig = plt.figure(figsize=(12, 5.5), facecolor='#f8fafc')
    fig.text(0.5, 0.97, '调度策略对比分析', ha='center', fontsize=15, fontweight='bold', color='#1e293b')

    strategies = [
        ('NF基础算法', 'O(n)', '★★☆\n中等', '订单已按重量预排序\n单车简单装载', 'I阶段基础版本\n已整合',
         [('复杂度', 'O(n)'), ('质量', '★★☆'), ('场景', '简单装载'), ('状态', '已整合')]),
        ('NF + 启发式插入', 'O(n²)', '★★★\n较好', '单车多订单\n需要优化取送顺序', 'II阶段核心算法\nfillVehicleNF',
         [('复杂度', 'O(n²)'), ('质量', '★★★'), ('场景', '单车优化'), ('状态', '核心算法')]),
        ('NF + 启发式 + 2-opt', 'O(n²·iter)', '★★★★\n良好', '单车多订单\n追求更优解', 'II阶段增强算法\n2-opt≤50轮迭代',
         [('复杂度', 'O(n²·50)'), ('质量', '★★★★'), ('场景', '单车精细'), ('状态', '已启用')]),
        ('全排列穷举 (理论最优)', 'O(n!)', '★★★★★\n最优', '仅benchmark对比\nn≤6时可行', '未采用\n计算不可行',
         [('复杂度', 'O(n!)'), ('质量', '★★★★★'), ('场景', '基准对比'), ('状态', '不采用')]),
    ]

    # Table layout
    table_x, table_y = 0.08, 0.06
    table_w, table_h = 0.84, 0.87
    header_h = 0.07
    row_h = 0.18
    col_ratios = [0.24, 0.18, 0.14, 0.20, 0.24]
    col_x = [table_x]
    for r in col_ratios[:-1]:
        col_x.append(col_x[-1] + r)

    headers = ['策略名称', '时间复杂度', '求解质量', '适用场景', '本系统采用情况']
    header_colors = ['#2563eb', '#6366f1', '#8b5cf6', '#10b981', '#f59e0b']
    row_colors = ['#ffffff', '#f1f5f9', '#ffffff', '#fef2f2']

    # Draw headers
    for i, (hdr, hc) in enumerate(zip(headers, header_colors)):
        cx = col_x[i]
        cw = col_ratios[i]
        rect = FancyBboxPatch((cx, table_y + table_h - header_h), cw, header_h,
                              boxstyle='round,pad=0.005', facecolor=hc, edgecolor=hc,
                              alpha=0.88, linewidth=0, transform=fig.transFigure)
        fig.patches.append(rect)
        fig.text(cx + cw/2, table_y + table_h - header_h/2, hdr, ha='center', va='center',
                 fontsize=10, fontweight='bold', color='white', transform=fig.transFigure)

    # Draw rows
    for row, (name, complexity, quality, scenario, status, _) in enumerate(strategies):
        vals = [name, complexity, quality, scenario, status]
        row_top = table_y + table_h - header_h - (row + 1) * row_h
        bg = row_colors[row]
        for i, (cx, cw, val) in enumerate(zip(col_x, col_ratios, vals)):
            rect = FancyBboxPatch((cx, row_top - row_h + 0.005), cw, row_h - 0.005,
                                  boxstyle='round,pad=0.005', facecolor=bg,
                                  edgecolor='#e2e8f0', linewidth=0.5, transform=fig.transFigure)
            fig.patches.append(rect)
            if i == 0:
                fig.text(cx + cw/2, row_top - row_h/2, val, ha='center', va='center',
                         fontsize=9.5, fontweight='bold', color='#1e293b', transform=fig.transFigure)
            elif i == 1:
                fig.text(cx + cw/2, row_top - row_h/2, val, ha='center', va='center',
                         fontsize=8.5, color='#6366f1', fontweight='bold', transform=fig.transFigure)
            elif i == 2:
                fig.text(cx + cw/2, row_top - row_h/2, val, ha='center', va='center',
                         fontsize=8, color='#334155', transform=fig.transFigure)
            elif i == 4 and row == 3:
                fig.text(cx + cw/2, row_top - row_h/2, val, ha='center', va='center',
                         fontsize=8, color='#ef4444', fontweight='bold', transform=fig.transFigure)
            else:
                fig.text(cx + cw/2, row_top - row_h/2, val, ha='center', va='center',
                         fontsize=8, color='#334155', transform=fig.transFigure)

    # Arrow showing evolution
    for i in range(3):
        ax = col_x[0] + col_ratios[0] / 2
        ay1 = table_y + table_h - header_h - (i + 1) * row_h
        ay2 = table_y + table_h - header_h - (i + 2) * row_h
        fig.text(0.04, (ay1 + ay2) / 2 - row_h/4, '↓', ha='center', fontsize=11, color='#94a3b8',
                 fontweight='bold', transform=fig.transFigure)

    path = os.path.join(out_dir, '图1-11_调度策略对比表.png')
    fig.savefig(path, facecolor=fig.get_facecolor(), edgecolor='none', dpi=150, bbox_inches='tight')
    plt.close()
    print(f'Saved: {path}')


# ============================================================
# 图1-14: 评估指标可视化示意图
# ============================================================
def fig_1_14():
    fig = plt.figure(figsize=(14, 9), facecolor='#f8fafc')
    fig.text(0.5, 0.97, '调度评估指标可视化', ha='center', fontsize=15, fontweight='bold', color='#1e293b')

    # ---- Panel 1 (top-left): 空载 vs 载货里程 ----
    ax1 = fig.add_axes([0.04, 0.48, 0.46, 0.46])
    ax1.set_xlim(0, 10)
    ax1.set_ylim(0, 8)
    ax1.axis('off')
    ax1.text(0, 7.7, '空载里程 vs 载货里程', fontsize=13, fontweight='bold', color='#1e293b')

    # Vehicle path illustration
    # Start point
    ax1.add_patch(Circle((0.8, 5.5), 0.25, facecolor='#2563eb', edgecolor='white', linewidth=2, zorder=3))
    ax1.text(0.8, 5.1, '起点\n(车辆位置)', ha='center', fontsize=7.5, color='#2563eb', fontweight='bold')

    # Pickup
    ax1.add_patch(Circle((3.5, 5.5), 0.25, facecolor='#f59e0b', edgecolor='white', linewidth=2, zorder=3))
    ax1.text(3.5, 5.1, '取货地', ha='center', fontsize=7.5, color='#f59e0b', fontweight='bold')

    # Delivery
    ax1.add_patch(Circle((7, 5.5), 0.25, facecolor='#ef4444', edgecolor='white', linewidth=2, zorder=3))
    ax1.text(7, 5.1, '送货地', ha='center', fontsize=7.5, color='#ef4444', fontweight='bold')

    # Empty mileage arrow (dashed, gray)
    ax1.annotate('', xy=(3.2, 5.5), xytext=(1.1, 5.5),
                 arrowprops=dict(arrowstyle='->', color='#94a3b8', lw=2.5, linestyle='dashed'))
    ax1.text(2.15, 5.85, '空载里程 (Empty)', ha='center', fontsize=9, color='#94a3b8', fontweight='bold')
    ax1.text(2.15, 5.5, '距离: pickupDist km\n成本: 仅磨损+油耗', ha='center', fontsize=7, color='#64748b')

    # Loaded mileage arrow (solid, blue)
    ax1.annotate('', xy=(6.7, 5.5), xytext=(3.8, 5.5),
                 arrowprops=dict(arrowstyle='->', color='#3b82f6', lw=2.5))
    ax1.text(5.25, 5.85, '载货里程 (Loaded)', ha='center', fontsize=9, color='#3b82f6', fontweight='bold')
    ax1.text(5.25, 5.5, '距离: transportDist km\n产生收入 Revenue', ha='center', fontsize=7, color='#64748b')

    # Ratio bar
    ax1.text(0.5, 4.0, '里程占比:', fontsize=8, color='#475569')
    # Empty bar
    ax1.add_patch(FancyBboxPatch((2.2, 3.75), 3.0, 0.4, boxstyle='round,pad=0.04',
                                  facecolor='#94a3b8', edgecolor='none', alpha=0.7))
    ax1.text(3.7, 3.95, '空载 35%', ha='center', fontsize=8, color='white', fontweight='bold')
    # Loaded bar
    ax1.add_patch(FancyBboxPatch((5.3, 3.75), 5.5, 0.4, boxstyle='round,pad=0.04',
                                  facecolor='#3b82f6', edgecolor='none', alpha=0.7))
    ax1.text(8.05, 3.95, '载货 65%', ha='center', fontsize=8, color='white', fontweight='bold')

    # Formula
    ax1.text(0.5, 3.2, '空载率 = 空载里程 / (空载 + 载货) × 100%', fontsize=8.5, color='#475569',
             bbox=dict(boxstyle='round,pad=0.3', facecolor='#f1f5f9', edgecolor='#e2e8f0'))

    # ---- Panel 2 (top-right): 等待时间构成 ----
    ax2 = fig.add_axes([0.53, 0.48, 0.45, 0.46])
    ax2.set_xlim(0, 10)
    ax2.set_ylim(0, 8)
    ax2.axis('off')
    ax2.text(0, 7.7, '订单等待时间构成', fontsize=13, fontweight='bold', color='#1e293b')

    # Timeline
    times = [
        (0, 2.5, '#f59e0b', '队列等待', '订单创建后\n等待被调度分配\n~1.2h'),
        (2.5, 4.0, '#94a3b8', '取货空驶', '车辆空驶到取货地\n距离/speed\n~0.8h'),
        (4.0, 6.0, '#3b82f6', '载货运输', '取货地→送货地\n距离/speed\n~1.5h'),
        (6.0, 6.5, '#10b981', '卸货', '卸货交接\n~0.3h'),
    ]

    # Draw timeline bar
    for start, end, color, label, desc in times:
        w = end - start
        bar = FancyBboxPatch((start + 0.5, 5.5), w, 0.6, boxstyle='round,pad=0.04',
                              facecolor=color, edgecolor='white', linewidth=1.5, alpha=0.85)
        ax2.add_patch(bar)
        if w > 0.8:
            ax2.text(start + 0.5 + w/2, 5.8, label, ha='center', fontsize=8.5, color='white', fontweight='bold')
        # Description below
        ax2.text(start + 0.5 + w/2, 5.15, desc, ha='center', fontsize=6.5, color='#475569')

    # Time axis
    ax2.annotate('', xy=(7.5, 4), xytext=(0.5, 4),
                 arrowprops=dict(arrowstyle='->', color='#64748b', lw=1))
    ax2.text(4, 3.65, '时间轴 →', ha='center', fontsize=8, color='#64748b')

    # Total
    ax2.text(0.5, 2.8, '订单平均等待时间 = 队列等待 + 取货空驶 + 载货运输 + 卸货交接',
             fontsize=8.5, color='#475569',
             bbox=dict(boxstyle='round,pad=0.3', facecolor='#f1f5f9', edgecolor='#e2e8f0'))

    # Cost2 visualization
    ax2.text(0.5, 2.0, 'Cost2 货物损耗 = weight × 3.0 × (1.05^总等待时间 - 1)',
             fontsize=8, color='#ef4444', fontweight='bold',
             bbox=dict(boxstyle='round,pad=0.3', facecolor='#fef2f2', edgecolor='#ef4444', alpha=0.6))

    # ---- Panel 3 (bottom): 载重利用率 ----
    ax3 = fig.add_axes([0.04, 0.04, 0.94, 0.38])
    ax3.set_xlim(0, 14)
    ax3.set_ylim(0, 5)
    ax3.axis('off')
    ax3.text(0, 4.8, '载重利用率示例 (4种车型 × 不同订单)', fontsize=13, fontweight='bold', color='#1e293b')

    examples = [
        ('SMALL 1.5T', 1.5, 1.2, '#10b981', '小型货车\n运1.2T钢材\n利用率80% ✓'),
        ('MEDIUM 6T', 6.0, 3.5, '#3b82f6', '中型货车\n运3.5T食品\n利用率58% △'),
        ('LARGE 15T', 15.0, 12.0, '#f59e0b', '大型货车\n运12T建材\n利用率80% ✓'),
        ('HEAVY 40T', 40.0, 4.0, '#ef4444', '重型卡车\n运4T电子元件\n利用率仅10% ✗\n→ 大车拉小货!'),
    ]

    for i, (name, capacity, used, color, desc) in enumerate(examples):
        x_base = 0.5 + i * 3.4
        y_top = 4.2

        # Label
        ax3.text(x_base + 1.25, y_top + 0.2, name, ha='center', fontsize=9.5, fontweight='bold', color=color)

        # Capacity bar (background)
        bar_h = 0.4
        bar_w = 2.5
        ax3.add_patch(FancyBboxPatch((x_base, y_top - bar_h - 0.2), bar_w, bar_h,
                                      boxstyle='round,pad=0.03', facecolor='#e5e7eb', edgecolor='none'))

        # Used bar (foreground)
        ratio = used / capacity
        used_w = bar_w * ratio
        ax3.add_patch(FancyBboxPatch((x_base, y_top - bar_h - 0.2), used_w, bar_h,
                                      boxstyle='round,pad=0.03', facecolor=color, edgecolor='none', alpha=0.85))

        # Percentage
        ax3.text(x_base + used_w/2, y_top - bar_h/2 - 0.2, f'{ratio*100:.0f}%',
                 ha='center', va='center', fontsize=9, color='white', fontweight='bold')

        # Numbers below bar
        ax3.text(x_base, y_top - bar_h - 0.45, f'载重: {capacity}T', fontsize=7, color='#64748b')
        ax3.text(x_base + bar_w, y_top - bar_h - 0.45, f'实载: {used}T', fontsize=7, color=color,
                 ha='right', fontweight='bold')

        # Description
        ax3.text(x_base + 1.25, y_top - bar_h - 0.9, desc, ha='center', fontsize=7.5, color='#475569')

    # Waste warning arrow for the last one
    ax3.annotate('⚠ 载重浪费', xy=(11.9, 2.8), xytext=(12.8, 3.8),
                 fontsize=9, color='#ef4444', fontweight='bold',
                 arrowprops=dict(arrowstyle='->', color='#ef4444', lw=2, connectionstyle='arc3,rad=0.3'))

    # Formula
    ax3.text(0.5, 0.4, '载重利用率 = 实际装载重量 / 车辆最大载重 × 100%',
             fontsize=9, color='#475569',
             bbox=dict(boxstyle='round,pad=0.3', facecolor='#f1f5f9', edgecolor='#e2e8f0'))

    path = os.path.join(out_dir, '图1-14_评估指标可视化.png')
    fig.savefig(path, facecolor=fig.get_facecolor(), edgecolor='none', dpi=150, bbox_inches='tight')
    plt.close()
    print(f'Saved: {path}')


if __name__ == '__main__':
    fig_1_11()
    fig_1_14()
