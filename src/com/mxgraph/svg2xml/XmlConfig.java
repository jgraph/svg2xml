/**
 * $Id: XmlConfig.java,v 1.6 2013/04/22 05:32:08 mate Exp $
 * Copyright (c) 2013, JGraph
 */

package com.mxgraph.svg2xml;

public class XmlConfig 
{
	//NOTE: don't set these variables manually, they are read from the XML config files.
	//these are default values
	
	//if true, the first element is placed in the stencils background block
	private boolean background = false;
	
	//true: calculate border 
	//false: read from source SVG if false
	private boolean calculateBorder = true;
	
	//the bounding box of the SVG
	private double stencilBoundsX;
	private double stencilBoundsY;
	private double stencilBoundsMinX = 0;
	private double stencilBoundsMinY = 0;

	//variable or fixed aspect ratio of the current stencil 
	public static enum aspectType { VARIABLE, FIXED };
	private aspectType aspect = aspectType.VARIABLE;
	
	private String strokewidth = "inherit";
	
	//use relative scaling (used if you want to change the size of resulting XMLs)
	private boolean relativeScaling = true;

	// scaling ratio used if relativeScaling=true (source SVG / destination XML)
	private double relativeScalingRatio = 1.0d;
	
	//every stencil will be absoluteScalingX in X size, if relativeScaling=false
	private double absoluteScalingX = 100d;
	
	//every stencil will be absoluteScalingY in Y size, if relativeScaling=false
	private double absoluteScalingY = 100d;

	//round coords to reduce XML size
	private boolean roundCoords = true;
	
	//round to decimals (used with roundCoords=true)
	private int decimalsToRound = 2;
	
	private Connection connection;
	private XmlStyle style;

	public XmlConfig(Svg2XmlGui gui)
	{
		setCalculateBorder(gui.isCalculateBorder());
		setRelativeScaling(gui.isRelativeScaling());
		setRelativeScalingRatio(gui.getRelativeScaleRatio(), gui);
		setAbsoluteScalingX(gui.getAbsoluteScalingX());
		setAbsoluteScalingY(gui.getAbsoluteScalingY());
		setRoundCoords(gui.isRoundCoords());
		setDecimalsToRound(gui.getDecimalsToRound());
	}
	
	public boolean isBackground()
	{
		return background;
	}
	
	public void setBackground(boolean background)
	{
		this.background = background;
	}
	
	public boolean isCalculateBorder()
	{
		return calculateBorder;
	}
	
	private void setCalculateBorder(boolean calculateBorder) 
	{
		this.calculateBorder = calculateBorder;
	}
	
	public aspectType getAspect() 
	{
		return aspect;
	}

	public String getAspectString() 
	{
		if (aspect == aspectType.FIXED)
		{
			return "fixed";
		}
		else
		{
			return "variable";
		}
	}
	
	public void setAspect(aspectType aspect)
	{
		this.aspect = aspect;
	}

	public void setAspect(String aspect)
	{
		if (aspect.toLowerCase().equals("fixed"))
		{
			this.aspect = aspectType.FIXED;
		}
		else
		{
			this.aspect = aspectType.VARIABLE;
		}
	}

	public boolean isRelativeScaling()
	{
		return relativeScaling;
	}

	private void setRelativeScaling(boolean relativeScaling) 
	{
		this.relativeScaling = relativeScaling;
	}

	public double getRelativeScalingRatio() 
	{
		return relativeScalingRatio;
	}

	private void setRelativeScalingRatio(double relativeScalingRatio, Svg2XmlGui gui) 
	{
		if (relativeScalingRatio>0)
		{
			if (gui.isRelativeScaling())
			{
				this.relativeScalingRatio = relativeScalingRatio;
			}
			else
			{
				this.relativeScalingRatio = 1.0;
			}
		}
	}

	public double getAbsoluteScalingX() 
	{
		return absoluteScalingX;
	}

	private void setAbsoluteScalingX(double absoluteScalingX) 
	{
		if (absoluteScalingX>0)
		{
			this.absoluteScalingX = absoluteScalingX;
		}
	}

	public double getAbsoluteScalingY() 
	{
		return absoluteScalingY;
	}

	private void setAbsoluteScalingY(double absoluteScalingY) 
	{
		if(absoluteScalingY>0)
		{
			this.absoluteScalingY = absoluteScalingY;
		}
	}

	public boolean isRoundCoords() 
	{
		return roundCoords;
	}

	private void setRoundCoords(boolean roundCoords) 
	{
		this.roundCoords = roundCoords;
	}

	public int getDecimalsToRound() 
	{
		return decimalsToRound;
	}

	private void setDecimalsToRound(int decimalsToRound) 
	{
		if (decimalsToRound>0)
		{
			this.decimalsToRound = decimalsToRound;
		}
		else
		{
			this.decimalsToRound = 0;
		}
	}

	public Connection getConnection() 
	{
		return connection;
	}

	public void setConnection(Connection connection) 
	{
		this.connection = connection;
	}

	public XmlStyle getStyle() 
	{
		return style;
	}

	public void setStyle(XmlStyle style) 
	{
		this.style = style;
	}

	public String getStrokewidth()
	{
		return strokewidth;
	}

	public void setStrokewidth(String strokewidth)
	{
		this.strokewidth = strokewidth;
	}

	public double getStencilBoundsX()
	{
		return stencilBoundsX;
	}

	public void setStencilBoundsX(double stencilBoundsX)
	{
		this.stencilBoundsX = stencilBoundsX;
	}

	public double getStencilBoundsY()
	{
		return stencilBoundsY;
	}

	public void setStencilBoundsY(double stencilBoundsY)
	{
		this.stencilBoundsY = stencilBoundsY;
	}

	public double getStencilBoundsMinX()
	{
		return stencilBoundsMinX;
	}

	public void setStencilBoundsMinX(double stencilBoundsMinX)
	{
		this.stencilBoundsMinX = stencilBoundsMinX;
	}

	public double getStencilBoundsMinY()
	{
		return stencilBoundsMinY;
	}

	public void setStencilBoundsMinY(double stencilBoundsMinY)
	{
		this.stencilBoundsMinY = stencilBoundsMinY;
	}
}
