(async () => {
    const $=id=>document.getElementById(id),base=window.SIMULATION_API_BASE || window.location.origin;
    const query=new URLSearchParams(window.location.search),markers=new Map(),lines=new Map();
    const eventNames={TRAFFIC_JAM:'交通拥堵',WEATHER:'天气影响',VEHICLE_FAULT:'车辆故障',CARGO_DAMAGE:'货损',SHORTAGE:'缺货'};
    const algorithmNames={BASELINE:'基准调度',OPTIMIZED:'收益优化',WEIGHTED:'加权多目标'};
    const colors={SMALL_1_5T:'#10b981',MEDIUM_6T:'#3b82f6',LARGE_15T:'#f59e0b',HEAVY_40T:'#ef4444'};
    let map,engine,clock,payload,history=[],catalog,poiLayer,poiVisible=false,loadVersion=0,animation,lastTick=0,lastPaint=-Infinity,selectedVehicle;
    document.querySelector('.page-header h1').textContent='沙箱实验 · 车辆回放';
    document.querySelector('#control-panel h2').textContent='沙箱回放控制台';
    document.querySelector('.events-panel h3').textContent='本次实验 · 随机事件';
    $('replay-controls').hidden=false;
    $('route-motion-status').textContent='沙箱轨迹 · 按任务时序插值';
    $('metric-cards').innerHTML=Metrics.definitions.map(([key,label,unit])=>`<article class="metric-card"><span>${label}</span><div><strong id="metric-${key}">—</strong> <small>${unit}</small></div><small>按回放时刻已完成任务累计</small></article>`).join('');
    $('trend-select').innerHTML=Metrics.definitions.map(([key,label,unit])=>`<option value="${key}">${label} (${unit})</option>`).join('');
    $('time-ratio-range').min='.25';$('time-ratio-range').max='8';$('time-ratio-range').step='.25';$('time-ratio-range').value='1';$('time-ratio-value').textContent='1';
    $('route-display-mode').options[0].textContent='曲线示意（非真实道路）';$('route-display-mode').options[1].textContent='原始端点连线';
    try {map=new AMap.Map('map',{zoom:5,center:[104.06,30.67]});}
    catch { $('map').textContent='地图加载失败，请检查网络后刷新。实验参数和结果仍可查看。'; }
    const message=error=>error.response?.data?.message || (error.isAxiosError?'回放数据读取失败，请确认后端服务已启动后重试':error.message) || '回放加载失败，请重试';
    const get=async path=>(await axios.get(base+path,{timeout:20000})).data;
    function pause() {clock?.pause();cancelAnimationFrame(animation);animation=null;$('replay-pause').disabled=true;$('replay-play').textContent='▶ 播放实验';}
    function clearMap() {markers.forEach(marker=>marker.setMap(null));markers.clear();lines.forEach(line=>line.overlay.setMap(null));lines.clear();poiLayer?.setMap(null);poiLayer=null;poiVisible=false;$('toggle-poi').textContent='显示 POI';$('toggle-poi').setAttribute('aria-pressed','false');}
    function finalResults(run) {
        $('replay-final-results').replaceChildren();
        Metrics.sandboxDefinitions.forEach(([key,label,unit])=>{
            const row=document.createElement('div');row.className='detail-row';const title=document.createElement('span'),value=document.createElement('strong');
            title.textContent=label;value.textContent=Metrics.format(run.metrics?.[key])+' '+unit;row.append(title,value);$('replay-final-results').append(row);
        });
        const config=run.config,enabled=Object.entries(config.events || {}).filter(([,on])=>on===true).map(([key])=>eventNames[key] || key);
        $('replay-config').textContent=`${run.runId}\n${algorithmNames[config.algorithm] || config.algorithm} · ${config.vehicleCount}辆车 · ${config.orderCount ?? run.orderCount}个订单 · 每${config.orderGenerationInterval}分钟生成一单 · seed ${config.randomSeed}。启用事件：${enabled.join('、') || '无'}。`;
        const weights=config.objectiveWeights;
        $('replay-weights').textContent=weights?'本Run冻结权重：'+[['waiting','等待'],['emptyDistance','空载'],['utilization','利用率'],['tonnage','吨位'],['carbon','排放']].map(([key,name])=>`${name} ${(weights[key]/Object.values(weights).reduce((a,b)=>a+Number(b),0)*100).toFixed(1)}%`).join(' · '):'';
        $('sandbox-link').href='sandbox.html?runId='+encodeURIComponent(run.runId);$('sandbox-link').textContent='返回该次沙箱实验 ↗';
    }
    function details(vehicle) {
        selectedVehicle=vehicle.id;$('detail-title').textContent='实验车辆 '+(vehicle.code || vehicle.id);$('detail-body').replaceChildren();
        for(const [label,value] of [['Run',payload.run.runId],['状态',vehicle.status],['当前载货 / 容量',`${vehicle.load} / ${vehicle.capacity} t`],['任务',vehicle.task?'任务'+vehicle.task.id+' · 订单'+vehicle.task.orderId:'暂无执行中的任务'],['坐标',vehicle.position?.map(n=>n.toFixed(5)).join(',') || '无']]) {
            const row=document.createElement('div');row.className='detail-row';const a=document.createElement('span'),b=document.createElement('strong');a.textContent=label;b.textContent=value;row.append(a,b);$('detail-body').append(row);
        }
        $('detail-panel').hidden=false;
    }
    function renderMap(frame) {
        if(!map)return;
        const active=new Set();let routeCount=0;
        frame.vehicles.forEach(vehicle=>{
            if(!vehicle.position)return;
            const color=colors[vehicle.type] || '#64748b';
            const content=`<div class="vehicle-marker${vehicle.affected?' event-affected':''}" style="background:${color}">${vehicle.id}</div>`;
            if(!markers.has(vehicle.id)) {
                const marker=new AMap.Marker({map,position:vehicle.position,anchor:'center',content});
                marker.on('click',()=>{const current=engine.frame(clock.current()).vehicles.find(v=>v.id===vehicle.id);details(current);});markers.set(vehicle.id,marker);
            }
            const marker=markers.get(vehicle.id);marker.setPosition(vehicle.position);
            if(marker.getContent()!==content)marker.setContent(content);
            if(vehicle.route && vehicle.route.path.length>1 && routeCount++<50) {
                active.add(vehicle.id);const key=vehicle.task.id+':'+vehicle.status,previous=lines.get(vehicle.id);
                if(previous?.key!==key){previous?.overlay.setMap(null);lines.set(vehicle.id,{key,overlay:new AMap.Polyline({map,path:vehicle.route.path,strokeColor:vehicle.load?'#148779':'#568ce1',strokeWeight:3,strokeOpacity:.55,strokeStyle:'dashed'})});}
            }
        });
        lines.forEach((line,id)=>{if(!active.has(id)){line.overlay.setMap(null);lines.delete(id);}});
    }
    function chart(frame) {
        const key=$('trend-select').value,rows=history.filter(row=>SandboxReplay.time(row.simulationTime)<=frame.time);
        rows.push({simulationTime:new Date(frame.time).toISOString(),...frame.metrics});
        $('trend').innerHTML=StatCharts.trend(rows,key).svg;
        $('trend-range').textContent='本Run时间轴 · 按已完成任务累计 · '+SandboxReplay.label(frame.time);
        $('trend-explanation').textContent='仅显示本次沙箱实验的回放数据。上方指标随进度变化，右侧展示本次实验的最终结果。';
    }
    function renderText(frame) {
        Metrics.definitions.forEach(([key])=>{$('metric-'+key).textContent=Metrics.format(frame.metrics[key]);});
        const percentage=engine.end===engine.start?100:(frame.time-engine.start)/(engine.end-engine.start)*100;
        $('replay-seek').value=Math.round(percentage*10);
        $('replay-time').textContent=`${SandboxReplay.label(frame.time)} · ${percentage.toFixed(1)}%\n起 ${SandboxReplay.label(engine.start)}\n止 ${SandboxReplay.label(engine.end)}`;
        $('runtime-status').textContent=`沙箱回放 · 订单完成 ${frame.completedOrders}/${frame.totalOrders} · 任务完成 ${frame.completedTasks}/${frame.totalTasks} · 生效事件 ${frame.activeEvents.length} · 仿真时间 ${SandboxReplay.label(frame.time)}`;
        $('cycle-status-text').textContent=clock.playing()?'回放中':frame.time>=engine.end?'回放结束':'回放已暂停';
        document.querySelector('.status-dot').className='status-dot '+(clock.playing()?'on':'off');
        $('cycle-stats').textContent=`任务 ${frame.completedTasks}/${frame.totalTasks}`;
        $('replay-pause').disabled=!clock.playing();$('replay-play').textContent=clock.playing()?'▶ 正在播放':'▶ 播放实验';$('replay-play').disabled=clock.playing();
        $('vehicle-count').textContent=frame.vehicles.length;$('idle-count').textContent=frame.vehicles.filter(v=>v.status==='空闲').length;
        $('order-count').textContent=frame.totalOrders;$('done-order-count').textContent=frame.completedOrders;$('task-count').textContent=frame.completedTasks;
        $('total-revenue').textContent=Metrics.format(frame.revenue);$('total-cost').textContent=Metrics.format(frame.cost);$('total-profit').textContent=Metrics.format(frame.profit);$('sim-time').textContent=SandboxReplay.label(frame.time);
        $('fleet-status').textContent=`本Run ${frame.vehicles.length}辆车 · 运输中 ${frame.vehicles.filter(v=>v.status!=='空闲').length}辆 · 已生成事件 ${engine.events.length}条`;
        frame.vehicles.forEach(v=>{const button=$('replay-vehicle-'+v.id);if(button)button.textContent=`${v.code || '车辆'+v.id} · ${v.status} · ${Metrics.format(v.load)}/${v.capacity} t`;});
        $('live-events').replaceChildren();
        const shown=engine.events.slice().sort((a,b)=>Math.abs(a.start-frame.time)-Math.abs(b.start-frame.time)).slice(0,10).sort((a,b)=>a.start-b.start);
        if(!shown.length)$('live-events').textContent='本次运行没有生成随机事件。';
        shown.forEach(event=>{
            const row=document.createElement('div');row.className='event-row';const title=document.createElement('strong'),text=document.createElement('small');
            const state=frame.time<event.start?'尚未发生':frame.time<event.end?'生效中':'已结束';
            title.textContent=`${eventNames[event.eventType] || event.eventType} · ${state}`;
            text.textContent=`${SandboxReplay.label(event.start)} · 持续${event.duration}分钟 · ${['SHORTAGE','CARGO_DAMAGE'].includes(event.eventType)?'订单 '+event.affectedOrderIds.join('、'):'车辆 '+event.affectedVehicleIds.join('、')+' · 额外延误'+event.extraDelay+'分钟'}`;
            row.append(title,text);$('live-events').append(row);
        });
        $('order-list').textContent=`已到达 ${frame.arrivedOrders}/${frame.totalOrders}，已全部送达 ${frame.completedOrders} 个订单。`;
        if(selectedVehicle!=null)details(frame.vehicles.find(v=>v.id===selectedVehicle));
        chart(frame);
    }
    function render(force=false) {if(!engine)return;const frame=engine.frame(clock.current());renderMap(frame);const now=performance.now();if(force||now-lastPaint>200){lastPaint=now;renderText(frame);}}
    function tick(now) {if(!clock?.playing())return;clock.advance(now-lastTick);lastTick=now;render();if(clock.playing())animation=requestAnimationFrame(tick);else{pause();render(true);}}
    async function loadRun(id) {
        const version=++loadVersion;pause();engine=null;clock=null;payload=null;selectedVehicle=null;clearMap();$('detail-panel').hidden=true;
        ['replay-play','replay-pause','replay-reset','replay-seek','toggle-poi'].forEach(key=>{$(key).disabled=true;});
        $('car-list').replaceChildren();$('live-events').replaceChildren();$('replay-message').textContent='正在读取本次实验的冻结轨迹…';
        $('replay-final-results').replaceChildren();$('replay-config').textContent='';$('replay-weights').textContent='';
        $('fleet-status').textContent='等待本次实验数据';$('order-list').textContent='';
        Metrics.definitions.forEach(([key])=>{$('metric-'+key).textContent='—';});
        $('replay-time').textContent='';$('replay-seek').value=0;$('trend').innerHTML='';$('trend-range').textContent='';$('trend-explanation').textContent='';
        $('cycle-status-text').textContent='正在读取实验';$('cycle-stats').textContent='';$('runtime-status').textContent='正在读取本次实验…';
        $('data-health').textContent='正在读取沙箱实验数据…';
        window.history.replaceState(null,'','cycle.html?mode=replay'+(id?'&runId='+encodeURIComponent(id):''));
        if(!id){$('replay-message').textContent='请先选择一个已完成的实验；没有记录时可进入实验沙箱新建。';$('data-health').textContent='等待选择沙箱实验';$('cycle-status-text').textContent='尚未选择实验';$('runtime-status').textContent='等待选择实验';return;}
        try {
            let run=catalog.runs.find(r=>r.runId===id);
            if(!run)run=await get('/sandbox/runs/'+encodeURIComponent(id));
            if(version!==loadVersion)return;
            finalResults(run);
            if(!run.playbackAvailable) {
                $('replay-message').textContent='这条历史运行没有保存任务轨迹。请点击右上角返回该次沙箱实验，再“重跑选中记录”，完成后播放新记录。';
                $('data-health').textContent='已读取历史参数与结果 · 尚无回放轨迹';$('runtime-status').textContent='历史记录暂无轨迹，请重跑生成。';$('cycle-status-text').textContent='无回放轨迹';
                Metrics.definitions.forEach(([key])=>{$('metric-'+key).textContent='—';});return;
            }
            const loaded=await get('/sandbox/runs/'+encodeURIComponent(id)+'/playback');if(version!==loadVersion)return;
            payload=loaded;engine=SandboxReplay.create(payload.playback,payload.run.events || []);engine.routes($('route-display-mode').value);
            clock=SandboxReplay.clock(engine.start,engine.end);clock.speed(Number($('time-ratio-range').value));history=engine.history();
            $('replay-message').textContent='已加载本Run轨迹。车辆按保存的任务时间移动；虚线为端点曲线示意，并非实际道路。最多同时显示50条活动路线。';
            $('data-health').textContent=map?'沙箱回放已就绪 · 实验数据与实时场景独立':'轨迹已加载 · 地图加载失败，请检查网络后刷新';
            for(const v of payload.playback.vehicles){const button=document.createElement('button');button.className='car-item';button.id='replay-vehicle-'+v.id;button.type='button';button.onclick=()=>details(engine.frame(clock.current()).vehicles.find(current=>current.id===v.id));$('car-list').append(button);}
            ['replay-play','replay-reset','replay-seek','toggle-poi'].forEach(key=>{$(key).disabled=false;});
            render(true);if(map && markers.size)map.setFitView([...markers.values()]);
        } catch(error) {if(version===loadVersion){$('replay-message').textContent=message(error);$('data-health').textContent='回放加载失败，请重新选择实验或刷新';$('cycle-status-text').textContent='加载失败';$('runtime-status').textContent='回放加载失败，请重新选择实验或刷新';}}
    }
    $('replay-play').onclick=()=>{if(!clock)return;clock.play();lastTick=performance.now();render(true);animation=requestAnimationFrame(tick);};
    $('replay-pause').onclick=()=>{pause();render(true);};
    $('replay-reset').onclick=()=>{pause();clock?.seek(engine.start);render(true);};
    $('replay-seek').oninput=()=>{if(clock){clock.seek(engine.start+(engine.end-engine.start)*Number($('replay-seek').value)/1000);render(true);}};
    $('time-ratio-range').oninput=()=>{$('time-ratio-value').textContent=$('time-ratio-range').value;clock?.speed(Number($('time-ratio-range').value));};
    $('route-display-mode').onchange=()=>{lines.forEach(line=>line.overlay.setMap(null));lines.clear();engine?.routes($('route-display-mode').value);render(true);};
    $('trend-select').onchange=()=>render(true);
    $('close-detail').onclick=()=>{selectedVehicle=null;$('detail-panel').hidden=true;};
    $('fit-vehicles').onclick=()=>{if(map && markers.size)map.setFitView([...markers.values()]);};
    $('toggle-poi').onclick=()=>{
        if(!map||!payload)return;
        try {
            if(!poiLayer){const points=payload.pois.map(p=>({lnglat:RouteGeometry.parse(p.location)[0],name:p.name || p.location_name || String(p.id)})).filter(p=>p.lnglat);poiLayer=new AMap.MassMarks(points,{zIndex:60,style:{url:'./icons/A/170300.svg',anchor:new AMap.Pixel(12,12),size:new AMap.Size(24,24)}});}
            poiVisible=!poiVisible;if(poiVisible){poiLayer.setMap(map);poiLayer.show();}else poiLayer.hide();
            $('toggle-poi').textContent=poiVisible?'隐藏 POI':'显示 POI';$('toggle-poi').setAttribute('aria-pressed',String(poiVisible));
        }catch{$('replay-message').textContent='POI 显示失败，请重试；车辆回放可以继续。';}
    };
    $('replay-run-select').onchange=()=>loadRun($('replay-run-select').value);
    window.addEventListener('pagehide',pause);document.addEventListener('visibilitychange',()=>{if(document.hidden){pause();render(true);}});
    try {
        catalog=await get('/sandbox/state');const complete=catalog.runs.filter(r=>r.status==='COMPLETED').sort((a,b)=>b.startedAt-a.startedAt);
        $('replay-run-select').replaceChildren(new Option('请选择已完成实验',''));
        complete.forEach(run=>{const name=catalog.experiments.find(e=>e.experimentId===run.experimentId)?.name || '历史方案';$('replay-run-select').add(new Option(`${name} · ${algorithmNames[run.config.algorithm]} · ${run.runId.slice(0,12)}${run.playbackAvailable?'':'（无轨迹）'}`,run.runId));});
        const selected=query.get('runId') || complete.find(r=>r.playbackAvailable)?.runId || complete[0]?.runId;
        if(selected){if(!complete.some(r=>r.runId===selected))$('replay-run-select').add(new Option(selected,selected));$('replay-run-select').value=selected;await loadRun(selected);}
        else await loadRun('');
    }catch(error){$('data-health').textContent='实验列表读取失败';$('replay-message').textContent=message(error);}
})();
