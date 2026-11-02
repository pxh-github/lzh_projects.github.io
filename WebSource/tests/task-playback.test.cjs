const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const context={window:{}};vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(__dirname,'../js/services/task-playback.js'),'utf8'),context);
const create=context.window.TaskPlayback.create,plain=v=>JSON.parse(JSON.stringify(v));
test('returning from sandbox skips all tasks generated while the monitor was away',()=>{
    const playback=create();playback.enter('scene-A',100);
    assert.deepEqual(plain(playback.take('scene-A',[{id:100,status:'RUNNING'},{id:101,status:'RUNNING'}])),[{id:101,status:'RUNNING'}]);
    playback.enter('scene-A',150);
    assert.deepEqual(plain(playback.take('scene-A',[{id:101,status:'RUNNING'},{id:149,status:'DONE'},{id:150,status:'RUNNING'}])),[]);
    assert.deepEqual(plain(playback.take('scene-A',[{id:151,status:'RUNNING'}])),[{id:151,status:'RUNNING'}]);
});
test('completed tasks advance the cursor but never animate or replay on retry',()=>{
    const playback=create();playback.enter('scene',10);
    assert.deepEqual(plain(playback.take('scene',[{id:12,status:'DONE'},{id:11,status:'RUNNING'}])),[{id:11,status:'RUNNING'}]);
    assert.equal(playback.cursor(),12);
    assert.deepEqual(plain(playback.take('scene',[{id:11,status:'RUNNING'},{id:12,status:'RUNNING'}])),[]);
});
test('a reset scene cannot reuse an old task cursor',()=>{
    const playback=create();playback.enter('old',999);
    assert.deepEqual(plain(playback.take('new',[{id:1000,status:'RUNNING'}])),[]);
    playback.enter('new',2000);
    assert.deepEqual(plain(playback.take('new',[{id:2001,status:'RUNNING'}])),[{id:2001,status:'RUNNING'}]);
});
