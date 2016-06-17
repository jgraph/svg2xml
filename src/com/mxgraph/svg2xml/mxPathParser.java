package com.mxgraph.svg2xml;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class mxPathParser
{
	/**
	 * Parent document
	 */
	private Document doc;
	
	/**
	 * Number of decimals used for rounding
	 */
	private int dtr;
	private boolean doRound = false;
	private Element pathEl;

	private double currentX = 0;
	private double currentY = 0;
	private double xCenter = 0;
	private double yCenter = 0;
	private double lastMoveX = 0;
	private double lastMoveY = 0;

	/**
	 * @param svgPath the "d" attribute of a SVG path element 
	 * @param parentDoc document where the XML element will be created
	 * @param decimals number of decimals used for rounding coordinates (use -1 to bypass rounding)
	 * @return An mxGraph XML equivalent of the SVG path element
	 */
	public Element createShape(String svgPath, Document parentDoc, int decimals)
	{
		this.doc = parentDoc;

		if (decimals >=0)
		{
			this.doRound = true;
			this.dtr = decimals;
		}

		this.pathEl = doc.createElement("path");
		char prevPathType = 'm';
		
		do
		{
			svgPath = svgPath.trim();
			int nextPartStartIndex; 

			char currPathType = svgPath.charAt(0);

			boolean hasLetter = true;
			
			if (!Character.isLetter(currPathType))
			{
				if (prevPathType == 'm')
				{
					currPathType = 'l';
				}
				else if (prevPathType == 'M')
				{
					currPathType = 'L';
				}
				else
				{
					currPathType = prevPathType;
				}
				
				hasLetter = false;
			}
			
			nextPartStartIndex = Shape2Xml.nextPartIndex(svgPath, currPathType);
			
			String currPathString = "";
			
			if (nextPartStartIndex == -1)
			{
				currPathString = svgPath;
			}
			else
			{
				currPathString = svgPath.substring(0, nextPartStartIndex);
			}

			if (!hasLetter)
			{
				currPathString = currPathType + " " + currPathString;
			}
			
			switch (currPathType)
			{
				case 0xD:
				case 0xA:
				case 0x20:
				case 0x9:
					break;
				case 'z':
				case 'Z':
					closePath();
					break;
				case 'm':
					movetoRel(currPathString);
					break;
				case 'M':
					movetoAbs(currPathString);
					break;
				case 'l':
					linetoRel(currPathString);
					break;
				case 'L':
					linetoAbs(currPathString);
					break;
				case 'h':
					linetoHorizontalRel(currPathString);
					break;
				case 'H':
					linetoHorizontalAbs(currPathString);
					break;
				case 'v':
					linetoVerticalRel(currPathString);
					break;
				case 'V':
					linetoVerticalAbs(currPathString);
					break;
				case 'c':
					curvetoCubicRel(currPathString);
					break;
				case 'C':
					curvetoCubicAbs(currPathString);
					break;
				case 'q':
					curvetoQuadraticRel(currPathString);
					break;
				case 'Q':
					curvetoQuadraticAbs(currPathString);
					break;
				case 's':
					curvetoCubicSmoothRel(currPathString);
					break;
				case 'S':
					curvetoCubicSmoothAbs(currPathString);
					break;
				case 't':
					curvetoQuadraticSmoothRel(currPathString);
					break;
				case 'T':
					curvetoQuadraticSmoothAbs(currPathString);
					break;
				case 'a':
					arcRel(currPathString);
					break;
				case 'A':
					arcAbs(currPathString);
					break;
				default:
					break;
			}
			
			if (nextPartStartIndex == -1)
			{
				svgPath = "";
			}
			else
			{
				svgPath = svgPath.substring(nextPartStartIndex, svgPath.length());
			}
		} 
		while (svgPath.length() > 0);

		return pathEl;
	}

	private void movetoRel(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);
		double y = Shape2Xml.getPathParam(path, 2);
		currentX = currentX + x;
		currentY = currentY + y;
		lastMoveX = currentX;
		lastMoveY = currentY;
		Element currChild = doc.createElement("move");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void movetoAbs(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);
		double y = Shape2Xml.getPathParam(path, 2);

		currentX = x;
		currentY = y;
		lastMoveX = currentX;
		lastMoveY = currentY;
		Element currChild = doc.createElement("move");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void closePath()
	{
		Element currChild = doc.createElement("close");
		pathEl.appendChild(currChild);
		currentX = lastMoveX;
		currentY = lastMoveY;
	}

	private void linetoRel(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);
		double y = Shape2Xml.getPathParam(path, 2);

		currentX = currentX + x;
		currentY = currentY + y;
		Element currChild = doc.createElement("line");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void linetoAbs(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);
		double y = Shape2Xml.getPathParam(path, 2);

		currentX = x;
		currentY = y;
		Element currChild = doc.createElement("line");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void linetoHorizontalRel(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);

		currentX = currentX + x;
		Element currChild = doc.createElement("line");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void linetoHorizontalAbs(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);

		currentX = x;
		Element currChild = doc.createElement("line");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void linetoVerticalRel(String path)
	{
		double y = Shape2Xml.getPathParam(path, 1);

		currentY = currentY + y;
		Element currChild = doc.createElement("line");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void linetoVerticalAbs(String path)
	{
		double y = Shape2Xml.getPathParam(path, 1);

		currentY = y;
		Element currChild = doc.createElement("line");
		currChild.setAttribute("x", Double.toString(rtd(currentX)));
		currChild.setAttribute("y", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void curvetoCubicRel(String path)
	{
		double x1 = Shape2Xml.getPathParam(path, 1);
		double y1 = Shape2Xml.getPathParam(path, 2);
		double x2 = Shape2Xml.getPathParam(path, 3);
		double y2 = Shape2Xml.getPathParam(path, 4);
		double x = Shape2Xml.getPathParam(path, 5);
		double y = Shape2Xml.getPathParam(path, 6);

		Element currChild = doc.createElement("curve");
		currChild.setAttribute("x1", Double.toString(rtd(currentX + x1)));
		currChild.setAttribute("y1", Double.toString(rtd(currentY + y1)));
		currChild.setAttribute("x2", Double.toString(rtd(xCenter = currentX + x2)));
		currChild.setAttribute("y2", Double.toString(rtd(yCenter = currentY + y2)));
		currChild.setAttribute("x3", Double.toString(rtd(currentX += x)));
		currChild.setAttribute("y3", Double.toString(rtd(currentY += y)));
		pathEl.appendChild(currChild);
	}

	private void curvetoCubicAbs(String path)
	{
		double x1 = Shape2Xml.getPathParam(path, 1);
		double y1 = Shape2Xml.getPathParam(path, 2);
		double x2 = Shape2Xml.getPathParam(path, 3);
		double y2 = Shape2Xml.getPathParam(path, 4);
		double x = Shape2Xml.getPathParam(path, 5);
		double y = Shape2Xml.getPathParam(path, 6);

		xCenter = x2;
		yCenter = y2;
		currentX = x;
		currentY = y;
		Element currChild = doc.createElement("curve");
		currChild.setAttribute("x1", Double.toString(rtd(x1)));
		currChild.setAttribute("y1", Double.toString(rtd(y1)));
		currChild.setAttribute("x2", Double.toString(rtd(x2)));
		currChild.setAttribute("y2", Double.toString(rtd(y2)));
		currChild.setAttribute("x3", Double.toString(rtd(currentX)));
		currChild.setAttribute("y3", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void curvetoCubicSmoothRel(String path)
	{
		double x2 = Shape2Xml.getPathParam(path, 1);
		double y2 = Shape2Xml.getPathParam(path, 2);
		double x = Shape2Xml.getPathParam(path, 3);
		double y = Shape2Xml.getPathParam(path, 4);

		Element currChild = doc.createElement("curve");
		currChild.setAttribute("x1", Double.toString(rtd(currentX * 2 - xCenter)));
		currChild.setAttribute("y1", Double.toString(rtd(currentY * 2 - yCenter)));
		currChild.setAttribute("x2", Double.toString(rtd(xCenter = currentX + x2)));
		currChild.setAttribute("y2", Double.toString(rtd(yCenter = currentY + y2)));
		currChild.setAttribute("x3", Double.toString(rtd(currentX += x)));
		currChild.setAttribute("y3", Double.toString(rtd(currentY += y)));
		pathEl.appendChild(currChild);
	}

	private void curvetoCubicSmoothAbs(String path)
	{
		double x2 = Shape2Xml.getPathParam(path, 1);
		double y2 = Shape2Xml.getPathParam(path, 2);
		double x = Shape2Xml.getPathParam(path, 3);
		double y = Shape2Xml.getPathParam(path, 4);

		Element currChild = doc.createElement("curve");
		currChild.setAttribute("x1", Double.toString(rtd(currentX * 2 - xCenter)));
		currChild.setAttribute("y1", Double.toString(rtd(currentY * 2 - yCenter)));
		currChild.setAttribute("x2", Double.toString(rtd(xCenter = x2)));
		currChild.setAttribute("y2", Double.toString(rtd(yCenter = y2)));
		currChild.setAttribute("x3", Double.toString(rtd(currentX = x)));
		currChild.setAttribute("y3", Double.toString(rtd(currentY = y)));
		pathEl.appendChild(currChild);
	}

	private void curvetoQuadraticRel(String path)
	{
		double x1 = Shape2Xml.getPathParam(path, 1);
		double y1 = Shape2Xml.getPathParam(path, 2);
		double x = Shape2Xml.getPathParam(path, 3);
		double y = Shape2Xml.getPathParam(path, 4);

		xCenter = currentX + x1;
		yCenter = currentY + y1;
		Element currChild = doc.createElement("quad");
		currChild.setAttribute("x1", Double.toString(rtd(currentX + x1)));
		currChild.setAttribute("y1", Double.toString(rtd(currentY + y1)));
		currChild.setAttribute("x2", Double.toString(rtd(currentX + x)));
		currChild.setAttribute("y2", Double.toString(rtd(currentY + y)));
		pathEl.appendChild(currChild);
		currentX = currentX + x;
		currentY = currentY + y;
	}

	private void curvetoQuadraticAbs(String path)
	{
		double x1 = Shape2Xml.getPathParam(path, 1);
		double y1 = Shape2Xml.getPathParam(path, 2);
		double x = Shape2Xml.getPathParam(path, 3);
		double y = Shape2Xml.getPathParam(path, 4);

		xCenter = x1;
		yCenter = y1;
		Element currChild = doc.createElement("quad");
		currChild.setAttribute("x1", Double.toString(rtd(x1)));
		currChild.setAttribute("y1", Double.toString(rtd(y1)));
		currChild.setAttribute("x2", Double.toString(rtd(x)));
		currChild.setAttribute("y2", Double.toString(rtd(y)));
		pathEl.appendChild(currChild);
		currentX = x;
		currentY = y;
	}

	private void curvetoQuadraticSmoothRel(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);
		double y = Shape2Xml.getPathParam(path, 2);

		xCenter = currentX * 2 - xCenter;
		yCenter = currentY * 2 - yCenter;
		currentX = currentX + x;
		currentY = currentY + y;
		Element currChild = doc.createElement("quad");
		currChild.setAttribute("x1", Double.toString(rtd(xCenter)));
		currChild.setAttribute("y1", Double.toString(rtd(yCenter)));
		currChild.setAttribute("x2", Double.toString(rtd(currentX)));
		currChild.setAttribute("y2", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void curvetoQuadraticSmoothAbs(String path)
	{
		double x = Shape2Xml.getPathParam(path, 1);
		double y = Shape2Xml.getPathParam(path, 2);

		xCenter = currentX * 2 - xCenter;
		yCenter = currentY * 2 - yCenter;
		currentX = x;
		currentY = y;
		Element currChild = doc.createElement("quad");
		currChild.setAttribute("x1", Double.toString(rtd(xCenter)));
		currChild.setAttribute("y1", Double.toString(rtd(yCenter)));
		currChild.setAttribute("x2", Double.toString(rtd(currentX)));
		currChild.setAttribute("y2", Double.toString(rtd(currentY)));
		pathEl.appendChild(currChild);
	}

	private void arcRel(String path)
	{
		double rx = Shape2Xml.getPathParam(path, 1);
		double ry = Shape2Xml.getPathParam(path, 2);
		double ax = Shape2Xml.getPathParam(path, 3);
		double laf = Shape2Xml.getPathParam(path, 4);
		double sf = Shape2Xml.getPathParam(path, 5);
		double x = Shape2Xml.getPathParam(path, 6);
		double y = Shape2Xml.getPathParam(path, 7);

		if (rx > 0 && ry > 0)
		{
			Element currChild = doc.createElement("arc");
			currChild.setAttribute("rx", Double.toString(rtd(rx)));
			currChild.setAttribute("ry", Double.toString(rtd(ry)));
			currChild.setAttribute("x-axis-rotation", Double.toString(rtd(ax)));
			currChild.setAttribute("large-arc-flag", Double.toString(laf));
			currChild.setAttribute("sweep-flag", Double.toString(sf));
			currChild.setAttribute("x", Double.toString(rtd(x+currentX)));
			currChild.setAttribute("y", Double.toString(rtd(y+currentY)));
			pathEl.appendChild(currChild);
			xCenter = rtd(currentX += x);
			yCenter = rtd(currentY += y);
		}
		else
		{
			currentX = currentX + x;
			currentY = currentY + y;
			Element currChild = doc.createElement("line");
			currChild.setAttribute("x", Double.toString(rtd(currentX)));
			currChild.setAttribute("y", Double.toString(rtd(currentY)));
			pathEl.appendChild(currChild);
		}
	}
	
	private void arcAbs(String path)
	{
		double rx = Shape2Xml.getPathParam(path, 1);
		double ry = Shape2Xml.getPathParam(path, 2);
		double ax = Shape2Xml.getPathParam(path, 3);
		double laf = Shape2Xml.getPathParam(path, 4);
		double sf = Shape2Xml.getPathParam(path, 5);
		double x = Shape2Xml.getPathParam(path, 6);
		double y = Shape2Xml.getPathParam(path, 7);

		if (rx > 0 && ry > 0)
		{
			Element currChild = doc.createElement("arc");
			currChild.setAttribute("rx", Double.toString(rtd(rx)));
			currChild.setAttribute("ry", Double.toString(rtd(ry)));
			currChild.setAttribute("x-axis-rotation", Double.toString(rtd(ax)));
			currChild.setAttribute("large-arc-flag", Double.toString(laf));
			currChild.setAttribute("sweep-flag", Double.toString(sf));
			currChild.setAttribute("x", Double.toString(rtd(x)));
			currChild.setAttribute("y", Double.toString(rtd(y)));
			pathEl.appendChild(currChild);
			xCenter = currentX = rtd(x);
			yCenter = currentY = rtd(y);
		}
		else
		{
			currentX = x;
			currentY = y;
			Element currChild = doc.createElement("line");
			currChild.setAttribute("x", Double.toString(rtd(currentX)));
			currChild.setAttribute("y", Double.toString(rtd(currentY)));
			pathEl.appendChild(currChild);
		}
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
	}
}
