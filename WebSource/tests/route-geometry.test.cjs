const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const context={window:{}};
vm.createContext(context);
vm.runInContext(fs.readFileSync(require('node:path').join(__dirname,'../js/services/route-geometry.js'),'utf8'),context);
const geometry=context.window.RouteGeometry;
const plain=value=>JSON.parse(JSON.stringify(value));

test('fallback bends while preserving origin, pickup and destination in order',()=>{
    const route=geometry.prepare('104,30;105,30;106,31');
    assert.equal(route.illustration,true);
    assert.deepEqual(plain(route.path[0]),[104,30]);
    assert.deepEqual(plain(route.path.at(-1)),[106,31]);
    const pickup=route.path.findIndex(p=>p[0]===105&&p[1]===30);
    assert.ok(pickup>0&&pickup<route.path.length-1);
    assert.ok(route.path.slice(1,pickup).some(p=>Math.abs(p[1]-30)>.001));
    assert.ok(route.path.every(p=>p.every(Number.isFinite)));
});
test('detailed road turns are preserved and original mode stays available',()=>{
    const road='104,30;104.1,30;104.1,30.1;104.2,30.1';
    assert.deepEqual(plain(geometry.prepare(road).path),plain(geometry.parse(road)));
    assert.equal(geometry.prepare(road).illustration,false);
    assert.deepEqual(plain(geometry.prepare('104,30;105,30','original').path),[[104,30],[105,30]]);
});
test('invalid and stationary input cannot create non-finite animation coordinates',()=>{
    for(const value of [null,'','NaN,30;104,30','Infinity,30;104,30','181,30;104,30',' ,30;104,30','104,30;104,30']) {
        const route=geometry.measure(geometry.prepare(value).path);
        assert.ok(Number.isFinite(route.total));
        const position=geometry.position(route,.5);
        assert.ok(position===null||position.every(Number.isFinite));
    }
});
test('distance-based progress accounts for longitude scale at high latitude',()=>{
    const measured=geometry.measure([[0,60],[1,60],[1,61]]);
    const position=geometry.position(measured,1/3);
    // At latitude 60, one longitude degree is about half one latitude degree.
    assert.ok(Math.abs(position[0]-1)<.001&&Math.abs(position[1]-60)<.001);
    assert.deepEqual(plain(geometry.position(measured,0)),[0,60]);
    assert.deepEqual(plain(geometry.position(measured,1)),[1,61]);
});
test('date-line and polar routes are not replaced by misleading arcs',()=>{
    for(const value of ['179,30;-179,30','104,86;105,86']) {
        const route=geometry.prepare(value);
        assert.equal(route.illustration,false);
        assert.deepEqual(plain(route.path),plain(geometry.parse(value)));
    }
});
