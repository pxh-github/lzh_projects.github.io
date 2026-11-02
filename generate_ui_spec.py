# -*- coding: utf-8 -*-
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
import os

plt.rcParams['font.sans-serif'] = ['Microsoft YaHei', 'SimHei']
plt.rcParams['axes.unicode_minus'] = False

out_dir = 'e:/works/figures'
os.makedirs(out_dir, exist_ok=True)

fig = plt.figure(figsize=(10, 18), facecolor='#f8fafc')

# ===== TITLE =====
fig.text(0.5, 0.985, '运输车辆调度优化与仿真 —— UI设计规范', ha='center', fontsize=20, fontweight='bold', color='#1e293b')
fig.text(0.5, 0.965, 'Design Specification v2.0', ha='center', fontsize=11, color='#64748b', style='italic')

# ===== SECTION 1: COLOR PALETTE =====
ax1 = fig.add_axes([0.06, 0.715, 0.88, 0.23])
ax1.set_xlim(0, 12)
ax1.set_ylim(0, 4.5)
ax1.axis('off')
ax1.text(0, 4.3, '色彩系统 Color System', fontsize=15, fontweight='bold', color='#1e293b')

palettes = [
    ('主色', [(0.3, '#2563eb', '主色调\n按钮/链接'),
              (1.2, '#1d4ed8', '深色变体\nhover态'),
              (2.1, '#dbeafe', '浅色背景\n选中态')]),
    ('功能色', [(3.5, '#10b981', '成功/空闲\nIDLE'),
               (4.4, '#ef4444', '危险/亏损\n负利润'),
               (5.3, '#f59e0b', '警告/运输中\nBUSY'),
               (6.2, '#94a3b8', '禁用/次要\n辅助')]),
    ('车辆色', [(7.6, '#10b981', '小型1.5T'),
               (8.5, '#3b82f6', '中型6T'),
               (9.4, '#f59e0b', '大型15T'),
               (10.3, '#ef4444', '重型40T')]),
]

for title, items in palettes:
    y_base = 3.2 if '主' in title else (1.7 if '功能' in title else 0.2)
    ax1.text(0, y_base + 0.5, title, fontsize=9, color='#64748b', fontweight='bold')
    for x, color, desc in items:
        swatch = FancyBboxPatch((x, y_base - 0.4), 0.75, 0.75, boxstyle='round,pad=0.04',
                                facecolor=color, edgecolor=color, linewidth=1.2)
        ax1.add_patch(swatch)
        ax1.text(x + 0.375, y_base - 0.65, color, ha='center', fontsize=6.5, color='#64748b')
        ax1.text(x + 0.375, y_base - 1.05, desc, ha='center', fontsize=7, color='#475569')

# ===== SECTION 2: BUTTON STATES =====
ax2 = fig.add_axes([0.06, 0.50, 0.88, 0.19])
ax2.set_xlim(0, 12)
ax2.set_ylim(0, 3.8)
ax2.axis('off')
ax2.text(0, 3.65, '按钮状态 Button States', fontsize=15, fontweight='bold', color='#1e293b')

btn_specs = [
    ('btn-primary  主要操作按钮', '#2563eb', '#1d4ed8', '#94a3b8', 2.8),
    ('btn-danger   危险操作/重置', '#ef4444', '#dc2626', '#94a3b8', 1.6),
    ('btn-normal   默认/次要按钮', '#2563eb', '#1d4ed8', '#94a3b8', 0.4),
]

for label, normal, hover, disabled, y in btn_specs:
    ax2.text(0, y + 0.3, label, fontsize=9, color='#475569')
    states = [('Normal', normal, 0.85), ('Hover', hover, 0.9), ('Disabled', disabled, 0.4)]
    for i, (state, color, alpha) in enumerate(states):
        x = 3.5 + i * 3
        btn = FancyBboxPatch((x, y - 0.25), 2.4, 0.5, boxstyle='round,pad=0.08',
                             facecolor=color, edgecolor=color, alpha=alpha, linewidth=0)
        ax2.add_patch(btn)
        txt_c = 'white' if alpha > 0.5 else '#64748b'
        ax2.text(x + 1.2, y, state, ha='center', va='center', fontsize=9, color=txt_c, fontweight='bold')
        ax2.text(x + 1.2, y - 0.5, color, ha='center', fontsize=7, color='#64748b')

# ===== SECTION 3: TYPOGRAPHY =====
ax3 = fig.add_axes([0.06, 0.28, 0.88, 0.20])
ax3.set_xlim(0, 12)
ax3.set_ylim(0, 4)
ax3.axis('off')
ax3.text(0, 3.9, '字体排版 Typography', fontsize=15, fontweight='bold', color='#1e293b')

