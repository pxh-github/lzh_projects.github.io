package org.example.service;

import org.example.dao.DispatchTaskDao;
import org.example.dao.OrderDao;
import org.example.entity.Dsp;
import org.example.entity.LocationPoint;
import org.example.entity.OrderEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class OrderService {

    private final DaoAdapter daoAdapter;
    private final OrderDao orderDao;
    private final Dsp dsp;
    private final Database database;
    private final DispatchTaskDao dispatchTaskDao;
    private final Random random = new Random();

    public OrderService(Database database, DaoAdapter daoAdapter,
                        OrderDao orderDao,
                        AmapRouteService amapRouteService,
                        Dsp dsp,
                        DispatchTaskDao dispatchTaskDao) {
        this.daoAdapter = daoAdapter;
        this.orderDao = orderDao;
        this.dsp = dsp;
        this.database = database;
        this.dispatchTaskDao = dispatchTaskDao;
    }

    public void generateOrders(int count) {
        database.write(() -> {
            if (count < 1 || count > 10000) throw new IllegalArgumentException("数量超出允许范围");
            List<LocationPoint> all = daoAdapter.loadAllLocations();
            List<LocationPoint> rawFactories = filterRawFactories(all);
            List<LocationPoint> processingFactories = filterProcessingFactories(all);
            ensureLocationPools(rawFactories, processingFactories);

            List<String> vehicleLocations = loadVehicleLocations();
            String[] cargoTypes = {"钢材", "煤炭", "木材", "水泥", "粮食", "化肥", "砂石", "矿石"};
            BigDecimal[] weights = {
                    new BigDecimal("1.2"), new BigDecimal("3.5"), new BigDecimal("5.0"), new BigDecimal("8.0"),
                    new BigDecimal("12.0"), new BigDecimal("20.0"), new BigDecimal("30.0"), new BigDecimal("38.0")
            };

            try (Connection conn = database.open()) {

                dispatchTaskDao.clear(conn);
                try (var statement = conn.createStatement()) {
                    statement.executeUpdate("UPDATE vehicle SET status='IDLE',available_at=NOW(),empty_mileage_km=0,loaded_mileage_km=0,total_mileage_km=0,total_orders=0,total_revenue=0,total_cost=0,total_profit=0");
                }
                orderDao.clear(conn);

                for (int i = 1; i <= count; i++) {
                    OrderEntity order = randomOrder(i, rawFactories, processingFactories, vehicleLocations, cargoTypes, weights);
                    orderDao.save(conn, order);
                }

            } catch (Exception e) {
                throw new RuntimeException("failed to generate orders", e);
            }
        });
    }

    public void addOrder() {
        addOrder(java.time.LocalDateTime.now().withNano(0));
    }

    public void addOrder(java.time.LocalDateTime createdAt) {
        database.write(() -> {
            List<LocationPoint> all = daoAdapter.loadAllLocations();
            List<LocationPoint> rawFactories = filterRawFactories(all);
            List<LocationPoint> processingFactories = filterProcessingFactories(all);
            ensureLocationPools(rawFactories, processingFactories);

            List<String> vehicleLocations = loadVehicleLocations();
            String[] cargoTypes = {"钢材", "煤炭", "木材", "水泥", "粮食", "化肥", "砂石", "矿石"};
            BigDecimal[] weights = {
                    new BigDecimal("1.2"), new BigDecimal("3.5"), new BigDecimal("5.0"), new BigDecimal("8.0"),
                    new BigDecimal("12.0"), new BigDecimal("20.0"), new BigDecimal("30.0"), new BigDecimal("38.0")
            };

            try (Connection conn = database.open()) {

                int nextIndex;
                try (var statement = conn.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM transport_order")) {
                    rows.next(); nextIndex = rows.getInt(1) + 1;
                }
                if (nextIndex > 10000) throw new IllegalStateException("订单数量已达到 10000 条上限");
                OrderEntity order = randomOrder(nextIndex, rawFactories, processingFactories, vehicleLocations, cargoTypes, weights);
                order.setCreatedAt(createdAt.withNano(0));
                orderDao.save(conn, order);

            } catch (Exception e) {
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new RuntimeException("failed to add order", e);
            }
        });
    }

    public List<OrderEntity> listOrders() {
        try (Connection conn = database.open()) {
            return orderDao.findAll(conn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OrderEntity randomOrder(int index,
                                    List<LocationPoint> rawFactories,
                                    List<LocationPoint> processingFactories,
                                    List<String> vehicleLocations,
                                    String[] cargoTypes,
                                    BigDecimal[] weights) {
        LocationPoint pickup = pickNearbyLocation(rawFactories, vehicleLocations, random);
        LocationPoint delivery = processingFactories.get(random.nextInt(processingFactories.size()));
        if (pickup.getId().equals(delivery.getId())) {
            delivery = processingFactories.stream().filter(p -> !p.getId().equals(pickup.getId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("订单至少需要两个不同地点"));
        }

        String cargoType = cargoTypes[random.nextInt(cargoTypes.length)];
        BigDecimal totalWeight = weights[random.nextInt(weights.length)];
        BigDecimal distanceKm = estimateDistanceKm(pickup.getLocation(), delivery.getLocation());
        BigDecimal durationH = distanceKm.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        OrderEntity order = new OrderEntity();
        order.setOrderCode("O" + String.format("%05d", index));
        order.setCargoType(cargoType);
        order.setCargoTotalWeight(totalWeight);
        order.setAllocatedWeight(BigDecimal.ZERO);
        order.setPendingWeight(totalWeight);
        order.setPickupLocationId(pickup.getId());
        order.setPickupLocationName(pickup.getName());
        order.setPickupLocation(pickup.getLocation());
        order.setDeliveryLocationId(delivery.getId());
        order.setDeliveryLocationName(delivery.getName());
        order.setDeliveryLocation(delivery.getLocation());
        order.setRouteDistanceKm(distanceKm);
        order.setRouteDurationH(durationH);
        order.setRevenuePerTonKm(new BigDecimal("1.50"));
        order.setTotalRevenue(BigDecimal.ZERO);
        order.setStatus("PENDING");
        return order;
    }

    private List<LocationPoint> filterRawFactories(List<LocationPoint> all) {
        List<LocationPoint> list = new ArrayList<>();
        for (LocationPoint point : all) {
            if (isRawFactory(point)) {
                list.add(point);
            }
        }
        return list;
    }

    private List<LocationPoint> filterProcessingFactories(List<LocationPoint> all) {
        List<LocationPoint> list = new ArrayList<>();
        for (LocationPoint point : all) {
            if (isProcessingFactory(point)) {
                list.add(point);
            }
        }
        return list;
    }

    private void ensureLocationPools(List<LocationPoint> rawFactories, List<LocationPoint> processingFactories) {
        if (rawFactories.isEmpty() || processingFactories.isEmpty()) {
            throw new RuntimeException("raw factory or processing factory locations are missing");
        }
    }

    private List<String> loadVehicleLocations() {
        List<String> vehicleLocations = new ArrayList<>();
        try (Connection conn = database.open()) {
            java.sql.Statement st = conn.createStatement();
            java.sql.ResultSet rs = st.executeQuery("SELECT current_location FROM vehicle");
            while (rs.next()) {
                String location = rs.getString("current_location");
                if (location != null && !location.isBlank()) {
                    vehicleLocations.add(location);
                }
            }
        } catch (Exception ignored) {
        }
        return vehicleLocations;
    }

    private LocationPoint pickNearbyLocation(List<LocationPoint> factories, List<String> vehicleLocations, Random random) {
        if (vehicleLocations.isEmpty() || factories.isEmpty()) {
            return factories.get(random.nextInt(factories.size()));
        }

        double[] scores = new double[factories.size()];
        double totalScore = 0;
        for (int i = 0; i < factories.size(); i++) {
            double minDist = Double.MAX_VALUE;
            for (String vehicleLocation : vehicleLocations) {
                double dist = calcStraightDegrees(factories.get(i).getLocation(), vehicleLocation);
                if (dist < minDist) {
                    minDist = dist;
                }
            }
            scores[i] = 1.0 / (1.0 + minDist * 100.0);
            totalScore += scores[i];
        }

        double target = random.nextDouble() * totalScore;
        double accum = 0;
        for (int i = 0; i < scores.length; i++) {
            accum += scores[i];
            if (target <= accum) {
                return factories.get(i);
            }
        }
        return factories.get(factories.size() - 1);
    }

    private double calcStraightDegrees(String p1, String p2) {
        try {
            String[] a = p1.split(",");
            String[] b = p2.split(",");
            double dx = Double.parseDouble(a[0]) - Double.parseDouble(b[0]);
            double dy = Double.parseDouble(a[1]) - Double.parseDouble(b[1]);
            return Math.sqrt(dx * dx + dy * dy);
        } catch (Exception e) {
            return 999;
        }
    }

    private BigDecimal estimateDistanceKm(String p1, String p2) {
        return BigDecimal.valueOf(org.example.util.Geo.roadEstimate(p1, p2)).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isRawFactory(LocationPoint point) {
        String name = point.getName() == null ? "" : point.getName();
        return name.contains("原料")
                || name.contains("矿")
                || name.contains("仓")
                || "170402".equals(point.getType())
                || "170404".equals(point.getType());
    }

    private boolean isProcessingFactory(LocationPoint point) {
        String name = point.getName() == null ? "" : point.getName();
        return name.contains("加工")
                || name.contains("工厂")
                || name.contains("制造")
                || "170205".equals(point.getType())
                || "170300".equals(point.getType());
    }
}
