const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const context={window:{}};vm.createContext(context);
for(const file of ['route-geometry.js','sandbox-replay.js']) {vm.runInContext(fs.readFileSync(path.join(__dirname,'../js/services',file),'utf8'),context);Object.assign(context,context.window);}
const replay=context.window.SandboxReplay;
const at=hour=>`2026-01-01T${String(hour).padStart(2,'0')}:00:00`;
const payload=()=>({version:1,startTime:at(0),endTime:at(4),vehicles:[{id:1,code:'V1',capacity:10,initialLocation:'0,0'},{id:2,code:'V2',capacity:10,initialLocation:'8,0'}],
    orders:[{id:1,createdAt:at(0),completedAt:at(2)},{id:2,createdAt:at(2),completedAt:at(4)}],
    tasks:[{id:1,vehicleId:1,orderId:1,origin:'0,0',pickup:'1,0',delivery:'2,0',startedAt:at(0),pickupAt:at(1),finishedAt:at(2),weight:2,emptyKm:10,loadedKm:10,vehicleWaitMinutes:5,cargoWaitTonMinutes:120,capacityKm:200,tonKm:20,completedTonnage:1.8,carbonKg:20},
        {id:2,vehicleId:1,orderId:2,origin:'2,0',pickup:'3,0',delivery:'4,0',startedAt:at(3),pickupAt:at(3),finishedAt:at(4),weight:3,emptyKm:0,loadedKm:20,vehicleWaitMinutes:10,cargoWaitTonMinutes:180,capacityKm:200,tonKm:60,completedTonnage:3,carbonKg:30}]});
const plain=value=>JSON.parse(JSON.stringify(value));

test('回放按取货与送达时刻分段，拖动进度可恢复历史位置且不串车',()=>{
    const engine=replay.create(payload());engine.routes('original');
    let frame=engine.frame(replay.time(at(0))+30*60000);
    assert.equal(frame.vehicles[0].status,'空载取货');assert.ok(Math.abs(frame.vehicles[0].position[0]-.5)<1e-9);
    frame=engine.frame(replay.time(at(1))+30*60000);
    assert.equal(frame.vehicles[0].status,'载货运输');assert.equal(frame.vehicles[0].load,2);assert.ok(Math.abs(frame.vehicles[0].position[0]-1.5)<1e-9);
    frame=engine.frame(replay.time(at(2))+30*60000);
    assert.equal(frame.vehicles[0].status,'空闲');assert.deepEqual(plain(frame.vehicles[0].position),[2,0]);
    assert.deepEqual(plain(frame.vehicles[1].position),[8,0]);
    assert.deepEqual(plain(engine.frame(engine.end).vehicles[0].position),[4,0]);
    assert.deepEqual(plain(engine.frame(engine.start-100).vehicles[0].position),[0,0]);
});

test('任务完成前不提前计入结果，结束时吨位、排放和订单时长正确',()=>{
    const engine=replay.create(payload());assert.equal(engine.frame(engine.start).metrics.completedTonnage,0);
    const middle=engine.frame(replay.time(at(2)));
    assert.equal(middle.completedOrders,1);assert.equal(middle.metrics.completedTonnage,1.8);
    const end=engine.frame(engine.end+1000);
    assert.equal(end.time,engine.end);assert.equal(end.completedOrders,2);assert.equal(end.completedTasks,2);
    assert.deepEqual(plain(end.metrics),{totalWaitTime:15,totalEmptyDistance:10,vehicleUtilization:75,completedTonnage:4.8,totalCarbonEmission:50,
        cargoWaitingTonMinutes:300,capacityDistanceUtilization:20,totalCompletionTimeH:4,averageOrderCompletionTimeH:2});
    assert.equal(engine.history().at(-1).totalCarbonEmission,50);
});

test('事件仅在保存的时间窗内生效，缺货事件不错误标记车辆故障',()=>{
    const events=[{eventType:'WEATHER',startTime:at(1),duration:30,affectedVehicleIds:[1,2]},{eventType:'SHORTAGE',startTime:at(2),duration:30,affectedVehicleIds:[1]}];
    const engine=replay.create(payload(),events);
    assert.equal(engine.frame(replay.time(at(1))).vehicles[0].affected,true);
    assert.equal(engine.frame(replay.time(at(1))+30*60000).activeEvents.length,0);
    assert.equal(engine.frame(replay.time(at(2))).activeEvents.length,1);
    assert.equal(engine.frame(replay.time(at(2))).vehicles[0].affected,false);
});

test('播放时钟支持暂停、倍速、拖动与从结尾重新播放',()=>{
    const clock=replay.clock(0,4000);clock.play();clock.advance(15000);assert.equal(clock.current(),1000);
    clock.pause();clock.advance(10000);assert.equal(clock.current(),1000);
    clock.speed(2);clock.play();clock.advance(7500);assert.equal(clock.current(),2000);
    clock.seek(9000);assert.equal(clock.current(),4000);assert.equal(clock.playing(),false);
    clock.play();assert.equal(clock.current(),0);clock.advance(30000);assert.equal(clock.current(),4000);assert.equal(clock.playing(),false);
});

test('无效回放版本和任务时间顺序被拒绝',()=>{
    assert.throws(()=>replay.create({...payload(),version:9}),/版本/);
    const invalid=payload();invalid.tasks[0].pickupAt=at(3);assert.throws(()=>replay.create(invalid),/时序/);
});

test('沙箱回放入口仅加载回放脚本，不加载实时轮询或控制脚本',async()=>{
    const loaded=[],select={};const isolated={window:{location:{search:'?mode=replay'}},URLSearchParams,
        document:{getElementById:()=>select,createElement:()=>({}),body:{classList:{add(){}},append(script){loaded.push(script.src);script.onload();}}}};
    vm.createContext(isolated);vm.runInContext(fs.readFileSync(path.join(__dirname,'../js/monitor-mode.js'),'utf8'),isolated);
    await new Promise(resolve=>setImmediate(resolve));
    assert.equal(select.value,'replay');assert.equal(loaded.length,2);assert.ok(loaded.every(url=>url.includes('sandbox-replay')));
    assert.ok(!loaded.some(url=>url.includes('dashboard.js')||url.includes('/cycle.js')));
});
