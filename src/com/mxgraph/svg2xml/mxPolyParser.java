package com.mxgraph.svg2xml;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class mxPolyParser
{
	private int dtr;
	private boolean doRound = false;
	
	public Element createShape(String svgPoly, Document parentDoc, int decimals, String tagName)
	{
		if (decimals >=0)
		{
			this.doRound = true;
			this.dtr = decimals;
		}
		
		Element pathEl = parentDoc.createElement("path");

		double x = 0;
		double y = 0;

		//a loop that reads the coords
		int commaIndex = svgPoly.indexOf(",");
		int spaceIndex = svgPoly.indexOf(" ", commaIndex+1);

		if (spaceIndex == -1)
		{
			spaceIndex = svgPoly.length();
		}

		int i = 0;
		
		while ((commaIndex != -1) && (spaceIndex > commaIndex))
		{
			
			//read x
			String xString = svgPoly.substring(0, commaIndex);
			//read y
			String yString = svgPoly.substring(commaIndex + 1, spaceIndex);

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);

			Element currChild = null;
			
			if (i == 0)
			{
				currChild = parentDoc.createElement("move");
			}
			else
			{
				currChild = parentDoc.createElement("line");
			}
			
			currChild.setAttribute("x", Double.toString(rtd(x)));
			currChild.setAttribute("y", Double.toString(rtd(y)));
			pathEl.appendChild(currChild);

			svgPoly = svgPoly.substring(spaceIndex, svgPoly.length());

			commaIndex = svgPoly.indexOf(",");
			spaceIndex = svgPoly.indexOf(" ", commaIndex + 1);

			if (spaceIndex==-1)
			{
				spaceIndex = svgPoly.length();
			}
			
			i++;
		}

		if (tagName.toLowerCase().equals("polygon"))
		{
			pathEl.appendChild(parentDoc.createElement("close"));
		}

		return pathEl;
	}
	
	/**
	 * @param d number to round
	 * @param c decimals to round to
	 * @return rounded <b>d</b> to <b>c</b> decimals
	 */
	private double rtd(double d) 
	{
		if (doRound)
		{
			BigDecimal temp = new BigDecimal(Double.toString(d));
			temp = temp.setScale(dtr, RoundingMode.HALF_EVEN);
			return temp.doubleValue();
		}
		else
		{
			return d;
		}
	}}
