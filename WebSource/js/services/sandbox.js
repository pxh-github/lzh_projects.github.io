window.SandboxService = (() => {
    const client = axios.create({ baseURL: window.SIMULATION_API_BASE || window.location?.origin || 'http://localhost:9091', timeout: 15000 });
    const clone = value => JSON.parse(JSON.stringify(value));
    const seed = () => crypto.getRandomValues(new Uint32Array(1))[0];
    const pendingStarts = new Map();
    const objectiveDefinitions = [
        ['waiting','排队等待',.25],['emptyDistance','空载里程',.25],['utilization','载重里程利用率',.20],
        ['tonnage','本轮运输吨位',.15],['carbon','估算碳排放',.15]
    ];
    const algorithmLabel = id => ({BASELINE:'基准调度',OPTIMIZED:'收益优化',WEIGHTED:'加权多目标'})[id] || id;
    function normalizedWeights(weights) {
        const values = objectiveDefinitions.map(([key]) => weights?.[key]);
        if (values.some(v => typeof v !== 'number' || !Number.isFinite(v) || v < 0 || v > 1000000) || values.reduce((a,b)=>a+b,0) <= 0)
            throw new Error('五项权重须为0～1000000的数字，且至少一项大于0');
        const total = values.reduce((a,b)=>a+b,0);
        return Object.fromEntries(objectiveDefinitions.map(([key],i)=>[key,values[i]/total]));
    }
    async function request(method, path, data) {
        try { return (await client.request({ method, url: path, data })).data; }
        catch (error) {
            const failure = new Error(error.response?.data?.message || '后端连接失败，请检查服务是否启动');
            failure.httpStatus = error.response?.status; throw failure;
        }
    }
    function validate(config) {
        if (!['BASELINE','OPTIMIZED','WEIGHTED'].includes(config.algorithm)) throw new Error('无效算法');
        if (config.algorithm === 'WEIGHTED') normalizedWeights(config.objectiveWeights);
        if (!Number.isInteger(config.randomSeed) || config.randomSeed < 0 || config.randomSeed > 4294967295) throw new Error('随机种子必须为 0～4294967295 的整数');
        if (!Number.isInteger(config.vehicleCount) || config.vehicleCount < 1 || config.vehicleCount > 500) throw new Error('车辆数应为 1～500 的整数');
        if (!Number.isInteger(config.orderGenerationInterval) || config.orderGenerationInterval < 1 || config.orderGenerationInterval > 1440) throw new Error('订单周期应为 1～1440 分钟的整数');
        if(config.simulationDuration === 0 && (!Number.isInteger(config.orderCount) || config.orderCount < 1 || config.orderCount > 1000)) throw new Error('订单数量应为1～1000的整数');
    }
    const canonical = value => value && typeof value === 'object' ? (Array.isArray(value) ? value.map(canonical) : Object.fromEntries(Object.keys(value).sort().map(k => [k,canonical(value[k])]))) : value;
    function comparisonControl(run) {
        if (!run?.modelId || !run.environmentId || !run.config) return null;
        const config = clone(run.config); delete config.algorithm;
        // Weights express algorithm preferences, not the generated environment or demand.
        delete config.objectiveWeights;
        if (config.events) config.events = Object.fromEntries(Object.entries(config.events).filter(([,v])=>v === true));
        if(config.simulationDuration === 24 && config.orderCount == null) delete config.orderCount;
        return JSON.stringify(canonical({environment:run.environmentId,model:run.modelId,config}));
    }
    // runs are chronological. Never substitute an old completed result for a pending/failed selection.
    function comparisonPair(runs, selectedRun) {
        const candidate = runs.find(r => r.runId === selectedRun);
        if (!candidate) return {baseline:'',candidate:''};
        const control = comparisonControl(candidate);
        const matches = control ? runs.filter(r => r.status === 'COMPLETED' && r.runId !== candidate.runId && comparisonControl(r) === control) : [];
        const baseline = matches.filter(r => r.config.algorithm === 'BASELINE').at(-1) || matches.at(-1);
        return {baseline:baseline?.runId || '',candidate:candidate.runId};
    }
    async function createRun(experimentId, rerunOf) {
        const operation = JSON.stringify([experimentId,rerunOf || null]);
        if (!pendingStarts.has(operation)) pendingStarts.set(operation,crypto.randomUUID());
        try {
            const run = await request('POST','/sandbox/experiments/' + encodeURIComponent(experimentId) + '/runs',{requestKey:pendingStarts.get(operation),rerunOf});
            pendingStarts.delete(operation); return run;
        } catch(e) {
            if (e.httpStatus >= 400 && e.httpStatus < 500) pendingStarts.delete(operation);
            else e.message += '；提交结果未确认，再次点击将沿用请求标识，避免重复创建运行';
            throw e;
        }
    }
    return { clone, seed, validate, canonical, comparisonControl, comparisonPair, objectiveDefinitions, normalizedWeights, algorithmLabel,
        catalog: () => request('GET','/sandbox/catalog'),
        load: () => request('GET','/sandbox/state'),
        create: (name, config, copyFrom) => request('POST','/sandbox/experiments',{name,config,copyFrom}),
        save: experiment => request('PUT','/sandbox/experiments/' + encodeURIComponent(experiment.experimentId), experiment),
        createRun,
        run: runId => request('GET','/sandbox/runs/' + encodeURIComponent(runId))
    };
})();
