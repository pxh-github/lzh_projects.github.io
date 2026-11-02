const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const context = {window:{},axios:{create:()=>({})}};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../js/services/sandbox.js'),'utf8'),context);
const service = context.window.SandboxService;
const run = (id,patch={}) => ({runId:id,status:'COMPLETED',modelId:'current-model',environmentId:'same-scene',config:{randomSeed:123,vehicleCount:10,algorithm:'BASELINE',events:{}},...patch});
const plain = value => JSON.parse(JSON.stringify(value));

test('comparison follows the selected run and uses the newest matching baseline, skipping legacy results',()=>{
    const runs=[run('legacy',{modelId:null}),run('first'),run('latest'),run('candidate',{config:{randomSeed:123,vehicleCount:10,algorithm:'OPTIMIZED',events:{}}})];
    assert.deepEqual(plain(service.comparisonPair(runs,'candidate')),{baseline:'latest',candidate:'candidate'});
    assert.deepEqual(plain(service.comparisonPair(runs,'first')),{baseline:'latest',candidate:'first'});
});
test('changing seed, fleet, event configuration, scene or model never silently reuses an incompatible baseline',()=>{
    for(const patch of [{environmentId:'other'},{modelId:'other'},{config:{randomSeed:456,vehicleCount:10,algorithm:'BASELINE',events:{}}},{config:{randomSeed:123,vehicleCount:20,algorithm:'BASELINE',events:{}}},{config:{randomSeed:123,vehicleCount:10,algorithm:'BASELINE',events:{WEATHER:true}}}]){
        assert.deepEqual(plain(service.comparisonPair([run('old'),run('new',patch)],'new')),{baseline:'',candidate:'new'});
    }
});
test('queued, running and failed selections are retained instead of falling back to an old completed result',()=>{
    for(const status of ['QUEUED','RUNNING','FAILED']){
        assert.deepEqual(plain(service.comparisonPair([run('old'),run('current',{status})],'current')),{baseline:'old',candidate:'current'});
    }
});
test('a new experiment with no runs clears stale comparison selections',()=>{
    assert.deepEqual(plain(service.comparisonPair([run('old')],undefined)),{baseline:'',candidate:''});
});
test('control matching ignores property order and algorithm while rejecting missing historical metadata',()=>{
    const a=run('a'), b=run('b',{config:{events:{},algorithm:'OPTIMIZED',vehicleCount:10,randomSeed:123}});
    assert.equal(service.comparisonControl(a),service.comparisonControl(b));
    assert.equal(service.comparisonControl(run('legacy',{modelId:null})),null);
    assert.equal(service.comparisonControl(run('legacy',{environmentId:null})),null);
});

test('weighted preferences may differ while scene and demand controls must still match',()=>{
    const a=run('a',{config:{randomSeed:123,vehicleCount:10,algorithm:'BASELINE',events:{WEATHER:false}}});
    const b=run('b',{config:{randomSeed:123,vehicleCount:10,algorithm:'WEIGHTED',events:{},objectiveWeights:{waiting:0,emptyDistance:1,utilization:0,tonnage:0,carbon:0}}});
    assert.equal(service.comparisonControl(a),service.comparisonControl(b));
    assert.deepEqual(plain(service.comparisonPair([a,b],'b')),{baseline:'a',candidate:'b'});
    b.config.objectiveWeights={waiting:1,emptyDistance:0,utilization:0,tonnage:0,carbon:0};
    assert.equal(service.comparisonControl(a),service.comparisonControl(b));
    b.config.vehicleCount=11;assert.notEqual(service.comparisonControl(a),service.comparisonControl(b));
});

test('weighted input validation and normalization agree on relative and zero preferences',()=>{
    const weights={waiting:25,emptyDistance:25,utilization:20,tonnage:15,carbon:15};
    assert.deepEqual(plain(service.normalizedWeights(weights)),{waiting:.25,emptyDistance:.25,utilization:.2,tonnage:.15,carbon:.15});
    const config={vehicleCount:10,orderGenerationInterval:10,randomSeed:123,simulationDuration:0,orderCount:10,algorithm:'WEIGHTED',objectiveWeights:weights};
    assert.doesNotThrow(()=>service.validate(config));
    for(const invalid of [null,{}, {...weights,waiting:-1},{...weights,waiting:NaN},{...weights,waiting:Infinity},{...weights,waiting:'25'},Object.fromEntries(Object.keys(weights).map(k=>[k,0]))])
        assert.throws(()=>service.validate({...config,objectiveWeights:invalid}));
    assert.equal(service.algorithmLabel('WEIGHTED'),'加权多目标');
});

test('a lost start response reuses its request key, while a completed call permits a new run',async()=>{
    const calls=[];let fail=true,sequence=0;
    const isolated={window:{},crypto:{randomUUID:()=>`request-${++sequence}`},axios:{create:()=>({request:async request=>{
        calls.push(request);if(fail){fail=false;throw new Error('timeout');}return {data:{runId:'RUN-1'}};
    }})}};
    vm.createContext(isolated);vm.runInContext(fs.readFileSync(path.join(__dirname,'../js/services/sandbox.js'),'utf8'),isolated);
    await assert.rejects(isolated.window.SandboxService.createRun('EXP-1'),/沿用请求标识/);
    await isolated.window.SandboxService.createRun('EXP-1');
    assert.equal(calls[0].data.requestKey,calls[1].data.requestKey);
    await isolated.window.SandboxService.createRun('EXP-1');
    assert.notEqual(calls[1].data.requestKey,calls[2].data.requestKey);
});
