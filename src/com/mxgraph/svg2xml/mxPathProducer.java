/*

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package com.mxgraph.svg2xml;

import com.mxgraph.util.svg.ExtendedGeneralPath;
import com.mxgraph.util.svg.ParseException;
import com.mxgraph.util.svg.PathHandler;
import com.mxgraph.util.svg.PathParser;

/**
 * This class provides an implementation of the PathHandler that initializes
 * a Shape from the value of a path's 'd' attribute.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 * @version $Id: mxPathProducer.java,v 1.1 2013/04/10 15:31:52 mate Exp $
 */
public class mxPathProducer implements PathHandler
{

	/**
	 * The temporary value of extendedGeneralPath.
	 */
	protected ExtendedGeneralPath path;

	/**
	 * The current x position.
	 */
	protected float currentX;

	/**
	 * The current y position.
	 */
	protected float currentY;

	/**
	 * The reference x point for smooth arcs.
	 */

	protected float lastMoveX=0;

	/**
	 * The current y position.
	 */
	protected float lastMoveY=0;

	/**
	 * The reference x point for smooth arcs.
	 */
protected float xCenter;

	/**
	 * The reference y point for smooth arcs.
	 */
	protected float yCenter;

	/**
	 * The string representing the lines
	 */
	protected StringBuilder lines = null;

	/**
	 * @return the lines
	 */
	public String getXMLString()
	{
		return lines.toString();
	}
	
	/**
	 * Utility method for creating an ExtendedGeneralPath.
	 * @param text The text representation of the path specification.
	 * @param wr The winding rule to use for creating the path.
	 */
	public static String createShape(String text) throws ParseException
	{
		mxPathProducer ph = new mxPathProducer();

		PathParser p = new PathParser(ph);
		p.parse(text);

		return ph.getXMLString();
	}

	/**
	 * Implements {@link PathHandler#startPath()}.
	 */
	public void startPath() throws ParseException
	{
		currentX = 0;
		currentY = 0;
		xCenter = 0;
		yCenter = 0;
		lines = new StringBuilder();;
		lines.append("<path>\n");
	}

	/**
	 * Implements {@link PathHandler#endPath()}.
	 */
	public void endPath() throws ParseException
	{
		lines.append("</path>\n");
	}

