/**
 * $Id: Constraint.java,v 1.2 2013/04/08 07:14:16 mate Exp $
 * Copyright (c) 2013, JGraph
 */

package com.mxgraph.svg2xml;

//a single constraint data that will be part of a stencils constraint data
public class Constraint 
{
	private double x;
	private double y;
	private boolean perimeter;
	private String name;

	public double getX() 
	{
		return x;
	}
	
	public void setX(double x) 
	{
		this.x = x;
	}
	
	public double getY() 
	{
		return y;
	}
	
	public void setY(double y) 
	{
		this.y = y;
	}
	
	public boolean isPerimeter() 
	{
		return perimeter;
	}
	
	public String getPerimeter()
	{
		if (perimeter)
		{
			return "1";
		}
		else
		{
			return"0";
		}
	}
	
	public void setPerimeter(boolean perimeter) 
	{
		this.perimeter = perimeter;
	}
	
	public String getName() 
	{
		return name;
	}
	
	public void setName(String name) 
	{
		name = name.replaceAll(" ", "");
		this.name = name;
	}
}
