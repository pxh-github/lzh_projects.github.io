/* Demonstration model only: never presented as backend algorithm evaluation. */
window.SandboxFixtures = {
    algorithms: [{ id: 'BASELINE', name: 'Baseline · 基准调度' }, { id: 'OPTIMIZED', name: 'Optimized · 收益优化' }],
    events: { TRAFFIC_JAM: '交通拥堵', WEATHER: '天气影响', VEHICLE_FAULT: '车辆故障', CARGO_DAMAGE: '货损', SHORTAGE: '缺货' },
    defaults: { mapId: '现有 POI 地图', poiCount: null, simulationDuration: 24, vehicleCount: 50, orderGenerationInterval: 10, randomSeed: 20260922, algorithm: 'BASELINE', events: {} },
    eventDetails(config) {
        return Object.keys(this.events).filter(type => config.events[type]).map((type, index) => ({
            eventId: `DEMO-EVENT-${index + 1}`, eventType: type, startTime: `${String(8 + index).padStart(2, '0')}:00`,
            duration: 12, extraDelay: 8, affectedVehicleIds: ['DEMO-V001'], affectedOrderIds: ['DEMO-O001']
        }));
    },
    evaluate(config) {
        let seed = config.randomSeed >>> 0;
        const random = () => { seed = (Math.imul(1664525, seed) + 1013904223) >>> 0; return seed / 4294967296; };
        const demand = 1440 / config.orderGenerationInterval;
        const disruption = Object.values(config.events).filter(Boolean).length;
        const factor = 0.9 + random() * 0.2;
        // Same illustrative model for both mode labels; no invented algorithm advantage.
        const distance = demand * 12 * factor;
        return { totalWaitTime: demand / config.vehicleCount * 100 * factor + disruption * 25,
            totalEmptyDistance: distance, vehicleUtilization: Math.min(95, demand / config.vehicleCount * 15),
            completedTonnage: Math.min(demand, config.vehicleCount * 4) * 5 * factor / (1 + disruption * 0.03),
            totalCarbonEmission: distance * 0.8 + config.vehicleCount * 10 };
    }
};
