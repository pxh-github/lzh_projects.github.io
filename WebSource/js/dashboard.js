(() => {
    const $ = id => document.getElementById(id);
    let snapshot, selectedVehicle, history = [];
    const eventNames={TRAFFIC_JAM:'交通拥堵',WEATHER:'恶劣天气',VEHICLE_FAULT:'车辆故障'};
    let eventRequest=null,refreshing=false;
    const eventDialog=$('live-event-dialog');
    $('open-live-event').onclick=()=>{
        if(!snapshot)return;
        $('live-event-vehicle').replaceChildren();
        snapshot.vehicles.forEach(v=>$('live-event-vehicle').add(new Option(`${v.vehicleCode||v.id} · ${v.status==='BUSY'?'运输 / 不可用':'空闲'}`,v.id)));
        $('live-event-error').textContent='';eventRequest=null;eventDialog.showModal();
    };
    $('close-live-event').onclick=$('cancel-live-event').onclick=()=>eventDialog.close();
    $('live-event-type').onchange=()=>{$('live-event-vehicle-field').hidden=$('live-event-type').value==='WEATHER';};
    $('live-event-form').onsubmit=async e=>{
        e.preventDefault();const button=$('create-live-event');if(button.disabled)return;
        const parameters={eventType:$('live-event-type').value,vehicleId:$('live-event-type').value==='WEATHER'?null:Number($('live-event-vehicle').value),durationMinutes:Number($('live-event-duration').value),delayMinutes:Number($('live-event-delay').value)};
        if(!eventRequest||JSON.stringify(eventRequest.parameters)!==JSON.stringify(parameters))eventRequest={parameters,requestKey:crypto.randomUUID()};
        button.disabled=true;$('live-event-error').textContent='';
        try {await SimulationService.triggerEvent({...parameters,requestKey:eventRequest.requestKey});eventDialog.close();eventRequest=null;await refresh();window.dispatchEvent(new Event('live-event-created'));}
        catch(error){$('live-event-error').textContent=error.response?.data?.message||'事件提交失败，可重试同一请求';}
        finally{button.disabled=false;}
    };
    function renderEvents(events) {
        const container=$('live-events');container.replaceChildren();
        if(!events.length){const empty=document.createElement('div');empty.className='empty-state';empty.textContent='暂无事件 · 可模拟拥堵、天气和车辆故障';container.append(empty);}
        events.slice(0,12).forEach(event=>{
            const row=document.createElement('div');row.className='event-row'+(event.status==='ENDED'?' ended':'');
            const title=document.createElement('strong'),detail=document.createElement('small');
            title.textContent=`${eventNames[event.eventType]||event.eventType} · ${event.status==='ACTIVE'?'生效中':'已结束'} · 仿真`;
            detail.textContent=`${event.affectedVehicleIds.length}辆车 · ${event.affectedTaskCount}项任务延后${event.delayMinutes}分钟 · 持续至${event.endTime.replace('T',' ')}`;
            row.append(title,detail);container.append(row);
        });
        window.updateLiveEvents?.(events);
    }
    $('metric-cards').innerHTML = Metrics.definitions.map(([key, label, unit]) => `<article class="metric-card"><span>${label}</span><div><strong id="metric-${key}">—</strong> <small>${unit}</small></div><small id="delta-${key}">${key === 'totalCarbonEmission' ? '油耗模型估算 · kg CO₂' : 'Baseline 对比：未获取'}</small></article>`).join('');
    $('compare-live').onclick = async () => {
        $('compare-live').disabled = true;
        try {
            const comparison = await SimulationService.compareModes();
            Metrics.definitions.forEach(([key, , , higher]) => {
                const value = Metrics.improvement(comparison.baseline[key], comparison.candidate[key], higher);
                $('delta-' + key).textContent = value === null ? '模式对比：缺少数据或基准为 0' : `模式快照：${value >= 0 ? '改善' : '退化'} ${Metrics.format(Math.abs(value))}%`;
            });
            $('compare-message').textContent = '已获取真实模式快照对比（独立于实时主值）· ' + new Date().toLocaleTimeString('zh-CN');
        } catch (e) { $('compare-message').textContent = '模式对比请求失败，请稍后重试'; }
        finally { $('compare-live').disabled = false; }
    };
    $('trend-select').innerHTML = Metrics.definitions.map(([key, label, unit]) => `<option value="${key}">${label} (${unit})</option>`).join('');
    function chart() {
        const key = $('trend-select').value;
        const result=StatCharts.trend(history,key);
        $('trend').innerHTML=result.svg; $('trend-range').textContent=result.note;
        $('trend-explanation').textContent=StatCharts.guidance[key];
    }
    function details(title, pairs) {
        $('detail-title').textContent = title;
        $('detail-body').replaceChildren();
        pairs.forEach(([label, value]) => {
            const row = document.createElement('div'); row.className = 'detail-row';
            const term = document.createElement('span'), text = document.createElement('strong');
            term.textContent = label; text.textContent = value ?? '未提供'; row.append(term, text); $('detail-body').append(row);
        });
        $('detail-panel').hidden = false;
    }
    const status = value => ({ IDLE: '空闲', BUSY: '运输中', DONE: '已完成', PENDING: '待匹配', PARTIAL: '部分分配', ASSIGNED: '已分配' }[value] || value || '未提供');
    function vehicleDetail(id) {
        if (!snapshot) return;
        selectedVehicle = id;
        const v = snapshot.vehicles.find(v => String(v.id) === String(id)); if (!v) return;
        const totals = snapshot.vehicleTotals.find(t => String(t.vehicleId) === String(id)) || { emptyDistance: 0, loadedDistance: 0, waitMinutes: 0, completedOrders: 0, completedTonnage: 0 };
        const empty = Metrics.number(totals.emptyDistance), loaded = Metrics.number(totals.loadedDistance);
        details(`车辆 ${v.vehicleCode || v.id}`, [['状态', status(v.status)], ['当前载货 / 额定载重', `未提供 / ${Metrics.format(v.maxLoadTon)} t`], ['累计等待（已完成任务）', `${Metrics.format(totals.waitMinutes)} min`], ['空载里程（全部分配任务）', `${Metrics.format(empty)} km`], ['载重里程（全部分配任务）', `${Metrics.format(loaded)} km`], ['空驶率', `${Metrics.format(Metrics.ratio(empty, empty === null || loaded === null ? null : empty + loaded))}%`], ['完成运输的订单数', totals.completedOrders], ['运输吨位（已完成任务）', `${Metrics.format(totals.completedTonnage)} t`]]);
    }
    window.DashboardDetails = { vehicle: vehicleDetail, order(id) {
        selectedVehicle = null;
        const o = snapshot?.orders.find(o => String(o.id) === String(id)); if (!o) return;
        const tasks = snapshot.tasks.filter(t => String(t.orderId) === String(id));
        details(`订单 ${o.orderCode || o.id} · 货物`, [['状态', status(o.status)], ['近期任务关联车辆', [...new Set(tasks.map(t => t.vehicleId))].join('、') || '近期无记录'], ['货物类型', o.cargoType], ['货物重量', `${Metrics.format(o.cargoTotalWeight)} t`], ['加工阶段', null], ['体积', null], ['实际到达重量', null], ['创建时间', o.createdAt], ['匹配尝试 / 司机拒绝', null], ['完成时间', null]]);
    } };
    $('close-detail').onclick = () => { selectedVehicle = null; $('detail-panel').hidden = true; };
    $('trend-select').onchange = chart;
    let stopped = false, timer;
    async function refresh() {
        if(refreshing)return;
        clearTimeout(timer);refreshing=true;
        try {
            snapshot = await SimulationService.snapshot();
            const m = snapshot.metrics;
            $('data-health').textContent = `实时接口已连接 · ${snapshot.cycle.running ? '仿真运行中' : '仿真已停止'} · ${new Date().toLocaleTimeString('zh-CN')}`;
            Metrics.definitions.forEach(([key]) => { $('metric-' + key).textContent = Metrics.format(m[key]); });
            $('runtime-status').textContent = `忙碌 / 不可用 ${m.runningVehicleCount} / ${m.totalVehicleCount}　 ·　 待处理 ${m.pendingOrderCount}　 ·　 完成 ${Metrics.format(m.orderCompletionRate)}%　 ·　 生效事件 ${snapshot.activeEventCount}　 ·　 仿真时间 ${m.simulationTime}`;
            $('fleet-status').textContent = `忙碌 / 不可用 ${m.runningVehicleCount} 辆 · 空闲 ${snapshot.vehicles.filter(v => v.status === 'IDLE').length} 辆 · 事件按仿真时钟生效`;
            renderEvents(snapshot.events);$('open-live-event').disabled=!snapshot.vehicles.length;
            window.applyMonitorSnapshot?.(snapshot);
            history=snapshot.history; chart();
            if (selectedVehicle !== null && selectedVehicle !== undefined) vehicleDetail(selectedVehicle);
        } catch (e) { $('data-health').textContent = '接口连接失败 · ' + (snapshot ? '保留上次数据，数据已过期' : '请启动后端服务');$('open-live-event').disabled=true; }
        finally{refreshing=false;}
        if (!stopped) timer = setTimeout(refresh, 3000);
    }
    window.addEventListener('simulation-reset', () => { history = []; chart(); });
    window.addEventListener('pagehide', () => { stopped = true; clearTimeout(timer); });
    window.addEventListener('pageshow', e=>{if(e.persisted){stopped=false;refresh();}});
    chart(); refresh();
})();
