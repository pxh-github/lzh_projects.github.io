package org.example.service;

import org.example.dao.DispatchTaskDao;
import org.example.dao.VehicleDao;
import org.example.entity.Dsp;
import org.example.entity.LocationPoint;
import org.example.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class VehicleService {

    private final VehicleDao vehicleDao;
    private final DaoAdapter daoAdapter;
    private final Dsp dsp;
    private final Database database;
    private final DispatchTaskDao dispatchTaskDao;
    private final Random random = new Random();

    public VehicleService(Database database, VehicleDao vehicleDao, DaoAdapter daoAdapter, Dsp dsp, DispatchTaskDao dispatchTaskDao) {
        this.vehicleDao = vehicleDao;
        this.daoAdapter = daoAdapter;
        this.dsp = dsp;
        this.database = database;
        this.dispatchTaskDao = dispatchTaskDao;
    }

    public void initVehicles(int count) {
        database.write(() -> {
            if (count < 1 || count > 500) throw new IllegalArgumentException("数量超出允许范围");
            List<LocationPoint> locations = daoAdapter.loadAllLocations();
            if (locations.isEmpty()) {
                throw new RuntimeException("location table is empty, cannot initialize vehicles");
            }

            Connection conn = null;
            try {
                conn = database.open();

                dispatchTaskDao.clear(conn);
                try (var statement = conn.createStatement()) {
                    statement.executeUpdate("UPDATE transport_order SET allocated_weight=0,pending_weight=cargo_total_weight,status='PENDING',total_revenue=0,assigned_at=NULL,finished_at=NULL");
                }
                vehicleDao.clear(conn);

                for (int i = 1; i <= count; i++) {
                    LocationPoint start = locations.get(random.nextInt(locations.size()));
                    vehicleDao.save(conn, buildSceneVehicle(i, start, random, LocalDateTime.now().withNano(0)));
                }

            } catch (Exception e) {
                rollbackQuietly(conn);
                throw new RuntimeException("failed to initialize vehicles", e);
            } finally {
                closeQuietly(conn);
            }
        });
    }

    public void addVehicle() {
        addVehicle(LocalDateTime.now().withNano(0));
    }

    public void addVehicle(LocalDateTime availableAt) {
        database.write(() -> {
            List<LocationPoint> locations = daoAdapter.loadAllLocations();
            if (locations.isEmpty()) {
                throw new RuntimeException("location table is empty, cannot add vehicle");
            }

            Connection conn = null;
            try {
                conn = database.open();

                int nextIndex;
                try (var statement = conn.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM vehicle")) {
                    rows.next(); nextIndex = rows.getInt(1) + 1;
                }
                if (nextIndex > 500) throw new IllegalStateException("车辆数量已达到 500 辆上限");
                LocationPoint start = locations.get(random.nextInt(locations.size()));
                vehicleDao.save(conn, buildSceneVehicle(nextIndex, start, random, availableAt.withNano(0)));

            } catch (Exception e) {
                rollbackQuietly(conn);
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new RuntimeException("failed to add vehicle", e);
            } finally {
                closeQuietly(conn);
            }
        });
    }

    public List<Vehicle> listVehicles() {
        try (Connection conn = database.open()) {
            return vehicleDao.findAll(conn);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Vehicle buildSceneVehicle(int index, LocationPoint start, Random generator, LocalDateTime startTime) {
        String[] types = {"SMALL_1_5T", "MEDIUM_6T", "LARGE_15T", "HEAVY_40T"};
        String type = types[generator.nextInt(types.length)];

        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleCode("V" + String.format("%03d", index));
        vehicle.setVehicleType(type);
        vehicle.setCurrentLocationId(start.getId());
        vehicle.setCurrentLocationName(start.getName());
        vehicle.setCurrentLocation(start.getLocation());
        vehicle.setStatus("IDLE");
        vehicle.setAvailableAt(startTime);
        vehicle.setId((long) index);

        switch (type) {
            case "SMALL_1_5T" -> {
                vehicle.setMaxLoadTon(new BigDecimal("1.5"));
                vehicle.setActualSpeedKmh(new BigDecimal("100"));
                vehicle.setSimSpeedKms(new BigDecimal("1000"));
                vehicle.setWearCostPerHour(new BigDecimal("18"));
                vehicle.setFuelConsumptionPer100km(new BigDecimal("9"));
                vehicle.setFuelCostPerKm(calcFuelCostPerKm("9", "8"));
            }
            case "MEDIUM_6T" -> {
                vehicle.setMaxLoadTon(new BigDecimal("6"));
                vehicle.setActualSpeedKmh(new BigDecimal("95"));
                vehicle.setSimSpeedKms(new BigDecimal("1000"));
                vehicle.setWearCostPerHour(new BigDecimal("30"));
                vehicle.setFuelConsumptionPer100km(new BigDecimal("16"));
                vehicle.setFuelCostPerKm(calcFuelCostPerKm("16", "8"));
            }
            case "LARGE_15T" -> {
                vehicle.setMaxLoadTon(new BigDecimal("15"));
                vehicle.setActualSpeedKmh(new BigDecimal("90"));
                vehicle.setSimSpeedKms(new BigDecimal("1000"));
                vehicle.setWearCostPerHour(new BigDecimal("46"));
                vehicle.setFuelConsumptionPer100km(new BigDecimal("28"));
                vehicle.setFuelCostPerKm(calcFuelCostPerKm("28", "8"));
            }
            default -> {
                vehicle.setMaxLoadTon(new BigDecimal("40"));
                vehicle.setActualSpeedKmh(new BigDecimal("80"));
                vehicle.setSimSpeedKms(new BigDecimal("1000"));
                vehicle.setWearCostPerHour(new BigDecimal("75"));
                vehicle.setFuelConsumptionPer100km(new BigDecimal("42"));
                vehicle.setFuelCostPerKm(calcFuelCostPerKm("42", "8"));
            }
        }
        return vehicle;
    }

    private BigDecimal calcFuelCostPerKm(String consumptionPer100Km, String fuelPrice) {
        return new BigDecimal(consumptionPer100Km)
                .multiply(new BigDecimal(fuelPrice))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private void rollbackQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (Exception ignored) {
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }
}
