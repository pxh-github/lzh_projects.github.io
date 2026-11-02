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

apis = [
    ('GET', '/user', '—', '200: [{id,name,location,type}] 2419条POI数组', '500', 'showMap/route/runCar', 'loadPoiData()', 'POI数据'),
    ('POST', '/dispatch/reset-all', 'vehicles=10\norders=20', '200: {"ok":true} 重置为N车+M单', '500', 'cycle / cycle-demo', '_resetAll()', '调度'),
    ('GET', '/vehicles', '—', '200: [{id,code,status,location,profit}]', '500', 'cycle / cycle-demo', 'refreshAll()', '车辆'),
    ('POST', '/vehicles/add', '—', '200: 新增车辆JSON对象', '500', 'cycle / cycle-demo', '_addVehicle()', '车辆'),
    ('GET', '/orders', '—', '200: [{id,code,status,pendingWeight}]', '500', 'cycle / cycle-demo', 'refreshAll()', '订单'),
    ('POST', '/orders/add', '—', '200: 新增订单JSON对象', '500', 'cycle / cycle-demo', '_addOrder()', '订单'),
    ('POST', '/dispatch/cycle/start', '—', '200: {"ok":true,"running":true}', '500', 'cycle.html', '_startCycle()', '循环'),
    ('POST', '/dispatch/cycle/stop', '—', '200: {"ok":true,"running":false}', '500', 'cycle.html', '_stopCycle()', '循环'),
    ('GET', '/dispatch/cycle/status', '—', '200: {"running":bool,"simTime":".."}', '500', 'cycle.html', 'checkCycleStatus()', '循环'),
    ('GET', '/dispatch/tasks', '—', '200: [{id,vehicleId,cost1-3,profit}]', '500', 'cycle.html', 'animateTaskBatch()', '任务'),
    ('POST', '/dispatch/run-batch', '—', '200: {"ok":true,"count":N}', '500', '(手动触发)', '手动调用', '调度'),
    ('GET', '/stats/overview', '—', '200: {vehicleCount,totalProfit,...}', '500', 'cycle / cycle-demo', 'updateStatsUI()', '统计'),
]

n = len(apis)
fig = plt.figure(figsize=(13, 1.6 + n * 0.55), facecolor='#f8fafc')
fig.text(0.5, 0.99, 'RESTful API 接口协议总览', ha='center', fontsize=14, fontweight='bold', color='#1e293b')
fig.text(0.5, 0.978, 'Base: http://localhost:9091 | JSON | UTF-8 | 共计 12 个接口',
         ha='center', fontsize=8, color='#64748b')

cols = [0.028, 0.12, 0.17, 0.235, 0.47, 0.64, 0.77, 0.89]
col_w = [0.09, 0.048, 0.063, 0.233, 0.168, 0.128, 0.117, 0.10]
headers = ['Method', 'URL', '参数', '成功响应(200)', '前端页面', 'JS函数', '分类', '']
# Last col unused, just for balance

col_colors = ['#2563eb', '#3b82f6', '#6366f1', '#10b981', '#8b5cf6', '#f59e0b', '#64748b', '#f8fafc']

y_top = 0.96
h_h = 0.026
for i, (cx, cw, hdr, cc) in enumerate(zip(cols, col_w, headers, col_colors)):
    if i == 7: continue
    rect = FancyBboxPatch((cx, y_top - h_h), cw, h_h, boxstyle='round,pad=0.002',
                          facecolor=cc, edgecolor=cc, alpha=0.85, linewidth=0, transform=fig.transFigure)
    fig.patches.append(rect)
    fig.text(cx + cw/2, y_top - h_h/2, hdr, ha='center', va='center',
             fontsize=7.5, fontweight='bold', color='white', transform=fig.transFigure)

method_colors = {'GET': '#10b981', 'POST': '#f59e0b'}

for row, api in enumerate(apis):
    method, url, params, success, error, page, js_func, cat = api
    y = y_top - h_h - (row + 1) * 0.0185
    bg = '#ffffff' if row % 2 == 0 else '#f1f5f9'

    values = [method, url, params, success, page, js_func, cat]
    for i, (cx, cw, val) in enumerate(zip(cols, col_w, values)):
        if i == 7: continue
        rect = FancyBboxPatch((cx, y - 0.016), cw, 0.0185, boxstyle='round,pad=0.001',
                              facecolor=bg, edgecolor='#e2e8f0', linewidth=0.25, transform=fig.transFigure)
        fig.patches.append(rect)

        if i == 0:
            mc = method_colors.get(val.strip(), '#64748b')
            fig.text(cx + cw/2, y, val.strip(), ha='center', va='center',
                     fontsize=7, fontweight='bold', color=mc, transform=fig.transFigure)
        elif i == 1:
            fig.text(cx + cw/2, y, val.replace('/dispatch/','/d/').replace('/cycle/','/c/'),
                     ha='center', va='center', fontsize=6.5, color='#1e293b', transform=fig.transFigure)
        elif i == 3:
            fig.text(cx + 0.003, y, val, ha='left', va='center',
                     fontsize=5.8, color='#334155', transform=fig.transFigure)
        elif i == 4:
            fig.text(cx + cw/2, y, val, ha='center', va='center',
                     fontsize=6.2, color='#8b5cf6', transform=fig.transFigure)
        elif i == 5:
            fig.text(cx + cw/2, y, val, ha='center', va='center',
                     fontsize=6.2, color='#f59e0b', transform=fig.transFigure)
        else:
            fig.text(cx + cw/2, y, val, ha='center', va='center',
                     fontsize=6.5, color='#334155', transform=fig.transFigure)

# Bottom legends
ly = y_top - h_h - (n + 1) * 0.0185 - 0.005
legs = [('GET', '#10b981'), ('POST', '#f59e0b'), ('cycle.html', '#8b5cf6'),
        ('cycle-demo', '#8b5cf6'), ('showMap', '#8b5cf6')]
fig.text(0.03, ly, '■', fontsize=7, color='#64748b', transform=fig.transFigure)
for i, (l, c) in enumerate(legs):
    fig.text(0.045 + i * 0.13, ly, f'■ {l}', fontsize=6.5, color=c, transform=fig.transFigure)

path = os.path.join(out_dir, '图1-10c_API接口协议总览.png')
fig.savefig(path, facecolor=fig.get_facecolor(), edgecolor='none', dpi=150, bbox_inches='tight')
plt.close()
print(f'Saved: {path}')
