const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = file => fs.readFileSync(path.join(__dirname,'..',file),'utf8');
const config = {vehicleCount:10,orderCount:20,orderGenerationInterval:10,randomSeed:123,algorithm:'BASELINE',events:{},simulationDuration:0};
const experiment = id => ({experimentId:id,name:id,version:1,config:{...config},environmentId:'scene'});
const run = (id,experimentId,status='COMPLETED') => ({runId:id,experimentId,status,modelId:'model',environmentId:'scene',config:{...config},metrics:{},events:[]});

async function page(experiments,runs=[],confirmed=true) {
    const elements=new Map(), calls=[];
    class Element {
        constructor() { this.children=[];this.value='';this.textContent='';this.classList={toggle(){}}; }
        set id(value) {this._id=value;elements.set(value,this);}
        get id() {return this._id;}
        replaceChildren(...children) {this.children=children;this.value='';}
        append(...children) {this.children.push(...children);}
        add(option) {this.append(option);}
        setAttribute() {}
        get selectedOptions() {return this.children.filter(child=>child.value===this.value);}
    }
    for(const [,id] of source('html/sandbox.html').matchAll(/\bid="([^"]+)"/g)) {const element=new Element();element.id=id;}
    const state={experiments:structuredClone(experiments),runs:structuredClone(runs)};
    const context={window:{location:{origin:'http://localhost:9091',search:''},addEventListener(){},confirm:()=>confirmed},URLSearchParams,
        document:{getElementById:id=>elements.get(id),createElement:()=>new Element(),querySelectorAll:()=>[]},
        Option:class {constructor(text,value){this.textContent=text;this.value=value;}},
        axios:{create:()=>({})},setTimeout:()=>1,clearTimeout(){}};
    vm.createContext(context);
    vm.runInContext(source('js/services/metrics.js'),context);
    vm.runInContext(source('js/services/sandbox.js'),context);
    const service=context.window.SandboxService;
    service.catalog=async()=>({algorithms:[{id:'BASELINE',name:'基准'}],objectiveDefaults:{waiting:25,emptyDistance:25,utilization:20,tonnage:15,carbon:15},events:{}});
    service.load=async()=>structuredClone(state);
    service.create=async()=>{calls.push('create');throw new Error('不应自动创建方案');};
    service.remove=async(id,version)=>{
        calls.push({id,version});state.experiments=state.experiments.filter(e=>e.experimentId!==id);
        state.runs=state.runs.filter(r=>r.experimentId!==id);
        return {experimentId:id};
    };
    Object.assign(context,{SandboxService:service,Metrics:context.window.Metrics,StatCharts:{comparison:()=>''}});
    await vm.runInContext(source('js/sandbox.js'),context);
    return {elements,calls,state,service,location:context.window.location};
}

test('取消删除保留方案和运行结果，不发送删除请求',async()=>{
    const ui=await page([experiment('EXP-1')],[run('RUN-1','EXP-1')],false);
    await ui.elements.get('delete-experiment').onclick();
    assert.equal(ui.calls.length,0);
    assert.equal(ui.state.experiments.length,1);
    assert.equal(ui.elements.get('export-run').disabled,false);
});

test('删除后切换到剩余方案，运行历史和比较选项移除被删结果',async()=>{
    const ui=await page([experiment('EXP-1'),experiment('EXP-2')],[run('RUN-1','EXP-1'),run('RUN-2','EXP-2')]);
    await ui.elements.get('delete-experiment').onclick();
    assert.deepEqual(ui.calls,[{id:'EXP-1',version:1}]);
    assert.equal(ui.elements.get('experiment-name').value,'EXP-2');
    assert.equal(ui.state.runs.length,1);
    for(const id of ['baseline-run','candidate-run']) assert.ok(!ui.elements.get(id).children.some(option=>option.value==='RUN-1'));
    assert.equal(ui.elements.get('sandbox-error').textContent,'');
});

test('删除最后一个方案清空表单、历史和导出状态，重新进入仍保持空列表',async()=>{
    const ui=await page([experiment('EXP-1')],[run('RUN-1','EXP-1')]);
    await ui.elements.get('delete-experiment').onclick();
    assert.equal(ui.elements.get('experiment-name').value,'');
    assert.equal(ui.elements.get('config-form').hidden,true);
    assert.equal(ui.elements.get('run-history').children.length,0);
    for(const id of ['delete-experiment','copy-experiment','save-config','start-run','rerun','export-run','export-comparison'])
        assert.equal(ui.elements.get(id).disabled,true,id);
    assert.equal(ui.elements.get('new-experiment').disabled,false);
    assert.match(ui.elements.get('experiments').children[0].textContent,/暂无实验方案/);
    const reopened=await page(ui.state.experiments,ui.state.runs);
    assert.equal(reopened.calls.length,0);
    assert.equal(reopened.elements.get('config-form').hidden,true);
});

test('排队或运行中的方案禁用删除按钮，已完成和失败的方案允许删除',async()=>{
    for(const status of ['QUEUED','RUNNING','COMPLETED','FAILED']) {
        const ui=await page([experiment('EXP-1')],[run('RUN-1','EXP-1',status)]);
        assert.equal(ui.elements.get('delete-experiment').disabled,['QUEUED','RUNNING'].includes(status),status);
    }
});

test('后端拒绝删除时保留方案和选中结果，并显示原因',async()=>{
    const ui=await page([experiment('EXP-1')],[run('RUN-1','EXP-1')]);
    ui.service.remove=async()=>{throw new Error('方案已被其他页面修改，请刷新后确认再删除');};
    await ui.elements.get('delete-experiment').onclick();
    assert.equal(ui.state.experiments.length,1);
    assert.equal(ui.elements.get('experiment-name').value,'EXP-1');
    assert.equal(ui.elements.get('export-run').disabled,false);
    assert.match(ui.elements.get('sandbox-error').textContent,/其他页面修改/);
});

test('删除请求携带方案版本并对标识进行路径编码',async()=>{
    const calls=[],context={window:{},axios:{create:()=>({request:async request=>{calls.push(request);return {data:{deletedRunCount:2}};}})}};
    vm.createContext(context);vm.runInContext(source('js/services/sandbox.js'),context);
    await context.window.SandboxService.remove('EXP/a',7);
    const request=calls[0];
    assert.equal(request.method,'DELETE');assert.equal(request.url,'/sandbox/experiments/EXP%2Fa');assert.equal(request.data.version,7);
});

test('播放按钮使用选中Run跳转，旧记录提示重跑生成轨迹',async()=>{
    const old=await page([experiment('EXP-1')],[run('RUN-old','EXP-1')]);
    assert.equal(old.elements.get('play-run').disabled,true);assert.match(old.elements.get('run-playback-hint').textContent,/重跑/);
    const saved={...run('RUN-ready','EXP-1'),playbackAvailable:true};
    const ready=await page([experiment('EXP-1')],[saved]);
    assert.equal(ready.elements.get('play-run').disabled,false);ready.elements.get('play-run').onclick();
    assert.equal(ready.location.href,'cycle.html?mode=replay&runId=RUN-ready');
});
