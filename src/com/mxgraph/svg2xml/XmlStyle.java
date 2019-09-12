/**
 * $Id: XmlStyle.java,v 1.4 2013/04/17 17:32:55 mate Exp $
 * Copyright (c) 2013, JGraph
 */

package com.mxgraph.svg2xml;

/**
 * Complete XML style description	 
 */
public class XmlStyle
{
	private String strokeColor = "";
	private String fillColor = "";
	private String strokeWidth = "";
	private String lineJoin = "";
	private String lineCap = "";
	private String miterLimit = "";
	private String dashPattern = "";
	private String dashed = "";
	private String alpha = "";
	private String strokeAlpha = "";
	private String fillAlpha = "";
	private String fontColor = "";
	private String isFontStyleBold = "";
	private String isFontStyleItalic = "";
	private String isFontStyleUnderline = "";
	private String fontSize = "";
	private String fontFamily = "";
	private String align = "";
	private String vAlign = "";

	public String getAlign() 
	{
		return align;
	}
	
	public void setAlign(String align) 
	{
		if (align != null)
		{
			if (align.equals("start"))
			{
				this.align = "left";
			}
			else if (align.equals("end"))
			{
				this.align = "right";
			}
			else
			{
				this.align = align;
			}
		}
	}
	
	public String getVAlign() 
	{
		return vAlign;
	}
	
	public void setVAlign(String vAlign) 
	{
		if (vAlign != null)
		{
			this.vAlign = vAlign;
		}
	}
	
	public String getStrokeColor() 
	{
		return strokeColor;
	}
	
	public void setStrokeColor(String strokeColor) 
	{
		if (strokeColor != null)
		{
			this.strokeColor = strokeColor;
		}
	}
	
	public String getFillColor() 
	{
		if (fillColor != null)
		{
			return fillColor;
		}
		else
		{
			return "none";
		}
	}
	
	public void setFillColor(String fillColor) 
	{
		if(fillColor != null)
		{
			this.fillColor = fillColor;
		}
	}
	
	public String getStrokeWidth() 
	{
		return strokeWidth;
	}
	
	public void setStrokeWidth(String strokeWidth) 
	{
		if(strokeWidth != null)
		{
			this.strokeWidth = strokeWidth;
		}
	}
	
	public String getLineJoin() 
	{
		return lineJoin;
	}
	
	public void setLineJoin(String lineJoin) 
	{
		if(lineJoin != null)
		{
			this.lineJoin = lineJoin;
		}
	}

	public String getLineCap() 
	{
		return lineCap;
	}

	public void setLineCap(String lineCap) 
	{
		if(lineCap != null)
		{
			this.lineCap = lineCap;
		}
	}

	public String getMiterLimit() 
	{
		return miterLimit;
	}
	
	public void setMiterLimit(String miterLimit) 
	{
		if(miterLimit != null)
		{
			this.miterLimit = miterLimit;
		}
	}
	
	public String getDashPattern() 
	{
		return dashPattern;
	}

	//only arrays that have all values >0 are accepted
	public void setDashPattern(String dashPattern) 
	{
		if(dashPattern != null)
		{
			this.dashPattern = dashPattern;
		}
	}
	
	public String isDashed() 
	{
		return dashed;
	}
	
	public void setDashed(String dashed) 
	{
		if(dashed != null)
		{
			this.dashed = dashed;
		}
	}
	
	public String getAlpha() 
	{
		return alpha;
	}
	
	public String getStrokeAlpha() 
	{
		return strokeAlpha;
	}
	
	public String getFillAlpha() 
	{
		return fillAlpha;
	}
	
	public void setAlpha(String alpha) 
	{
		if(alpha != null)
		{
			this.alpha = alpha;
		}
	}
	
	public void setStrokeAlpha(String alpha) 
	{
		if(alpha != null)
		{
			this.strokeAlpha = alpha;
		}
	}

	public void setFillAlpha(String alpha) 
	{
		if(alpha != null)
		{
			this.fillAlpha = alpha;
		}
	}
	
	public String getFontColor() 
	{
		return fontColor;
	}
	
	public void setFontColor(String fontColor) 
	{
		if(fontColor != null)
		{
			this.fontColor = fontColor;
		}
	}

	public String isFontStyleBold() 
	{
		return isFontStyleBold;
	}
	
	public void setFontStyleBold(String isFontStyleBold) 
	{
		if(isFontStyleBold != null)
		{
			this.isFontStyleBold = isFontStyleBold;
		}
	}
	
	public String isFontStyleItalic() 
	{
		return isFontStyleItalic;
	}
	
	public void setFontStyleItalic(String isFontStyleItalic) 
	{
		if(isFontStyleItalic != null)
		{
			this.isFontStyleItalic = isFontStyleItalic;
		}
	}
	
	public String isFontStyleUnderline() 
	{
		return isFontStyleUnderline;
	}
	
	public void setFontStyleUnderline(String isFontStyleUnderline) 
	{
		if(isFontStyleUnderline != null)
		{
			this.isFontStyleUnderline = isFontStyleUnderline;
		}
	}
	
	public String getFontSize() 
	{
		return fontSize;
	}
	
	public void setFontSize(String fontSize) 
	{
		if(fontSize != null)
		{
			this.fontSize = fontSize;
		}
	}
	
	public String getFontFamily() 
	{
		return fontFamily;
	}
	
	public void setFontFamily(String fontFamily) 
	{
		this.fontFamily = fontFamily;
	}
}
