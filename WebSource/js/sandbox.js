(async () => {
    const $ = id => document.getElementById(id), service = SandboxService;
    let data = {experiments:[],runs:[]}, catalog, selected, selectedRun, timer, stopped = false, acting = false;
    let comparisonFollowing = true, followLatestRun = true, refreshVersion = 0, formVersion;
    const experiment = () => data.experiments.find(e => e.experimentId === selected);
    const labels = {QUEUED:'排队中',RUNNING:'运行中',COMPLETED:'已完成',FAILED:'执行失败'};
    const weightKeys = service.objectiveDefinitions.map(([key])=>key);
    function readWeights() { return Object.fromEntries(weightKeys.map(key=>[key,$('weight-'+key).value === '' ? NaN : Number($('weight-'+key).value)])); }
    function showWeights(weights) {
        const values = weights || catalog.objectiveDefaults;
        weightKeys.forEach(key=>{ $('weight-'+key).value = values[key]; }); updateWeights();
    }
    function updateWeights() {
        const weighted = $('algorithm-select').value === 'WEIGHTED';
        $('algorithm').classList.toggle('weighted-active',weighted);
        $('objective-settings').hidden = !weighted; $('objective-settings').disabled = !weighted;
        if (!weighted) return;
        try {
            const normalized = service.normalizedWeights(readWeights());
            $('weight-summary').textContent = service.objectiveDefinitions.map(([key,label])=>`${label} ${(normalized[key]*100).toFixed(1)}%`).join(' · ');
        } catch(e) { $('weight-summary').textContent = e.message; }
    }
    async function act(action) {
        if (acting) return;
        ++refreshVersion;
        acting = true; updateStatus(); $('sandbox-error').textContent = '';
        try { await action(); } catch(e) { $('sandbox-error').textContent = e.message; }
        finally { acting = false; updateStatus(); }
    }
    function fill() {
        const e = experiment();
        if (!e) {
            formVersion = undefined;
            ['experiment-name','vehicle-count-input','order-count-input','order-interval','seed'].forEach(id => { $(id).value = ''; });
            $('poi-count').textContent = '请选择或新建实验';
            return;
        }
        const c = e.config;
        formVersion = e.version;
        $('experiment-name').value = e.name; $('vehicle-count-input').value = c.vehicleCount;
        $('order-interval').value = c.orderGenerationInterval; $('seed').value = c.randomSeed; $('algorithm-select').value = c.algorithm;
        $('order-count-input').value = c.orderCount ?? Math.ceil(1440/c.orderGenerationInterval);
        $('poi-count').textContent = c.poiCount;
        showWeights(c.objectiveWeights);
        document.querySelectorAll('[data-event]').forEach(input => { input.checked = !!c.events[input.dataset.event]; });
    }
    function read() {
        const c = service.clone(experiment().config);
        c.vehicleCount = Number($('vehicle-count-input').value); c.orderGenerationInterval = Number($('order-interval').value);
        c.simulationDuration = 0; c.orderCount = Number($('order-count-input').value);
        c.randomSeed = $('seed').value === '' ? NaN : Number($('seed').value); c.algorithm = $('algorithm-select').value;
        c.objectiveWeights = c.algorithm === 'WEIGHTED' ? readWeights() : null;
        document.querySelectorAll('[data-event]').forEach(input => { c.events[input.dataset.event] = input.checked; });
        service.validate(c); return c;
    }
    async function saveConfig() {
        const name = $('experiment-name').value.trim(); if (!name) throw new Error('请输入方案名称');
        const updated = await service.save({...experiment(),version:formVersion,name,config:read()});
        formVersion = updated.version;
        data.experiments = data.experiments.map(e => e.experimentId === updated.experimentId ? updated : e); renderList();
    }
    function renderList() {
        $('experiments').replaceChildren();
        if (!data.experiments.length) {
            const empty = document.createElement('p'); empty.className = 'muted'; empty.textContent = '暂无实验方案，请点击“新建实验”。';
            $('experiments').append(empty);
        }
        data.experiments.forEach(e => {
            const button = document.createElement('button'); button.className = 'experiment' + (e.experimentId === selected ? ' active' : '');
            button.textContent = `${e.name} · ${data.runs.filter(r => r.experimentId === e.experimentId).length} Runs`;
            button.onclick = () => { if (acting) return; selected = e.experimentId; followLatestRun = true; comparisonFollowing = true; selectedRun = data.runs.filter(r => r.experimentId === selected).at(-1)?.runId; fill(); renderList(); renderRuns(); };
            $('experiments').append(button);
        });
    }
    function renderRuns() {
        $('run-history').replaceChildren();
        data.runs.filter(r => r.experimentId === selected).slice().reverse().forEach(r => {
            const button = document.createElement('button'); button.className = 'experiment' + (r.runId === selectedRun ? ' active' : '');
            button.textContent = `${r.runId.slice(0,12)} · ${service.algorithmLabel(r.config.algorithm)} · ${labels[r.status] || r.status} · seed ${r.config.randomSeed}`;
            button.title = r.runId; button.onclick = () => { if (acting) return; selectedRun = r.runId; followLatestRun = false; comparisonFollowing = true; renderRuns(); }; $('run-history').append(button);
        });
        updateStatus(); $('run-events').replaceChildren();
        const run = data.runs.find(r => r.runId === selectedRun);
        if (!run?.events?.length) $('run-events').textContent = '当前 Run 没有随机事件';
        (run?.events || []).forEach(event => {
            const button = document.createElement('button'); button.className = 'experiment'; button.textContent = catalog.events[event.eventType];
            button.onclick = () => {
                $('event-title').textContent = catalog.events[event.eventType];
                $('event-detail').textContent = `事件 ${event.eventId}\n开始时间 ${event.startTime}\n持续时间 ${event.duration} min\n额外延误 ${event.extraDelay} min\n影响车辆 ${event.affectedVehicleIds.join('、')}\n影响订单 ${event.affectedOrderIds.join('、')}\n由后端按随机种子生成并作用于调度过程。`;
                $('event-dialog').showModal();
            }; $('run-events').append(button);
        });
        const complete = data.runs.filter(r => r.status === 'COMPLETED');
        const pair = service.comparisonPair(data.runs, selectedRun);
        ['baseline-run','candidate-run'].forEach((id,index) => {
            const select = $(id), previous = select.value; select.replaceChildren(new Option('请选择已完成 Run',''));
            const options = complete.slice().reverse();
            if(index === 1 && run && run.status !== 'COMPLETED') options.unshift(run);
            options.forEach(r => select.add(new Option(`${data.experiments.find(e => e.experimentId === r.experimentId)?.name} / ${r.runId.slice(0,12)} · ${service.algorithmLabel(r.config.algorithm)} · seed ${r.config.randomSeed} · ${labels[r.status] || r.status}`,r.runId)));
            select.value = comparisonFollowing ? (index === 0 ? pair.baseline : pair.candidate) : (options.some(r => r.runId === previous) ? previous : '');
            select.title = select.selectedOptions[0]?.textContent || '';
        }); compare();
    }
    function updateStatus() {
        const run = data.runs.find(r => r.runId === selectedRun);
        $('start-run').disabled = acting || !experiment(); $('rerun').disabled = acting || !run || run.config.simulationDuration !== 0 || !['COMPLETED','FAILED'].includes(run.status);
        $('rerun').title = run && run.config.simulationDuration !== 0 ? '历史24小时口径；请点击开始实验生成全订单完成结果' : '按选中记录的冻结参数复现';
        $('new-experiment').disabled = acting;
        ['copy-experiment','save-config'].forEach(id => { $(id).disabled = acting || !experiment(); });
        const activeRuns = data.runs.some(r => r.experimentId === selected && ['QUEUED','RUNNING'].includes(r.status));
        $('delete-experiment').disabled = acting || !experiment() || activeRuns;
        $('delete-experiment').title = activeRuns ? '请等待排队或运行中的实验结束后再删除' : '删除当前方案及其全部运行历史和结果';
        $('config-form').hidden = !experiment(); $('experiment-name').disabled = acting || !experiment();
        $('run-baseline').disabled = acting || !run || !['COMPLETED','FAILED'].includes(run.status) || run.config.simulationDuration !== 0 || run.config.algorithm === 'BASELINE';
        $('play-run').disabled = acting || !run || run.status !== 'COMPLETED' || !run.playbackAvailable;
        $('run-playback-hint').textContent = !run ? '' : run.status === 'COMPLETED'
            ? run.playbackAvailable ? '已保存本次实验轨迹，可点击“播放本次实验”查看对应车辆动画。' : '此历史记录没有保存轨迹；点击“重跑选中记录”，完成后可播放新运行的动画。'
            : '实验完成后可播放本次运行的车辆动画。';
        $('run-state').textContent = run ? `${labels[run.status]} · 后端真实执行${run.error ? '：' + run.error : ''}` : '尚未运行';
        $('run-identity').textContent = run ? `${run.experimentId} / ${run.runId}` : '';
        $('run-progress').value = run?.progress || 0; $('run-percent').textContent = `${run?.progress || 0}%`;
        $('run-time').textContent = run ? `仿真时间 ${run.simulationTime} · seed ${run.config.randomSeed} · 完成任务 ${run.completedTaskCount ?? '—'}/${run.taskCount ?? 0}` : '';
        $('completion-summary').textContent = !run ? '按订单完成比例显示进度；100%表示全部订单运输完成。' : run.config.simulationDuration !== 0 ? '历史运行：24小时截止口径；重新开始实验将运行至全部订单完成。' : `订单完成 ${run.completedOrderCount ?? 0}/${run.orderCount ?? run.config.orderCount} · 总完成时长 ${Metrics.format(run.metrics?.totalCompletionTimeH)} h · 平均订单时长 ${Metrics.format(run.metrics?.averageOrderCompletionTimeH)} h`;
        $('run-event-settings').hidden = !run;
        $('run-event-settings').textContent = '';
        if (run) {
            const enabledEvents = Object.entries(run.config.events || {}).filter(([,enabled]) => enabled === true)
                .map(([type]) => catalog.events[type] || type);
            $('run-event-settings').textContent = run.config.events == null ? '本Run事件配置：未记录'
                : '本Run启用随机事件：' + (enabledEvents.length ? enabledEvents.join('、') : '无（全部关闭）');
            $('run-event-settings').textContent += run.status === 'COMPLETED'
                ? `；实际生成 ${run.events?.length ?? 0} 条，详情见下方“随机事件”。`
                : '。启用的事件按概率生成，不保证一定发生。';
        }
        $('run-objectives').textContent = '';
        if (run?.config.algorithm === 'WEIGHTED') {
            const normalized = service.normalizedWeights(run.config.objectiveWeights);
            $('run-objectives').textContent = '本Run冻结权重：'+service.objectiveDefinitions.map(([key,label])=>`${label} ${(normalized[key]*100).toFixed(1)}%`).join(' · ')
                + (run.optimization?.decisionCount ? `。已执行 ${run.optimization.decisionCount} 次加权选择，评估 ${run.optimization.candidateCount} 个候选；JSON含前20次评分样本。` : '。完成后可导出评分样本。');
        }
    }
    function compare() {
        const baseline = data.runs.find(r => r.runId === $('baseline-run').value), candidate = data.runs.find(r => r.runId === $('candidate-run').value);
        const a = baseline?.status === 'COMPLETED' ? baseline : null, b = candidate?.status === 'COMPLETED' ? candidate : null;
        const control = service.comparisonControl;
        const comparable=a && b && a.runId!==b.runId && control(a) && control(a)===control(b);
        $('comparison-follow-state').textContent = comparisonFollowing ? '跟随当前运行' : '手动选择历史记录';
        $('comparison-note').textContent = candidate && candidate.status !== 'COMPLETED'
            ? `当前候选 ${candidate.runId.slice(0,12)} ${labels[candidate.status] || candidate.status}${candidate.error ? '：'+candidate.error : '，完成后自动更新'}。尚无本次结果，未使用旧结果代替。`
            : !b ? '当前方案尚无运行结果；开始实验后这里会自动更新。'
            : !a ? '已显示当前运行结果，未找到同场景、种子、参数和模型的基准记录；可手动选择基准，或复制方案以相同参数执行基准算法。'
            : a.runId === b.runId ? '当前选择了同一个 Run，请选择另一次运行进行比较。'
            : !control(a) || !control(b) ? '历史记录缺少场景或模型标识，无法验证同一计价版本，请使用新的 Run 比较。'
            : control(a) !== control(b) ? '模型或控制条件不一致，差异不能直接归因于算法。'
            : '控制条件一致：两次运行使用相同场景、种子、模型和计价口径。';
        if(comparable && [a,b].some(r=>r.config.algorithm==='WEIGHTED')) $('comparison-note').textContent += ' 加权方案体现算法与权重的取舍，各项指标可能有升有降；归一化评分仅用于本轮候选选择。';
        if(a?.metrics && b?.metrics && comparable && JSON.stringify(service.canonical(a.metrics)) === JSON.stringify(service.canonical(b.metrics))) $('comparison-note').textContent += ' 两次结果相同：相同配置和种子的重跑可复现；不同算法也可能在当前场景选择相同方案。';
        $('comparison-body').replaceChildren();
        $('comparison-charts').innerHTML=StatCharts.comparison(a,b,Metrics.sandboxDefinitions);
        $('export-run').disabled=!data.runs.find(r=>r.runId===selectedRun && r.status==='COMPLETED');
        $('export-comparison').disabled=!a || !b;
        Metrics.sandboxDefinitions.forEach(([key,label,unit,higher]) => {
            const value = comparable ? Metrics.improvement(a?.metrics?.[key],b?.metrics?.[key],higher) : null, row = document.createElement('tr');
            [label+' / '+unit,Metrics.format(a?.metrics?.[key]),Metrics.format(b?.metrics?.[key]),!comparable?'条件未满足，不能归因于算法':value === null ? '不可计算（缺失或基准为0）' : `${value > 0 ? '改善' : value < 0 ? '退化' : '持平'} ${Metrics.format(Math.abs(value))}%`].forEach((text,i) => { const td = document.createElement('td'); td.textContent = text; if (i===3 && value) td.className = value > 0 ? 'good' : 'bad'; row.append(td); });
            $('comparison-body').append(row);
        });
    }
    async function refresh() {
        const version = ++refreshVersion, next = await service.load();
        if(version !== refreshVersion || stopped) return;
        data = next; data.runs.reverse().sort((a,b) => (Number(a.startedAt) || 0) - (Number(b.startedAt) || 0));
        if (!selected || !experiment()) {
            selected = data.experiments[0]?.experimentId; selectedRun = undefined;
            followLatestRun = true; comparisonFollowing = true; fill();
        }
        if (followLatestRun || !selectedRun) selectedRun = data.runs.filter(r => r.experimentId === selected).at(-1)?.runId;
        renderList(); renderRuns();
    }
    $('config-form').onsubmit = event => { event.preventDefault(); act(async () => { await saveConfig(); const run = await service.createRun(selected); selectedRun = run.runId; comparisonFollowing = true; followLatestRun = true; await refresh(); }); };
    $('save-config').onclick = () => act(saveConfig);
    $('generate-seed').onclick = () => { $('seed').value = service.seed(); };
    $('rerun').onclick = () => act(async () => { const run = await service.createRun(selected,selectedRun); selectedRun = run.runId; comparisonFollowing = true; followLatestRun = true; await refresh(); });
    $('run-baseline').onclick = () => act(async () => {
        const source = await service.run(selectedRun), config = service.clone(source.config);
        config.algorithm = 'BASELINE'; config.objectiveWeights = null;
        const created = await service.create((experiment().name.slice(0,48)+' 同条件基准'),config,source.experimentId);
        await service.createRun(created.experimentId);
        // Keep the weighted result selected; automatic matching picks up the baseline when it completes.
        followLatestRun = false; comparisonFollowing = true; await refresh();
    });
    async function add(copy) {
        if (copy) await saveConfig();
        const e = await service.create(copy ? experiment().name.slice(0,55)+' 副本' : `实验方案 ${data.experiments.length+1}`,copy ? experiment().config : catalog.defaults,copy ? selected : undefined);
        selected = e.experimentId; selectedRun = null; comparisonFollowing = true; followLatestRun = true; await refresh(); fill();
    }
    $('new-experiment').onclick = () => act(() => add(false)); $('copy-experiment').onclick = () => act(() => add(true));
    $('delete-experiment').onclick = () => act(async () => {
        const target = experiment(); if (!target) return;
        if (!window.confirm(`确定删除实验方案“${target.name}”？\n该方案的全部运行历史、结果和事件将一并永久删除，无法恢复。`)) return;
        const index = data.experiments.findIndex(e => e.experimentId === target.experimentId);
        await service.remove(target.experimentId,formVersion);
        data.experiments = data.experiments.filter(e => e.experimentId !== target.experimentId);
        data.runs = data.runs.filter(r => r.experimentId !== target.experimentId);
        selected = data.experiments[Math.min(index,data.experiments.length-1)]?.experimentId;
        selectedRun = undefined; followLatestRun = true; comparisonFollowing = true;
        fill(); renderList(); renderRuns();
        await refresh(); fill();
    });
    $('compare-focus').onclick = () => { $('comparison').scrollIntoView({behavior:'smooth'}); $('baseline-run').focus(); };
    $('play-run').onclick = () => {
        const run = data.runs.find(r => r.runId === selectedRun);
        if (!acting && run?.status === 'COMPLETED' && run.playbackAvailable) window.location.href = 'cycle.html?mode=replay&runId=' + encodeURIComponent(run.runId);
    };
    $('baseline-run').onchange = $('candidate-run').onchange = () => { comparisonFollowing = false; compare(); };
    $('follow-comparison').onclick = () => { comparisonFollowing = true; renderRuns(); };
    $('close-event').onclick = () => $('event-dialog').close();
    function download(name,body,type) {
        const url=URL.createObjectURL(new Blob([body],{type})),link=document.createElement('a');
        link.href=url;link.download=name;document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
    }
    $('export-run').onclick=()=>act(async()=>{
        const run=await service.run(selectedRun);download(run.runId+'.json',JSON.stringify(run,null,2),'application/json');
    });
    $('export-comparison').onclick=()=>act(async()=>{
        const [a,b]=await Promise.all([service.run($('baseline-run').value),service.run($('candidate-run').value)]);
        const rows=[['指标','单位','Baseline','Candidate'],['Run ID','',a.runId,b.runId],['算法','',a.config.algorithm,b.config.algorithm],['种子','',a.config.randomSeed,b.config.randomSeed],['环境快照','',a.environmentId,b.environmentId],['模型指纹','',a.modelId||'未提供',b.modelId||'未提供'],['冻结配置','',JSON.stringify(a.config),JSON.stringify(b.config)],['目标权重','',JSON.stringify(a.config.objectiveWeights||null),JSON.stringify(b.config.objectiveWeights||null)],['比较说明','',$('comparison-note').textContent,'']];
        Metrics.sandboxDefinitions.forEach(([key,label,unit])=>rows.push([label,unit,a.metrics[key],b.metrics[key]]));
        const cell=value=>'"'+String(value??'').replace(/^[=+@-]/,"'$&").replace(/"/g,'""')+'"';
        download('调度实验比较.csv','\uFEFF'+rows.map(row=>row.map(cell).join(',')).join('\r\n'),'text/csv;charset=utf-8');
    });
    document.querySelectorAll('[data-tab]').forEach(button => { button.onclick = () => {
        document.querySelectorAll('[data-tab]').forEach(b => { b.setAttribute('aria-selected',String(b===button)); $(b.dataset.tab).hidden = b !== button; });
    }; });
    try {
        catalog = await service.catalog();
        catalog.algorithms.forEach(a => $('algorithm-select').add(new Option(a.name,a.id)));
        service.objectiveDefinitions.forEach(([key,label])=>{
            const field = document.createElement('label'); field.className = 'field';
            const span = document.createElement('span'); span.textContent = label;
            const input = document.createElement('input'); input.id = 'weight-'+key; input.type = 'number';
            input.min = '0'; input.max = '1000000'; input.step = 'any'; input.required = true; input.oninput = updateWeights;
            field.append(span,input); $('objective-inputs').append(field);
        });
        $('algorithm-select').onchange = updateWeights;
        $('reset-weights').onclick = () => showWeights(catalog.objectiveDefaults);
        Object.entries(catalog.events).forEach(([type,name]) => {
            const label = document.createElement('label'); label.className = 'field'; const span = document.createElement('span'); span.textContent = name;
            const input = document.createElement('input'); input.type = 'checkbox'; input.dataset.event = type; label.append(span,input); $('event-options').append(label);
        });
        await refresh();
        const requestedRunId = new URLSearchParams(window.location.search).get('runId');
        const requestedRun = data.runs.find(r => r.runId === requestedRunId);
        if (requestedRun) {
            selected = requestedRun.experimentId; selectedRun = requestedRun.runId;
            followLatestRun = false; comparisonFollowing = true; renderList(); renderRuns();
        }
        fill();
    } catch(e) { $('sandbox-error').textContent = e.message; updateStatus(); }
    async function poll() {
        if (stopped) return;
        if (!acting && catalog) try { await refresh(); } catch(e) { $('sandbox-error').textContent = e.message; }
        timer = setTimeout(poll,2000);
    }
    timer = setTimeout(poll,2000); window.addEventListener('pagehide',() => { stopped = true; clearTimeout(timer); });
    window.addEventListener('pageshow',e=>{if(e.persisted){stopped=false;clearTimeout(timer);poll();}});
})();
