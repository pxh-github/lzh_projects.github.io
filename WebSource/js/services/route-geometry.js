window.RouteGeometry = (() => {
    const valid = p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite) && Math.abs(p[0]) <= 180 && Math.abs(p[1]) <= 90;
    const same = (a,b) => a[0] === b[0] && a[1] === b[1];
    function clean(points) {
        const result=[];
        for(const p of points) if(valid(p) && (!result.length || !same(result.at(-1),p))) result.push(p.slice());
        return result;
    }
    function parse(value) {
        if(typeof value !== 'string') return [];
        return clean(value.split(';').map(pair => {
            const parts=pair.split(',');
            return parts.length===2 && parts.every(v=>v.trim()!=='') ? parts.map(Number) : [];
        }));
    }
    const project = p => [p[0]*Math.PI/180,Math.log(Math.tan(Math.PI/4+p[1]*Math.PI/360))];
    const unproject = p => [p[0]*180/Math.PI,(2*Math.atan(Math.exp(p[1]))-Math.PI/2)*180/Math.PI];
    function curve(points) {
        const anchors=clean(points), result=[];
        if(anchors.some(p=>Math.abs(p[1])>85)) return anchors;
        for(let i=1;i<anchors.length;i++) {
            const from=anchors[i-1], to=anchors[i];
            // Avoid drawing an invented arc across the date line.
            if(Math.abs(from[0]-to[0])>180) return anchors;
            const a=project(from),b=project(to),dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);
            const bend=Math.min(length*.18,.02);
            const control=[(a[0]+b[0])/2-dy/length*bend,(a[1]+b[1])/2+dx/length*bend];
            if(!result.length) result.push(from.slice());
            for(let step=1;step<32;step++) {
                const t=step/32,u=1-t;
                const point=unproject([u*u*a[0]+2*u*t*control[0]+t*t*b[0],u*u*a[1]+2*u*t*control[1]+t*t*b[1]]);
                if(!valid(point)) return anchors;
                result.push(point);
            }
            result.push(to.slice());
        }
        return anchors.length<2 ? anchors : result;
    }
    function prepare(value,mode='curve') {
        const anchors=parse(value);
        // Detailed road geometry stays intact: smoothing it could cut across roads.
        const illustration=mode==='curve' && anchors.length>=2 && anchors.length<=3;
        const path=illustration ? curve(anchors) : anchors;
        return {path,illustration:illustration && path.length>anchors.length};
    }
    function distance(a,b) {
        const rad=Math.PI/180,lat=(b[1]-a[1])*rad,lng=(b[0]-a[0])*rad;
        const h=Math.sin(lat/2)**2+Math.cos(a[1]*rad)*Math.cos(b[1]*rad)*Math.sin(lng/2)**2;
        return 6371.0088*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
    }
    function measure(points) {
        const path=clean(points), cumulative=[0];
        for(let i=1;i<path.length;i++) cumulative.push(cumulative.at(-1)+distance(path[i-1],path[i]));
        return {path,cumulative,total:cumulative.at(-1)};
    }
    function position(route,fraction) {
        const {path,cumulative,total}=route;
        if(!path.length) return null;
        const t=Number.isFinite(fraction)?Math.max(0,Math.min(1,fraction)):0;
        if(t===0) return path[0].slice();
        if(t===1 || total===0) return path.at(-1).slice();
        const target=t*total;
        let low=1,high=path.length-1;
        while(low<high) { const mid=(low+high)>>1;if(cumulative[mid]<target)low=mid+1;else high=mid; }
        const span=cumulative[low]-cumulative[low-1],ratio=span>0?(target-cumulative[low-1])/span:0;
        const a=path[low-1],b=path[low];
        return [a[0]+(b[0]-a[0])*ratio,a[1]+(b[1]-a[1])*ratio];
    }
    return {parse,prepare,measure,position};
})();
