package org.example.dto;

import org.example.entity.DispatchTask;
import org.example.entity.OrderEntity;
import org.example.entity.Vehicle;

public class DispatchCandidate {
    private Vehicle vehicle;
    private OrderEntity order;
    private DispatchTask task;

    public DispatchCandidate(Vehicle vehicle, OrderEntity order, DispatchTask task) {
        this.vehicle = vehicle;
        this.order = order;
        this.task = task;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public DispatchTask getTask() {
        return task;
    }
}