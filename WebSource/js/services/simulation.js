window.SimulationService = (() => {
    const client = axios.create({ baseURL: window.SIMULATION_API_BASE || window.location?.origin || 'http://localhost:9091', timeout: 10000 });
    async function snapshot() {
        const paths = ['/stats/live', '/dispatch/tasks?limit=500&includeRoute=false', '/vehicles', '/orders', '/stats/vehicle-totals', '/dispatch/events'];
        const [live, tasks, vehicles, orders, vehicleTotals, eventState] = await Promise.all(paths.map(path => client.get(path).then(r => r.data)));
        return { stats:live.current.stats, tasks, vehicles, orders, cycle:live.current.cycle, vehicleTotals,
            metrics:live.current.metrics, history:live.history.map(s=>({...s.metrics,sampleId:s.sampleId,capturedAt:s.capturedAt})), sceneId:live.current.sceneId,
            events:eventState.sceneId===live.current.sceneId?eventState.events:[],activeEventCount:eventState.sceneId===live.current.sceneId?eventState.activeCount:0 };
    }
    async function compareModes() {
        const { data } = await client.get('/stats/compare');
        const convert = stats => {
            const empty = Metrics.number(stats.totalEmptyMileageKm), loaded = Metrics.number(stats.totalLoadedMileageKm);
            return { totalWaitTime: Metrics.number(stats.totalWaitTime), totalEmptyDistance: empty,
                vehicleUtilization: Metrics.ratio(loaded, empty === null || loaded === null ? null : empty + loaded),
                completedTonnage: Metrics.number(stats.completedTonnage), totalCarbonEmission: Metrics.number(stats.totalCarbonEmission) };
        };
        return { baseline: convert(data.baseline), candidate: convert(data.optimized) };
    }
    async function triggerEvent(request) {return (await client.post('/dispatch/events',request)).data;}
    return { snapshot, compareModes, triggerEvent };
})();
