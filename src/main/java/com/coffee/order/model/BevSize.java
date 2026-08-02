package com.coffee.order.model;

public enum BevSize {
    SMALL("小杯", 0),
    MEDIUM("中杯", 2),
    LARGE("大杯", 4);

    private final String label;
    private final double extraPrice;

    BevSize(String label, double extraPrice) {
        this.label = label;
        this.extraPrice = extraPrice;
    }

    public String getLabel() { return label; }
    public double getExtraPrice() { return extraPrice; }
}
