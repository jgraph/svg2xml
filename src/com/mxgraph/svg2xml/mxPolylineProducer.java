/**
 * $Id: mxPolylineProducer.java,v 1.1 2013/04/10 15:32:23 mate Exp $
 * Copyright (c) 2011, David Benson, Gaudenz Alder
 */
package com.mxgraph.svg2xml;

import com.mxgraph.svgxml.svg.ParseException;
import com.mxgraph.svgxml.svg.PointsHandler;

/**
 * This class produces the XML for mxGraph stencils
 * for polylines
 */
public class mxPolylineProducer implements PointsHandler
{
	/**
	 * The string representing the lines
	 */
	protected String lines = null;
	
	protected String indent = "";

	/**
	 * @return the lines
	 */
	public String getLines()
	{
		return lines;
	}

	/**
	 * The winding rule to use to construct the path.
	 */
	protected int windingRule;

	/**
	 * Implements {@link PointsHandler#point(float,float)}.
	 */
	public void point(float x, float y) throws ParseException
	{
		if (lines == null)
		{
			lines = new String();
			lines += "<move x=\"" + x + "\" y=\"" + y + "\"/>\n";
		}
		else
		{
			lines += "<line x=\"" + x + "\" y=\"" + y + "\"/>\n";
		}
	}

	/**
	 * Implements {@link PointsHandler#endPoints()}.
	 */
	public void endPoints() throws ParseException
	{
	}

	public void startPoints() throws ParseException
	{
		// TODO Auto-generated method stub
		
	}
}
