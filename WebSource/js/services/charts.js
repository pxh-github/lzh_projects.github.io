window.StatCharts = (() => {
    const escape = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const guidance = {
        totalWaitTime: '已完成任务的车辆等待总分钟。累计值上升不等于效率变差；结合新增完成吨位和相同场景的算法对比判断。',
        totalEmptyDistance: '车辆去取货的累计空载公里，实时页包含已分配路线。比较同等运输需求时，空载越少通常越好。',
        vehicleUtilization: '载重里程 ÷（载重里程＋空载里程）×100%。这是里程利用率，不代表车辆装满比例；没有里程时记为0。',
        completedTonnage: '已完成运输的吨位，运输中任务不提前计入。趋势台阶表示任务陆续完成；沙箱货损事件会减少达成重量。',
        totalCarbonEmission: '已完成任务按里程与车辆油耗估算的kg CO₂，系数为2.68 kg/L。比较同等吨位时再判断排放高低，不是传感器实测。'
    };
    function trend(rows, key) {
        const points = rows.filter(p => Metrics.number(p[key]) !== null);
        if (!points.length) return {svg:'<text x="60" y="80">暂无数据库采样</text>',note:'开始调度后由后端计算并保存采样。'};
        let times = points.map(p => Date.parse(p.simulationTime)), axis = '仿真时间';
        if(times.some(t => !Number.isFinite(t)) || Math.max(...times) === Math.min(...times)) { times=points.map(p=>Date.parse(p.capturedAt));axis='采样时间'; }
        if(times.some(t=>!Number.isFinite(t)) || Math.max(...times)===Math.min(...times)) times=points.map((p,i)=>i);
        const min=Math.min(...times), span=Math.max(...times)-min, top=Math.max(1,...points.map(p=>Number(p[key])));
        const coords=points.map((p,i)=>[60+(span ? (times[i]-min)/span : 0)*700,120-Number(p[key])/top*95]);
        const path=coords.map(p=>p.join(',')).join(' '), last=coords.at(-1);
        const title=Metrics.definitions.find(d=>d[0]===key);
        const label=p=>axis==='仿真时间'?p.simulationTime:p.capturedAt || '采样序号';
        return {svg:`<title>${escape(title[1])}，${escape(title[2])}</title><path d="M60 20V120H770" fill="none" stroke="#d5dfeb"/><text x="4" y="26">${escape(Metrics.format(top))}</text><text x="38" y="123">0</text>${coords.length>1?`<polyline points="${path}" fill="none" stroke="#2563eb" stroke-width="3"/>`:''}<circle cx="${last[0]}" cy="${last[1]}" r="4" fill="#2563eb"/><text x="60" y="145">${escape(label(points[0]))}</text><text x="770" y="162" text-anchor="end">${escape(label(points.at(-1)))}</text>`,
            note:`${axis}为横轴；纵轴单位 ${title[2]}。${points.length===1?'仅1个采样，尚不能判断趋势。':`最近${points.length}个数据库采样；时间间隔按实际间隔显示。`}`};
    }
    function comparison(a,b,definitions=Metrics.definitions) {
        return definitions.map(([key,label,unit,higher])=>{
            const baseline=Metrics.number(a?.metrics?.[key]), candidate=Metrics.number(b?.metrics?.[key]);
            const max=Math.max(1,baseline||0,candidate||0), width=v=>v===null?0:Math.max(0,v/max*230);
            const note=key==='totalCompletionTimeH'?'从仿真开始到最后一个订单完成，包含订单陆续到达的时间。':key==='averageOrderCompletionTimeH'?'每个订单从生成到全部分批货物送达的耗时，再按订单平均。':'每项指标独立刻度，条长只比较本项的两个数值。';
            return `<article class="comparison-chart"><h4>${escape(label)} <small>${escape(unit)} · ${higher?'通常越高越好':'同等需求下越低越好'}</small></h4><svg viewBox="0 0 360 90" role="img" aria-label="${escape(label)}基准与候选值比较"><text x="0" y="25">Baseline</text><rect x="85" y="10" width="${width(baseline)}" height="20" fill="#94a3b8"/><text x="85" y="43">${escape(Metrics.format(baseline))} ${escape(unit)}</text><text x="0" y="65">Candidate</text><rect x="85" y="50" width="${width(candidate)}" height="20" fill="#2563eb"/><text x="85" y="83">${escape(Metrics.format(candidate))} ${escape(unit)}</text></svg><p>${note}</p></article>`;
        }).join('');
    }
    return {guidance,trend,comparison};
})();