typo_specs = [
    (3.3, '控制面板标题', 16, 700, '#1e293b', 'font-family: Microsoft YaHei, sans-serif'),
    (2.6, '模块小标题 h3', 14, 600, '#64748b', 'font-size: 14px; color: #64748b;'),
    (1.9, '正文数据 车辆/订单信息', 13, 400, '#334155', 'font-size: 13px; line-height: 1.7;'),
    (1.2, '辅助说明文字 .hint', 11, 400, '#94a3b8', 'font-size: 11px; color: #94a3b8;'),
    (0.5, '等宽数字 收益/成本统计', 13, 600, '#10b981', 'font-variant-numeric: tabular-nums;'),
]

for y, label, size, weight, color, css in typo_specs:
    ax3.text(0, y - 0.05, label, fontsize=8.5, color='#64748b', va='center')
    ax3.text(3.2, y + 0.05, '运输车辆调度优化 12345.67', fontsize=size, fontweight=weight,
             color=color, va='center')
    ax3.text(3.2, y - 0.4, css, fontsize=6.5, color='#94a3b8', va='center')

for ys in [3.55, 2.85, 2.15, 1.45]:
    ax3.axhline(y=ys, xmin=0.0, xmax=0.97, color='#e2e8f0', linewidth=0.5)

# ===== SECTION 4: SPACING =====
ax4 = fig.add_axes([0.06, 0.02, 0.88, 0.24])
ax4.set_xlim(0, 12)
ax4.set_ylim(0, 5)
ax4.axis('off')
ax4.text(0, 4.85, '间距规范 Spacing', fontsize=15, fontweight='bold', color='#1e293b')

# Panel mockup
px, py, pw, ph = 0.5, 0.3, 7.5, 4.2
panel = FancyBboxPatch((px, py), pw, ph, boxstyle='round,pad=0.12',
                       facecolor='white', edgecolor='#cbd5e1', linewidth=1.2)
ax4.add_patch(panel)
ax4.text(px + 0.2, py + ph - 0.35, '控制面板 Panel', fontsize=10, fontweight='bold', color='#1e293b')

# Section blocks inside panel
blocks = [
    (px + 0.25, py + ph - 0.85, pw - 0.5, 0.7, '#f8fafc', '循环调度'),
    (px + 0.25, py + ph - 1.7, pw - 0.5, 0.7, '#f8fafc', '动态添加'),
    (px + 0.25, py + ph - 2.55, pw - 0.5, 0.9, '#f8fafc', '概览统计卡片'),
]
for bx, by, bw, bh, bc, bl in blocks:
    b = FancyBboxPatch((bx, by), bw, bh, boxstyle='round,pad=0.03',
                       facecolor=bc, edgecolor='#e2e8f0', linewidth=0.7)
    ax4.add_patch(b)
    ax4.text(bx + 0.15, by + bh - 0.3, bl, fontsize=7.5, color='#64748b', fontweight='bold')

# Spacing arrows
ax4.annotate('', xy=(px + pw, py + ph), xytext=(px, py + ph),
             arrowprops=dict(arrowstyle='<->', color='#3b82f6', lw=1.2))
ax4.text(px + pw/2, py + ph + 0.1, 'padding 16px × 2', ha='center', fontsize=7, color='#3b82f6')

ax4.annotate('', xy=(px + pw/2, py + ph - 0.85), xytext=(px + pw/2, py + ph - 1.7),
             arrowprops=dict(arrowstyle='<->', color='#10b981', lw=1.2))
ax4.text(px + pw/2 + 0.3, py + ph - 1.3, '区块间距 14px', fontsize=7, color='#10b981')

ax4.annotate('', xy=(px + pw - 0.2, py + ph - 0.85), xytext=(px + pw - 0.2, py + ph - 1.55),
             arrowprops=dict(arrowstyle='<->', color='#f59e0b', lw=1.2))
ax4.text(px + pw - 0.1, py + ph - 1.25, '区块内距 14px', fontsize=7, color='#f59e0b')

# Spacing spec text on the right
specs = [
    'padding: 16px',
    'margin-bottom: 14px',
    '区块内间距: 14px',
    '按钮 gap: 8px',
    'border-radius: 14px',
    'box-shadow: 0 10px 30px\n  rgba(0,0,0,0.18)',
]
for i, s in enumerate(specs):
    ax4.text(px + pw + 0.3, py + ph - 1.0 - i * 0.45, s, fontsize=7, color='#475569')

path = os.path.join(out_dir, '图1-10b_UI设计规范.png')
fig.savefig(path, facecolor=fig.get_facecolor(), edgecolor='none', dpi=150, bbox_inches='tight')
plt.close()
print(f'Saved: {path}')
