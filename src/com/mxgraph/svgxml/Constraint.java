package com.mxgraph.svgxml;

public class Constraint {
	private double x;
	private double y;
	private boolean perimeter;
	private String name;
	
	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
	}
	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
	}
	public boolean isPerimeter() {
		return perimeter;
	}
	public void setPerimeter(boolean perimeter) {
		this.perimeter = perimeter;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
}