	/**
	 * Implements {@link PathHandler#movetoRel(float,float)}.
	 */
	public void movetoRel(float x, float y) throws ParseException
	{
		currentX += x;
		currentY += y;
		lastMoveX = currentX;
		lastMoveY = currentY;
		lines.append("<move x=\"" + (currentX) + "\" y=\"" + (currentY) + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#movetoAbs(float,float)}.
	 */
	public void movetoAbs(float x, float y) throws ParseException
	{
		currentX = x;
		currentY = y;
		lastMoveX = currentX;
		lastMoveY = currentY;
		lines.append("<move x=\"" + currentX + "\" y=\"" + currentY + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#closePath()}.
	 */
	public void closePath() throws ParseException
	{
		
// TODO: need to solve the currentX and currentY update on close, but then we need to find the path start coords. (AFAIK close closes to the last move command)		
		lines.append("<close/>\n");
		currentX = lastMoveX;
		currentY = lastMoveY;
//		Point2D pt = path.getCurrentPoint();
//		currentX = (float) pt.getX();
//		currentY = (float) pt.getY();
	}

	/**
	 * Implements {@link PathHandler#linetoRel(float,float)}.
	 */
	public void linetoRel(float x, float y) throws ParseException
	{
		currentX += x;
		currentY += y;
		lines.append("<line x=\"" + (currentX) + "\" y=\"" + (currentY) + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#linetoAbs(float,float)}.
	 */
	public void linetoAbs(float x, float y) throws ParseException
	{
		currentX = x;
		currentY = y;
		lines.append("<line x=\"" + x + "\" y=\"" + y + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#linetoHorizontalRel(float)}.
	 */
	public void linetoHorizontalRel(float x) throws ParseException
	{
		currentX += x;
		lines.append("<line x=\"" + (currentX) + "\" y=\"" + currentY + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#linetoHorizontalAbs(float)}.
	 */
	public void linetoHorizontalAbs(float x) throws ParseException
	{
		currentX = x;
		lines.append("<line x=\"" + currentX + "\" y=\"" + currentY + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#linetoVerticalRel(float)}.
	 */
	public void linetoVerticalRel(float y) throws ParseException
	{
		currentY += y;
		lines.append("<line x=\"" + currentX + "\" y=\"" + currentY + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#linetoVerticalAbs(float)}.
	 */
	public void linetoVerticalAbs(float y) throws ParseException
	{
		currentY = y;
		lines.append("<line x=\"" + currentX + "\" y=\"" + currentY + "\"/>\n");
	}

	/**
	 * Implements {@link
	 * PathHandler#curvetoCubicRel(float,float,float,float,float,float)}.
	 */
	public void curvetoCubicRel(float x1, float y1, float x2, float y2,
			float x, float y) throws ParseException
	{
		lines.append("<curve x1=\"" + (currentX + x1) + "\" y1=\"" + (currentY + y1) + "\" x2=\"" + (xCenter = currentX + x2) + "\" y2=\"" + (yCenter = currentY + y2) + "\" x3=\"" + (currentX += x) + "\" y3=\"" + (currentY += y) + "\"/>\n");
		}

	/**
	 * Implements {@link
	 * PathHandler#curvetoCubicAbs(float,float,float,float,float,float)}.
	 */
	public void curvetoCubicAbs(float x1, float y1, float x2, float y2,
			float x, float y) throws ParseException
	{
		xCenter = x2;
		yCenter = y2;
		currentX = x;
		currentY = y;
		lines.append("<curve x1=\"" + x1 + "\" y1=\"" + y1 + "\" x2=\"" + x2 + "\" y2=\"" + y2 + "\" x3=\"" + currentX + "\" y3=\"" + currentY + "\"/>\n");
	}

	/**
	 * Implements
	 * {@link PathHandler#curvetoCubicSmoothRel(float,float,float,float)}.
	 */
	public void curvetoCubicSmoothRel(float x2, float y2, float x, float y)
			throws ParseException
	{
		lines.append("<curve x1=\"" + (currentX * 2 - xCenter) + "\" y1=\"" + (currentY * 2 - yCenter) + "\" x2=\"" + (xCenter = currentX + x2) + "\" y2=\"" + (yCenter = currentY + y2) + "\" x3=\"" + (currentX += x) + "\" y3=\"" + (currentY += y) + "\"/>\n");
	}

	/**
	 * Implements
	 * {@link PathHandler#curvetoCubicSmoothAbs(float,float,float,float)}.
	 */
	public void curvetoCubicSmoothAbs(float x2, float y2, float x, float y)
			throws ParseException
	{
		lines.append("<curve x1=\"" + (currentX * 2 - xCenter) + "\" y1=\"" + (currentY * 2 - yCenter) + "\" x2=\"" + (xCenter = x2) + "\" y2=\"" + (yCenter = y2) + "\" x3=\"" + (currentX = x) + "\" y3=\"" + (currentY = y) + "\"/>\n");
	}

	/**
	 * Implements
	 * {@link PathHandler#curvetoQuadraticRel(float,float,float,float)}.
	 */
	public void curvetoQuadraticRel(float x1, float y1, float x, float y)
			throws ParseException
	{
		xCenter = currentX + x1;
		yCenter = currentY + y1;
		lines.append("<quad x1=\"" + (currentX + x1) + "\" y1=\"" + (currentY + y1) + "\" x2=\"" + (currentX + x) + "\" y2=\"" + (currentY + y) + "\"/>\n");
		currentX += x;
		currentY += y;
	}

	/**
	 * Implements
	 * {@link PathHandler#curvetoQuadraticAbs(float,float,float,float)}.
	 */
	public void curvetoQuadraticAbs(float x1, float y1, float x, float y)
			throws ParseException
	{
		xCenter = x1;
		yCenter = y1;
		lines.append("<quad x1=\"" + x1 + "\" y1=\"" + y1 + "\" x2=\"" + x + "\" y2=\"" + y + "\"/>\n");
		currentX = x;
		currentY = y;
	}

	/**
	 * Implements {@link PathHandler#curvetoQuadraticSmoothRel(float,float)}.
	 */
	public void curvetoQuadraticSmoothRel(float x, float y)
			throws ParseException
	{
		xCenter = currentX * 2 - xCenter;
		yCenter = currentY * 2 - yCenter;
		currentX += x;
		currentY += y;
		lines.append("<quad x1=\"" + (xCenter) + "\" y1=\"" + (yCenter) + "\" x2=\"" + (currentX) + "\" y2=\"" + (currentY) + "\"/>\n");
	}

	/**
	 * Implements {@link PathHandler#curvetoQuadraticSmoothAbs(float,float)}.
	 */
	public void curvetoQuadraticSmoothAbs(float x, float y)
			throws ParseException
	{
		xCenter = currentX * 2 - xCenter;
		yCenter = currentY * 2 - yCenter;
		currentX = x;
		currentY = y;
		lines.append("<quad x1=\"" + (xCenter) + "\" y1=\"" + (yCenter) + "\" x2=\"" + (currentX) + "\" y2=\"" + (currentY) + "\"/>\n");
	}

	/**
	 * Implements {@link
	 * PathHandler#arcRel(float,float,float,boolean,boolean,float,float)}.
	 */
	public void arcRel(float rx, float ry, float xAxisRotation,
			boolean largeArcFlag, boolean sweepFlag, float x, float y)
			throws ParseException
	{
		String largeArcFlagStr = largeArcFlag ? "1" : "0";
		String sweepFlagStr = sweepFlag ? "1" : "0";
		lines.append("<arc rx=\"" + rx + "\" ry=\"" + ry + "\" x-axis-rotation=\"" + xAxisRotation + "\" large-arc-flag=\"" + largeArcFlagStr + "\" sweep-flag=\"" + sweepFlagStr + "\" x=\"" + (x+currentX) + "\" y=\"" + (y+currentY) + "\"/>\n");
		xCenter = currentX += x;
		yCenter = currentY += y;
	}

	/**
	 * Implements {@link
	 * PathHandler#arcAbs(float,float,float,boolean,boolean,float,float)}.
	 */
	public void arcAbs(float rx, float ry, float xAxisRotation,
			boolean largeArcFlag, boolean sweepFlag, float x, float y)
			throws ParseException
	{
		String largeArcFlagStr = largeArcFlag ? "1" : "0";
		String sweepFlagStr = sweepFlag ? "1" : "0";
		lines.append("<arc rx=\"" + rx + "\" ry=\"" + ry + "\" x-axis-rotation=\"" + xAxisRotation + "\" large-arc-flag=\"" + largeArcFlagStr + "\" sweep-flag=\"" + sweepFlagStr + "\" x=\"" + (x) + "\" y=\"" + (y) + "\"/>\n");
		xCenter = currentX = x;
		yCenter = currentY = y;
		}
}
