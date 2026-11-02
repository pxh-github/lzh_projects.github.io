/* Shared contracts and unit conversion. Missing measurements remain null. */
window.Metrics = (() => {
    const definitions = [
        ['totalWaitTime', '总体等待时间', 'min', false],
        ['totalEmptyDistance', '空载里程', 'km', false],
        ['vehicleUtilization', '车辆利用率', '%', true],
        ['completedTonnage', '运输达成吨位', 't', true],
        ['totalCarbonEmission', '总碳排放', 'kg CO₂', false]
    ];
    const number = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? null : Number(value);
    const sum = (rows, field) => rows.every(row => number(row[field]) !== null) ? rows.reduce((n, row) => n + Number(row[field]), 0) : null;
    const ratio = (part, total) => part === null || total === null ? null : total === 0 ? 0 : part / total * 100;
    function adapt(stats, tasks, vehicles, orders, cycle) {
        const empty = number(stats.totalEmptyMileageKm), loaded = number(stats.totalLoadedMileageKm);
        const wait = sum(tasks, 'vehicleWaitTimeH');
        return {
            totalWaitTime: number(stats.totalWaitTime) ?? (wait === null ? null : wait * 60),
            totalEmptyDistance: empty,
            vehicleUtilization: ratio(loaded, empty === null || loaded === null ? null : loaded + empty),
            completedTonnage: number(stats.completedTonnage) ?? sum(tasks.filter(t => t.status === 'DONE'), 'assignedWeight'),
            totalCarbonEmission: number(stats.totalCarbonEmission),
            runningVehicleCount: vehicles.filter(v => v.status === 'BUSY').length,
            totalVehicleCount: vehicles.length,
            pendingOrderCount: orders.filter(o => o.status !== 'DONE').length,
            orderCompletionRate: ratio(orders.filter(o => o.status === 'DONE').length, orders.length),
            activeEventCount: null, simulationTime: cycle.simTime || '—'
        };
    }
    function improvement(baseline, candidate, higher) {
        baseline = number(baseline); candidate = number(candidate);
        if (baseline === null || candidate === null || baseline === 0) return null;
        return (higher ? candidate - baseline : baseline - candidate) / Math.abs(baseline) * 100;
    }
    const format = value => number(value) === null ? '—' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 1 });
    const sandboxDefinitions = [...definitions,
        ['cargoWaitingTonMinutes','货物累计等待','t·min',false],
        ['capacityDistanceUtilization','载重里程利用率','%',true],
        ['totalCompletionTimeH','全部订单完成时长','h',false],
        ['averageOrderCompletionTimeH','平均订单完成时长','h',false]];
    return { definitions, sandboxDefinitions, number, sum, ratio, adapt, improvement, format };
})();
