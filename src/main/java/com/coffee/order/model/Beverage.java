package com.coffee.order.model;

public abstract class Beverage {
    protected String name = "未知饮品";
    protected BevSize size = BevSize.MEDIUM;

    public abstract double cost();

    public String getName() {
        return size.getLabel() + " " + name;
    }

    public BevSize getSize() { return size; }
    public void setSize(BevSize size) { this.size = size; }
}
