window.TaskPlayback = (() => {
    function create() {
        let scene=null,cursor=0;
        return {
            enter(id,latest) {scene=id;cursor=Number.isSafeInteger(latest)&&latest>=0?latest:0;},
            scene() {return scene;}, cursor() {return cursor;},
            take(id,tasks) {
                if(id!==scene)return [];
                const fresh=tasks.filter(t=>Number.isSafeInteger(t.id)&&t.id>cursor).sort((a,b)=>a.id-b.id);
                if(fresh.length)cursor=fresh.at(-1).id;
                return fresh.filter(t=>t.status==='RUNNING');
            }
        };
    }
    return {create};
})();
