window.SandboxReplay = (() => {
    // 仿真时刻没有时区，统一当作 UTC 计算差值，保持不同电脑上的时间轴一致。
    const time = value => Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(value || '') ? value : value + 'Z');
    const label = value => new Date(value).toISOString().slice(0,16).replace('T',' ');
    const number = value => Number(value) || 0;
    const rounded = value => Math.round((value + Number.EPSILON) * 100) / 100;
    const ratio = (part,total) => total > 0 ? part / total * 100 : 0;
    function create(data,events=[]) {
        if (data?.version !== 1 || !Array.isArray(data.tasks) || !Array.isArray(data.vehicles) || !Array.isArray(data.orders)) throw new Error('回放数据版本无效');
        const start=time(data.startTime),end=time(data.endTime);
        if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) throw new Error('回放时间范围无效');
        const groups=new Map(data.vehicles.map(v=>[v.id,[]]));
        const tasks=data.tasks.map(t=>({...t,start:time(t.startedAt),pickupTime:time(t.pickupAt),finish:time(t.finishedAt)}));
        tasks.forEach(t=>{if(!groups.has(t.vehicleId) || !Number.isFinite(t.start) || !Number.isFinite(t.pickupTime) || !Number.isFinite(t.finish) || t.start > t.pickupTime || t.pickupTime > t.finish) throw new Error('回放任务时序无效');groups.get(t.vehicleId).push(t);});
        groups.forEach(list=>list.sort((a,b)=>a.start-b.start || a.id-b.id));
        const orders=data.orders.map(o=>({...o,created:time(o.createdAt),completed:time(o.completedAt)}));
        const replayEvents=events.map(e=>({...e,start:time(e.startTime),end:time(e.startTime)+number(e.duration)*60000}));
        function routes(mode='curve') {
            tasks.forEach(t=>{
                t.emptyRoute=RouteGeometry.measure(RouteGeometry.prepare(t.origin+';'+t.pickup,mode).path);
                t.loadedRoute=RouteGeometry.measure(RouteGeometry.prepare(t.pickup+';'+t.delivery,mode).path);
            });
        }
        routes();
        function frame(value) {
            const at=Math.max(start,Math.min(end,Number.isFinite(value)?value:start));
            const activeEvents=replayEvents.filter(e=>e.start<=at && at<e.end);
            const affected=new Set(activeEvents.filter(e=>['TRAFFIC_JAM','WEATHER','VEHICLE_FAULT'].includes(e.eventType)).flatMap(e=>e.affectedVehicleIds || []));
            const vehicles=data.vehicles.map(v=>{
                const list=groups.get(v.id);let low=0,high=list.length;
                while(low<high){const middle=(low+high)>>1;if(list[middle].start<=at)low=middle+1;else high=middle;}
                const task=list[low-1];let position=RouteGeometry.parse(v.initialLocation)[0],status='空闲',load=0,route=null;
                if(task) {
                    if(at>=task.finish) position=RouteGeometry.parse(task.delivery)[0];
                    else if(at<task.pickupTime) {
                        route=task.emptyRoute;position=RouteGeometry.position(route,(at-task.start)/(task.pickupTime-task.start));status='空载取货';
                    } else {
                        route=task.loadedRoute;position=RouteGeometry.position(route,task.finish===task.pickupTime?1:(at-task.pickupTime)/(task.finish-task.pickupTime));status='载货运输';load=number(task.weight);
                    }
                }
                return {...v,position,status,load,task:status==='空闲'?null:task,route,affected:affected.has(v.id)};
            });
            const completed=tasks.filter(t=>t.finish<=at);
            const sum=key=>completed.reduce((total,t)=>total+number(t[key]),0);
            const empty=sum('emptyKm'),loaded=sum('loadedKm');
            const finishedOrders=orders.filter(o=>o.completed<=at);
            const metrics={totalWaitTime:rounded(sum('vehicleWaitMinutes')),totalEmptyDistance:rounded(empty),
                vehicleUtilization:rounded(ratio(loaded,empty+loaded)),completedTonnage:rounded(sum('completedTonnage')),
                totalCarbonEmission:rounded(sum('carbonKg')),cargoWaitingTonMinutes:rounded(sum('cargoWaitTonMinutes')),
                capacityDistanceUtilization:rounded(ratio(sum('tonKm'),sum('capacityKm'))),totalCompletionTimeH:rounded((at-start)/3600000),
                averageOrderCompletionTimeH:rounded(finishedOrders.length?finishedOrders.reduce((total,o)=>total+(o.completed-o.created)/3600000,0)/finishedOrders.length:0)};
            return {time:at,vehicles,metrics,activeEvents,completedTasks:completed.length,completedOrders:finishedOrders.length,
                arrivedOrders:orders.filter(o=>o.created<=at).length,totalOrders:orders.length,totalTasks:tasks.length,
                revenue:sum('revenue'),cost:sum('cost'),profit:sum('profit')};
        }
        function history(limit=120) {
            const count=Math.max(2,Math.min(limit,tasks.length+2));
            return Array.from({length:count},(_,i)=>{
                const at=start+(end-start)*i/(count-1);
                return {simulationTime:new Date(at).toISOString(),...frame(at).metrics};
            });
        }
        return {start,end,tasks,orders,events:replayEvents,frame,history,routes};
    }
    // 一倍速用60秒展示完整实验；暂停和拖动只改变回放位置。
    function clock(start,end) {
        let current=start,playing=false,speed=1;
        return {
            current:()=>current,playing:()=>playing,
            play(){if(current>=end)current=start;playing=end>start;},pause(){playing=false;},
            seek(value){current=Math.max(start,Math.min(end,Number.isFinite(value)?value:start));if(current>=end)playing=false;return current;},
            speed(value){if(Number.isFinite(value)&&value>0)speed=value;return speed;},
            advance(milliseconds){if(playing){current=Math.min(end,current+Math.max(0,milliseconds)*(end-start)/60000*speed);if(current>=end)playing=false;}return current;}
        };
    }
    return {create,clock,time,label};
})();
