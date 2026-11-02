const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const requests = [];
const context = { window: {}, crypto: require('node:crypto').webcrypto,
    axios: {create: () => ({request: async payload => { requests.push(payload); return {data:{runId:'RUN-server',source:'BACKEND'}}; }})} };
vm.createContext(context);
for (const file of ['services/metrics.js', 'services/sandbox.js', 'services/charts.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../js', file), 'utf8'), context);
    Object.assign(context, context.window);
}
const { Metrics: m, SandboxService: s } = context.window;
const defaults = {vehicleCount:50,randomSeed:20260922,orderGenerationInterval:10,algorithm:'BASELINE'};
test('real metrics use vehicle wait, not cargo wait; units and missing carbon', () => {
    const result = m.adapt({ totalEmptyMileageKm: 20, totalLoadedMileageKm: 80, totalCargoWaitHours: 999 }, [{ vehicleWaitTimeH: 2, assignedWeight: 8, status: 'DONE' }, { vehicleWaitTimeH: 1, assignedWeight: 5, status: 'PENDING' }], [], [], {});
    assert.equal(result.totalWaitTime, 180); assert.equal(result.vehicleUtilization, 80);
    assert.equal(result.completedTonnage, 8); assert.equal(result.totalCarbonEmission, null);
});
test('zero and missing measurements never produce NaN or Infinity', () => {
    assert.equal(m.improvement(0, 20, true), null); assert.equal(m.improvement(null, 20, true), null);
    assert.equal(m.improvement(100, 80, false), 20); assert.equal(m.improvement(100, 120, true), 20);
    assert.equal(m.ratio(0, 0), 0); assert.equal(m.sum([{ x: null }], 'x'), null);
});
test('aggregate backend measurements take precedence over paginated task data', () => {
    const result=m.adapt({totalWaitTime:200,completedTonnage:300,totalCarbonEmission:400,totalEmptyMileageKm:20,totalLoadedMileageKm:80},[],[],[],{});
    assert.equal(result.totalWaitTime,200);assert.equal(result.completedTonnage,300);assert.equal(result.totalCarbonEmission,400);
});
test('rerun delegates immutable snapshot handling to backend', async () => {
    const run=await s.createRun('EXP-test','RUN-old');
    const request=requests.at(-1);
    assert.equal(run.source,'BACKEND');assert.equal(request.data.rerunOf,'RUN-old');
    assert.equal(request.url,'/sandbox/experiments/EXP-test/runs');
    assert.ok(request.data.requestKey);
});
test('control comparison ignores object key order', () => {
    assert.equal(JSON.stringify(s.canonical({seed:1,events:{WEATHER:true,TRAFFIC_JAM:false}})),JSON.stringify(s.canonical({events:{TRAFFIC_JAM:false,WEATHER:true},seed:1})));
});
test('invalid experiment settings rejected before requests', () => {
    for (const patch of [{vehicleCount:0},{randomSeed:NaN},{randomSeed:-1},{algorithm:'GA'},{orderGenerationInterval:0}]) assert.throws(() => s.validate({...defaults,...patch}));
});

test('trend uses real time spacing rather than equally spaced sample indices', () => {
    const points=[{simulationTime:'2026-01-01T00:00',completedTonnage:1},{simulationTime:'2026-01-01T00:01',completedTonnage:2},{simulationTime:'2026-01-01T00:10',completedTonnage:3}];
    const chart=context.window.StatCharts.trend(points,'completedTonnage');
    const coords=chart.svg.match(/points="([^"]+)"/)[1].split(' ').map(pair=>Number(pair.split(',')[0]));
    assert.equal(coords[0],60);assert.equal(coords[1],130);assert.equal(coords[2],760);
});
test('one real sample does not imply a trend and missing data does not produce NaN', () => {
    const chart=context.window.StatCharts.trend([{simulationTime:'2026-01-01T00:00',capturedAt:'2026-01-01T00:00',completedTonnage:0}],'completedTonnage');
    assert.ok(chart.note.includes('仅1个采样'));assert.ok(!chart.svg.includes('polyline'));assert.ok(!chart.svg.includes('NaN'));
});
test('comparison charts keep each metric in its own units and scale', () => {
    const chart=context.window.StatCharts.comparison({metrics:{totalEmptyDistance:500}},{metrics:{totalEmptyDistance:1000}});
    assert.ok(chart.includes('width="115"'));assert.ok(chart.includes('width="230"'));assert.ok(chart.includes('每项指标独立刻度'));
});
test('sandbox includes completion time while live monitoring retains its five metric contract',()=>{
    assert.equal(m.definitions.length,5);assert.equal(m.sandboxDefinitions.length,9);
    const chart=context.window.StatCharts.comparison({metrics:{totalCompletionTimeH:80,averageOrderCompletionTimeH:40}},{metrics:{totalCompletionTimeH:60,averageOrderCompletionTimeH:30}},m.sandboxDefinitions);
    assert.ok(chart.includes('全部订单完成时长'));assert.ok(chart.includes('平均订单完成时长'));assert.ok(chart.includes('60 h'));assert.ok(chart.includes('从仿真开始'));
    assert.equal(m.improvement(80,60,false),25);
});
test('completion-mode order count must be finite and within capacity',()=>{
    for(const orderCount of [0,1001,NaN,1.5])assert.throws(()=>s.validate({...defaults,simulationDuration:0,orderCount}));
    assert.doesNotThrow(()=>s.validate({...defaults,simulationDuration:0,orderCount:12}));
});
