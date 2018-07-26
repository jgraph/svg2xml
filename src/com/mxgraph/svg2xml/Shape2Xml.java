/**
 * $Id: Shape2Xml.java,v 1.17 2013/06/26 09:20:27 mate Exp $
 * Copyright (c) 2013, JGraph
 */

package com.mxgraph.svg2xml;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Transforms a SVG shape element to a JGraph XML element or elements, if necessary
 *
 */
public class Shape2Xml 
{
	protected static double lastPathX=0; // where the last path part ended
	protected static double lastPathY=0; 
	protected static double prevPathX=0; // where the last path part ended before transforming
	protected static double prevPathY=0;
	protected static double lastMoveX=0; // where the last move ended
	protected static double lastMoveY=0; 
	protected static double prevMoveX=0; // where the last move ended before transforming
	protected static double prevMoveY=0;
	
	/**
	 * Parses a SVG element to an stencil XML element
	 * @param element SVG element that is to be parsed
	 * @param xmlDoc taget stencil XML document
	 * @param configDoc config data of <b>xmlDoc</b>
	 * @return Element in <b>xmlDoc</b>
	 */
	public static Element parse(Element element, Document xmlDoc, XmlConfig configDoc)
	{
		double s = configDoc.getRelativeScalingRatio();
		
		if (element.getNodeName().equals("rect"))
		{
			if (element.getAttribute("transform") != null && !element.getAttribute("transform").equals(""))
			{
				//transformed rect or roundrect
				return matrixTransformRect(element, xmlDoc, configDoc);
			}

			int dn = configDoc.getDecimalsToRound();
			double x = getDoubleAttribute(element, "x");
			double y = getDoubleAttribute(element, "y");
			double w = getDoubleAttribute(element, "width");
			double h = getDoubleAttribute(element, "height");
			double rx = getDoubleAttribute(element, "rx");
			double ry = getDoubleAttribute(element, "ry");

			x = x - configDoc.getStencilBoundsMinX();
			y = y - configDoc.getStencilBoundsMinY();

			x = roundToDecimals(x * s, dn);
			y = roundToDecimals(y * s, dn);
			w = roundToDecimals(w * s, dn);
			h = roundToDecimals(h * s, dn);
			rx = roundToDecimals(rx * s, dn);
			ry = roundToDecimals(ry * s, dn);

			if(rx == 0 && ry == 0)
			{
				//untransformed rect
				Element el = xmlDoc.createElement("rect");
				el.setAttribute("x", String.valueOf(x));
				el.setAttribute("y", String.valueOf(y));
				el.setAttribute("w", String.valueOf(w));
				el.setAttribute("h", String.valueOf(h));
				return el;
			}
			else if (rx != 0 || ry != 0)
			{
				//untransformed roundrect
				Element el = xmlDoc.createElement("roundrect");
				el.setAttribute("x", String.valueOf(x));
				el.setAttribute("y", String.valueOf(y));
				el.setAttribute("w", String.valueOf(w));
				el.setAttribute("h", String.valueOf(h));
				double arcSize = 0;
				double r = (rx + ry) / 2;
				double dim = Math.min(w, h);
				arcSize = Math.min(r / dim * 100, 50);
				arcSize = Math.max(arcSize, 0);
				arcSize = roundToDecimals(arcSize, dn);
				el.setAttribute("arcsize", String.valueOf(arcSize));
				return el;
			}
		}
		else if (element.getNodeName().equals("line"))
		{
			Double[] tr = getTransform(element);

			double x1 = getDoubleAttribute(element, "x1");
			double y1 = getDoubleAttribute(element, "y1");
			double x2 = getDoubleAttribute(element, "x2");
			double y2 = getDoubleAttribute(element, "y2");

			String pathString =  "M " + x1 + " " + y1 + " L " + x2 + " " + y2;

			pathString = matrixTransformPath(pathString, tr, configDoc);
			double dx = configDoc.getStencilBoundsMinX();
			double dy = configDoc.getStencilBoundsMinY();
			pathString = matrixTransformPath(pathString, new Double[]{1.0, 0.0, 0.0, 1.0, -dx, -dy}, configDoc);
			pathString = matrixTransformPath(pathString, new Double[]{s, 0.0, 0.0, s, 0.0, 0.0}, configDoc);

			mxPathParser pp = new mxPathParser();
			return pp.createShape(pathString, xmlDoc, configDoc.getDecimalsToRound());
		}
		else if (element.getNodeName().equals("polyline") || element.getNodeName().equals("polygon"))
		{
			if (element.getAttribute("transform") != null && !element.getAttribute("transform").equals(""))
			{
				element = matrixTransformPoly(element, configDoc);
			}

			String pointsString = element.getAttribute("points");
			int dn = configDoc.getDecimalsToRound();
			
			pointsString = pointsString.replaceAll("\\s{2,}", " ");
			pointsString = pointsString.replaceAll("E -", "E-");
			pointsString = pointsString.replaceAll("e -", "e-");

			String newPointsString = "";
			double x = 0;
			double y = 0;
			String xString;
			String yString;
			double xNew;
			double yNew;

			//a loop that reads the coords
			int commaIndex = pointsString.indexOf(",");
			int spaceIndex = pointsString.indexOf(" ", commaIndex+1);

			if (spaceIndex == -1)
			{
				spaceIndex = pointsString.length();
			}

			if (commaIndex != -1)
			{
				while ((commaIndex != -1) && (spaceIndex > commaIndex))
				{
					//read x
					xString = pointsString.substring(0, commaIndex);
					//read y
					yString = pointsString.substring(commaIndex + 1, spaceIndex);
	
					x = Double.valueOf(xString);
					y = Double.valueOf(yString);
					x = x - configDoc.getStencilBoundsMinX();
					y = y - configDoc.getStencilBoundsMinY();
	
					// add the new coords to the new string
					xNew = roundToDecimals(x * s, dn);
					yNew = roundToDecimals(y * s, dn);
	
					newPointsString += xNew + "," + yNew + " ";
	
					pointsString = pointsString.substring(spaceIndex, pointsString.length());
	
					commaIndex = pointsString.indexOf(",");
					spaceIndex = pointsString.indexOf(" ", commaIndex + 1);
	
					if (spaceIndex==-1)
					{
						spaceIndex = pointsString.length();
					}
				}
			}
			else if (spaceIndex > -1)
			{
				spaceIndex = pointsString.indexOf(" ");
				int spaceIndex2 = pointsString.indexOf(" ", spaceIndex + 1);
				
				while (spaceIndex2 != spaceIndex)
				{
					//read x
					xString = pointsString.substring(0, spaceIndex);
					//read y
					yString = pointsString.substring(spaceIndex + 1, spaceIndex2);
	
					x = Double.valueOf(xString);
					y = Double.valueOf(yString);
					x = x - configDoc.getStencilBoundsMinX();
					y = y - configDoc.getStencilBoundsMinY();
	
					// add the new coords to the new string
					xNew = roundToDecimals(x * s, dn);
					yNew = roundToDecimals(y * s, dn);
	
					newPointsString += xNew + "," + yNew + " ";
	
					pointsString = pointsString.substring(Math.min(spaceIndex2 + 1, pointsString.length()), pointsString.length());
	
					spaceIndex = pointsString.indexOf(" ");
					spaceIndex2 = pointsString.indexOf(" ", spaceIndex + 1);
	
					if (spaceIndex==-1)
					{
						spaceIndex = pointsString.length();
					}
					
					if (spaceIndex2==-1)
					{
						spaceIndex2 = pointsString.length();
					}
				}
			}
			
			newPointsString = newPointsString.substring(0, (newPointsString.length() - 1));
			newPointsString = setPathRoot(newPointsString, configDoc);
//			String polyXML = "<path>" + System.getProperty("line.separator");
			//			mxPolylineProducer ph = new mxPolylineProducer();
			//			PointsParser p = new PointsParser(ph);
			//			p.parse(newPointsString, configDoc.getDecimalsToRound());
			//			polyXML += ph.getLines();

//			if (element.getNodeName().equals("polygon"))
//			{
//				polyXML += "<close/>" + System.getProperty("line.separator");
//			}
//
//			polyXML += "</path>" + System.getProperty("line.separator");
			mxPolyParser pp = new mxPolyParser();
			return pp.createShape(newPointsString, xmlDoc, dn, element.getNodeName());
//			return returnXmlFragment(xmlDoc, polyXML);
		}
		else if (element.getNodeName().equals("circle") || element.getNodeName().equals("ellipse"))
		{
			boolean isCircle = false;
			int dn = configDoc.getDecimalsToRound();

			if (element.getNodeName().equals("circle"))
			{
				isCircle = true;
			}

			Double[] tr = getTransform(element);

			double cx = getDoubleAttribute(element, "cx");
			double cy = getDoubleAttribute(element, "cy");
			double r = 0;
			double rx = 0;
			double ry = 0;

			if (isCircle)
			{
				r = getDoubleAttribute(element, "r");

				rx = roundToDecimals(r, dn);
				ry = rx;
			}
			else
			{
				rx = getDoubleAttribute(element, "rx");
				ry = getDoubleAttribute(element, "ry");
			}

			if (r < 0 || rx < 0 || ry < 0)
			{
				return null; // error in SVG spec
			}

			if (tr != null)
			{
				double x1 = cx;
				double y1 = cy-ry;
				double x2 = cx+rx;
				double y2 = cy;
				double x3 = cx;
				double y3 = cy+ry;
				double x4 = cx-rx;
				double y4 = cy;

				String ellString = "M " + x1 + " " + y1 + 
						" A " + rx + " " + ry + " 0 0 1 " + x2 + " " + y2 + 
						" A " + rx + " " + ry + " 0 0 1 " + x3 + " " + y3 + 
						" A " + rx + " " + ry + " 0 0 1 " + x4 + " " + y4 + 
						" A " + rx + " " + ry + " 0 0 1 " + x1 + " " + y1 + " Z ";

				ellString = matrixTransformPath(ellString, tr, configDoc);
				double dx = configDoc.getStencilBoundsMinX();
				double dy = configDoc.getStencilBoundsMinY();
				ellString = matrixTransformPath(ellString, new Double[]{1.0, 0.0, 0.0, 1.0, -dx, -dy}, configDoc);
				ellString = matrixTransformPath(ellString, new Double[]{s, 0.0, 0.0, s, 0.0, 0.0}, configDoc);
				mxPathParser pp = new mxPathParser();
				return pp.createShape(ellString, xmlDoc, configDoc.getDecimalsToRound());

			}
			else
			{
				//untransformed circle/ellipse
				Element ellipse = xmlDoc.createElement("ellipse");

				int rd = configDoc.getDecimalsToRound();
				cx = cx - configDoc.getStencilBoundsMinX();
				cy = cy - configDoc.getStencilBoundsMinY();
				cx = cx * s;
				cy = cy * s;
				rx = rx * s;
				ry = ry * s;

				ellipse.setAttribute("x", String.valueOf(roundToDecimals(cx - rx, rd)));
				ellipse.setAttribute("y", String.valueOf(roundToDecimals(cy - ry, rd)));
				ellipse.setAttribute("w", String.valueOf(roundToDecimals(2 * rx, rd)));
				ellipse.setAttribute("h", String.valueOf(roundToDecimals(2 * ry, rd)));
				return ellipse;
			}
		}
		else if (element.getNodeName().equals("path"))
		{
			String path = element.getAttribute("d");
			path = path.replaceAll("(?=[a-zA-Z])", " ");
			path = path.replaceAll("(?<=[a-zA-Z])", " ");
			path = path.replaceAll(",", " ");
			path = path.replaceAll("-", " -");
			path = path.replaceAll("\\s{2,}", " ");
			path = path.replaceAll("E -", "E-");
			path = path.replaceAll("e -", "e-");
			element.setAttribute("d", path);

			Double[] tr = getTransform(element);

			path = matrixTransformPath(path, tr, configDoc);
			double dx = configDoc.getStencilBoundsMinX();
			double dy = configDoc.getStencilBoundsMinY();
			path = matrixTransformPath(path, new Double[]{1.0, 0.0, 0.0, 1.0, -dx, -dy}, configDoc);
			path = matrixTransformPath(path, new Double[]{s, 0.0, 0.0, s, 0.0, 0.0}, configDoc);
			mxPathParser pp = new mxPathParser();
			return pp.createShape(path, xmlDoc, configDoc.getDecimalsToRound());
		}

		return null;
	}

	/**
	 * Text is parsed differently, because it generates a more complex XML structure than other shapes. This function handles everything that goes into the stencil XML regarding the current text shape, not just geometry. Shape2Xml.parse() returns just geometry, so these two are handled differently.
	 * @param element text element that needs parsing
	 * @param xmlDoc the resulting elements will be created in this document
	 * @param configDoc config document for the target stencil XML
	 * @param isFirst is the current text element the first shape in the stencil (if yes, it will be inserted in the background block)
	 */
	public static void parseText(Element element, Document xmlDoc, XmlConfig configDoc, boolean isFirst)
	{
		XmlStyle style = Svg2Xml.getStyle(element);
		double rot = 0;
		double s = configDoc.getRelativeScalingRatio();

		if (element.getAttribute("transform") != null && !element.getAttribute("transform").equals(""))
		{
			//transformed text, but only x,y are transformed here
			matrixTransformPoint(element, xmlDoc, configDoc);
		}

		String xS = element.getAttribute("x");
		String yS = element.getAttribute("y");

		double x = Double.valueOf(xS);
		double y = Double.valueOf(yS);

		if (xS.length() > 0)
		{
			x = Double.parseDouble(xS);
		}

		if (xS.length() > 0)
		{
			x = Double.parseDouble(xS);
		}

		int rd = configDoc.getDecimalsToRound();
		x = x - configDoc.getStencilBoundsMinX();
		y = y - configDoc.getStencilBoundsMinY();
		x = roundToDecimals(x * s, rd);
		y = roundToDecimals(y * s, rd);

		//create, populate and append the text element
		Element textEl = xmlDoc.createElement("text");

		String str = "";

		NodeList textChildren = element.getChildNodes();

		for (int i = 0; i < textChildren.getLength(); ++i)
		{
			Node child = textChildren.item(i);

			if (child.getNodeType() == Node.TEXT_NODE)
			{
				str = str + child.getTextContent();
			}
		}


		if (str != null && !str.equals(""))
		{
			textEl.setAttribute("str", str);
			textEl.setAttribute("x", String.valueOf(x));
			textEl.setAttribute("y", String.valueOf(y));

			if (!style.getAlign().equals(""))
			{
				textEl.setAttribute("align", style.getAlign());
			}

			//SVG doesn't support vertical alignment, so this is fixed			
			//if (!style.getVAlign().equals(""))
			//{
			//	textEl.setAttribute("valign", style.getVAlign());
			//}
			textEl.setAttribute("valign", "bottom");
			rot = getMatrixRotation(element.getAttribute("transform"), configDoc.getDecimalsToRound());

			if (rot != 0)
			{
				textEl.setAttribute("rotation", Double.toString(rot));
			}

			if (str != null && !str.equals(""))
			{
				if (!isFirst)
				{
					xmlDoc.getElementsByTagName("foreground").item(0).appendChild(textEl);
				}
				else
				{
					xmlDoc.getElementsByTagName("background").item(0).appendChild(textEl);
				}
			}
		}

		str = "";

		//possible tspan elements
		for (int i = 0; i < textChildren.getLength(); i++)
		{
			Node currNode = textChildren.item(i);


			if (currNode.getNodeType() == Node.ELEMENT_NODE && currNode.getNodeName().equals("tspan"))
			{
				XmlStyle currStyle = Svg2Xml.getStyle(currNode);
				//we found a nested tspan element
				Map<String, String> styleDiff = Svg2Xml.getStyleDiff(style, currStyle);
				//				style = currStyle;
				Element currEl = (Element) currNode;
				Svg2Xml.appendStyle(xmlDoc.getElementsByTagName("foreground").item(0), styleDiff, configDoc, currEl);

				if (currEl.getAttribute("transform") != null && !currEl.getAttribute("transform").equals(""))
				{
					//transformed text, but only x,y are transformed here
					matrixTransformPoint(currEl, xmlDoc, configDoc);
				}

				xS = currEl.getAttribute("x");
				yS = currEl.getAttribute("y");

				if (xS.equals(""))
				{
					xS = element.getAttribute("x");
				}

				if (yS.equals(""))
				{
					yS = element.getAttribute("y");
				}

				x = Double.valueOf(xS);
				y = Double.valueOf(yS);

				if (xS.length() > 0)
				{
					x = Double.parseDouble(xS);
				}

				if (xS.length() > 0)
				{
					x = Double.parseDouble(xS);
				}

				x = x - configDoc.getStencilBoundsMinX();
				y = y - configDoc.getStencilBoundsMinY();
				x = roundToDecimals(x * s, rd);
				y = roundToDecimals(y * s, rd);

				//create, populate and append the text element
				Element tspanEl = xmlDoc.createElement("text");

				str = "";

				textChildren = currEl.getChildNodes();

				for (int j = 0; j < textChildren.getLength(); ++j)
				{
					Node child = textChildren.item(j);

					if (child.getNodeType() == Node.TEXT_NODE)
					{
						str = str + child.getTextContent();
					}
				}

				if (str != null && !str.equals("") && currEl.getNodeName().equals("tspan"))
				{
					tspanEl.setAttribute("str", str);
					tspanEl.setAttribute("x", String.valueOf(x));
					tspanEl.setAttribute("y", String.valueOf(y));

					if (!currStyle.getAlign().equals(""))
					{
						tspanEl.setAttribute("align", currStyle.getAlign());
					}
					else if (!style.getAlign().equals(""))
					{
						tspanEl.setAttribute("align", style.getAlign());
					}

					//SVG doesn't support vertical alignment, so this is fixed			
					//if (!currStyle.get().equals(""))
					//{
					//	tspanEl.setAttribute("", currStyle.getVAlign());
					//}
					tspanEl.setAttribute("valign", "bottom");
					double newRot = rot + getMatrixRotation(currEl.getAttribute("transform"), rd);

					if (newRot != 0)
					{
						tspanEl.setAttribute("rotation", Double.toString(newRot));
					}

					xmlDoc.getElementsByTagName("foreground").item(0).appendChild(tspanEl);
				}

				style = currStyle;
			}
		}
	}

	private static double getMatrixRotation(String tranformString, int decimalNum)
	{
		if (tranformString != null && tranformString.contains("matrix"))
		{
			double rot = 0;
			double a=0;
			double b=0;
			double c=0;
			double d=0;
			double e=0;
			double f=0;
			String aString;
			String bString;
			String cString;
			String dString;
			String eString;
			String fString;

			tranformString = tranformString.replaceAll(",", "");
			int startCurrIndex = tranformString.indexOf("matrix(");
			int endCurrIndex = tranformString.indexOf(" ",startCurrIndex);
			aString = tranformString.substring(startCurrIndex+7, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = tranformString.indexOf(" ",startCurrIndex);
			bString = tranformString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = tranformString.indexOf(" ",startCurrIndex);
			cString = tranformString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = tranformString.indexOf(" ",startCurrIndex);
			dString = tranformString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = tranformString.indexOf(" ",startCurrIndex);
			eString = tranformString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = tranformString.indexOf(")",startCurrIndex);
			fString = tranformString.substring(startCurrIndex, endCurrIndex);

			a = Double.valueOf(aString);
			b = Double.valueOf(bString);
			c = Double.valueOf(cString);
			d = Double.valueOf(dString);
			e = Double.valueOf(eString);
			f = Double.valueOf(fString);

			double x1 = 2;
			double y1 = 2;
			double x2 = 3;
			double y2 = 2;

			double x1New = x1 * a + y1 * c + e;
			double y1New = x1 * b + y1 * d + f;
			double x2New = x2 * a + y2 * c + e;
			double y2New = x2 * b + y2 * d + f;

			double dx = x2New - x1New;
			double dy = y1New - y2New;
			rot = Math.atan2(dy, dx) * 180.0 / Math.PI;
			rot = roundToDecimals(rot, decimalNum);

			return rot;
		}
		else
		{
			return 0;
		}
	}

	private static void matrixTransformPoint(Element element, Document xmlDoc, XmlConfig configDoc)
	{
		int dn = configDoc.getDecimalsToRound();
		String trString = element.getAttribute("transform");

		if (trString != null && trString.contains("matrix"))
		{
			double a=0;
			double b=0;
			double c=0;
			double d=0;
			double e=0;
			double f=0;
			String aString;
			String bString;
			String cString;
			String dString;
			String eString;
			String fString;

			trString = trString.replaceAll(",", "");
			int startCurrIndex = trString.indexOf("matrix(");
			int endCurrIndex = trString.indexOf(" ",startCurrIndex);
			aString = trString.substring(startCurrIndex+7, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = trString.indexOf(" ",startCurrIndex);
			bString = trString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = trString.indexOf(" ",startCurrIndex);
			cString = trString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = trString.indexOf(" ",startCurrIndex);
			dString = trString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = trString.indexOf(" ",startCurrIndex);
			eString = trString.substring(startCurrIndex, endCurrIndex);

			startCurrIndex = endCurrIndex+1;
			endCurrIndex = trString.indexOf(")",startCurrIndex);
			fString = trString.substring(startCurrIndex, endCurrIndex);

			a = Double.valueOf(aString);
			b = Double.valueOf(bString);
			c = Double.valueOf(cString);
			d = Double.valueOf(dString);
			e = Double.valueOf(eString);
			f = Double.valueOf(fString);

			double x = 0;
			double y = 0;
			String xString = element.getAttribute("x");
			String yString = element.getAttribute("y");

			if (xString.equals(""))
			{
				xString = "0";
			}

			if (yString.equals(""))
			{
				yString = "0";
			}

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);

			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			xNew = roundToDecimals(xNew, dn);
			yNew = roundToDecimals(yNew, dn);

			element.setAttribute("x", Double.toString(xNew));
			element.setAttribute("y", Double.toString(yNew));
		}
	}

	/**
	 * Parses the <b>transform</b> attribute of <b>element</b> and applies it to the element geometry
	 * @param element SVG Element to be transformed
	 * @param configDoc target stencil XML config document
	 * @return <b>element</b> with an applied transformation. The transform attribute is removed in the process.
	 */
	private static Element matrixTransformPoly(Element element, XmlConfig configDoc)
	{
		Double[] tr = getTransform(element);

		if (tr == null)
		{
			return element;
		}

		String pointsString = element.getAttribute("points");
		String newPointsString = "";
		double x = 0;
		double y = 0;
		String xString;
		String yString;

		//a loop that reads the coords
		int commaIndex = pointsString.indexOf(",");
		int spaceIndex = pointsString.indexOf(" ", commaIndex+1);

		if (spaceIndex == -1)
		{
			spaceIndex = pointsString.length();
		}

		while ((commaIndex != -1) && (spaceIndex > commaIndex))
		{
			//read x
			xString = pointsString.substring(0, commaIndex);
			//read y
			yString = pointsString.substring(commaIndex + 1, spaceIndex);

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);

			// the actual transform
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];
			// add the new coords to the new string

			newPointsString += xNew + "," + yNew + " ";

			pointsString = pointsString.substring(spaceIndex, pointsString.length());

			commaIndex = pointsString.indexOf(",");
			spaceIndex = pointsString.indexOf(" ", commaIndex + 1);

			if (spaceIndex==-1)
			{
				spaceIndex = pointsString.length();
			}
		}

		newPointsString = newPointsString.substring(0, (newPointsString.length() - 1));
		newPointsString = setPathRoot(newPointsString, configDoc);
		element.setAttribute("points", newPointsString);

		return element;
	}

	/**
	 * Parses the <b>transform</b> attribute of <b>element</b> and applies it to the element geometry
	 * @param element SVG Element to be transformed
	 * @param xmlDoc target stencil XML document
	 * @param configDoc target stencil XML config document
	 * @return <b>element</b> with an applied transformation. The transform attribute is removed in the process.
	 */
	private static Element matrixTransformRect(Element element, Document xmlDoc, XmlConfig configDoc)
	{
		Double[] tr = getTransform(element);
		double x = getDoubleAttribute(element, "x");
		double y = getDoubleAttribute(element, "y");
		double w = getDoubleAttribute(element, "width");
		double h = getDoubleAttribute(element, "height");
		double rx = getDoubleAttribute(element, "rx");
		double ry = getDoubleAttribute(element, "ry");

		double x1 = x;
		double y1 = y;
		String pathString = "";

		if (rx==0 && ry==0)
		{
			//rect
			pathString =  "M " + x1 + " " + y1 + " v " + h + " h " + w + " v " + (-h) + " h " + (-w) + " Z ";
		}
		else
		{
			//roundrect
			pathString =  "M " + x1 + " " + (y1 + ry) + 
					" v " + (h - 2 * ry) +
					" a " + rx + " " + ry + " 0 0 0 " + rx + " " + ry +
					" h " + (w - 2 * rx) +
					" a " + rx + " " + ry + " 0 0 0 " + rx + " " + ( -ry) +
					" v " + (2 * ry - h) + 
					" a " + rx + " " + ry + " 0 0 0 " + ( -rx) + " " + ( -ry) +
					" h " + (2 * rx - w) +
					" a " + rx + " " + ry + " 0 0 0 " + ( -rx) + " " + ry +
					" Z ";
		}

		if (tr != null)
		{
			pathString = matrixTransformPath(pathString, tr, configDoc);
		}

		double dx = configDoc.getStencilBoundsMinX();
		double dy = configDoc.getStencilBoundsMinY();
		pathString = matrixTransformPath(pathString, new Double[]{1.0, 0.0, 0.0, 1.0, -dx, -dy}, configDoc);
		double s = configDoc.getRelativeScalingRatio();
		pathString = matrixTransformPath(pathString, new Double[]{s, 0.0, 0.0, s, 0.0, 0.0}, configDoc);

		mxPathParser pp = new mxPathParser();
		return pp.createShape(pathString, xmlDoc, configDoc.getDecimalsToRound());
	}

	/**
	 * @param d number to round
	 * @param c decimals to round to (use values <0 if you want to skip rounding)
	 * @return rounnded <b>d</b> to <b>c</b> decimals
	 */
	public static double roundToDecimals(double d, int c) 
	{
		if (c >= 0)
		{
			BigDecimal temp = new BigDecimal(Double.toString(d));
			temp = temp.setScale(c, RoundingMode.HALF_EVEN);
			return temp.doubleValue();
		}
		else
		{
			return d;
		}
	}

	/**
	 * @param parentDoc the document in which the element will be created
	 * @param fragment fragment of XML code that needs converting
	 * @return a DOM Elemement representation of the <b>fragment</b> string  
	 * @throws IOException
	 * @throws SAXException
	 */
	private static Element returnXmlFragment(Document parentDoc, String fragment) 
	{
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = null;

		try
		{
			docBuilder = docBuilderFactory.newDocumentBuilder();
		}
		catch (ParserConfigurationException e)
		{
			e.printStackTrace();
		}

		Node fragmentNode;
		try
		{
			fragmentNode = docBuilder.parse(new InputSource(new StringReader(fragment))).getDocumentElement();
			fragmentNode = parentDoc.importNode(fragmentNode, true);
			return (Element) fragmentNode;
		}
		catch (SAXException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * @param pathString SVG path's "d" attribute
	 * @param <b>a b c d e f</b> are parts of the SVG transform attribute's matrix in <b>double</b> format
	 * @param configDoc config doc of the target stencil XML
	 * @return transformed SVG path's "d" attribute string
	 */
	private static String matrixTransformPath(String pathString, Double[] tr, XmlConfig configDoc)
	{
		prevPathX = 0;
		prevPathY = 0;
		lastPathX = 0;
		lastPathY = 0;

		if (pathString != null)
		{
			pathString = pathString.replaceAll(System.getProperty("line.separator"), "");
			pathString = pathString.replaceAll("E -", "E-");
			pathString = pathString.replaceAll("e -", "e-");
			pathString = pathString.replaceAll("\n", "");
			pathString = pathString.replaceAll(" {2,}", " ");
			pathString = pathString.replaceAll("-\\.", "-0.");
			pathString = pathString.replaceAll(" e", "e");
			pathString = pathString.replaceAll(" E", "e");

			// handle the case of two decimals (".ddddd. to .ddddd 0.")
			 Matcher m = Pattern.compile("\\.\\d+\\.").matcher(pathString);
			 
			 while (m.find()) {
				 pathString = pathString.substring(0, m.end() - 1) + " 0" + pathString.substring(m.end() - 1, pathString.length());
				 m = Pattern.compile("\\.\\d+\\.").matcher(pathString);
			 }
			 
			 
			if (tr != null)
			{
				String newPathString = "";
				int nextPartStartIndex; 
				char prevPathType = 'm';
				
				do
				{
					pathString = pathString.trim();
					nextPartStartIndex = nextPartIndex(pathString, prevPathType);
					String currPathString = null;
					
					if (nextPartStartIndex != -1)
					{
						currPathString = pathString.substring(0, nextPartStartIndex);
					}
					else
					{
						currPathString = pathString;
					}
					
					char currPathType = currPathString.charAt(0);
					
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
						
						currPathString = currPathType + " " + currPathString;
					}
					
					newPathString += parseMatrixTransformPathPart(currPathString, tr);
					
					if (nextPartStartIndex != -1)
					{
						pathString = pathString.substring(nextPartStartIndex, pathString.length());
						prevPathType = currPathType;
					}
					else
					{
						pathString = "";
					}
				} 
				
				while (pathString.length() > 0);

				newPathString = newPathString.substring(0, (newPathString.length() - 1));
				newPathString = setPathRoot(newPathString, configDoc);
				return newPathString;
			}
			else
			{
				return pathString;
			}
		}
		else
		{
			return null;
		}
	}

	/**
	 * @param currPathString one shape in the SVG path string
	 * @param <b>a b c d e f</b> are parts of the SVG transform attribute's matrix in <b>double</b> format
	 * @param configDoc config doc of the target stencil XML
	 * @return path part with applied transformation
	 */
	private static String parseMatrixTransformPathPart(String currPathString, Double[] tr)
	{
		char pathType = currPathString.charAt(0);
		String newPath="error"; // if it doesn't get changes, the path type isn't recognized, so it's an error

		switch (pathType)
		{
			case 'M' : return newPath = matrixTransformPathPartMove(currPathString, tr, true);
			case 'm' : return newPath = matrixTransformPathPartMove(currPathString, tr, false);
			case 'L' : return newPath = matrixTransformPathPartLine(currPathString, tr, true);
			case 'l' : return newPath = matrixTransformPathPartLine(currPathString, tr, false);
			case 'H' : return newPath = matrixTransformPathPartHorLine(currPathString, tr, true);
			case 'h' : return newPath = matrixTransformPathPartHorLine(currPathString, tr, false);
			case 'V' : return newPath = matrixTransformPathPartVerLine(currPathString, tr, true);
			case 'v' : return newPath = matrixTransformPathPartVerLine(currPathString, tr, false);
			case 'C' : return newPath = matrixTransformPathPartCurve(currPathString, tr, true);
			case 'c' : return newPath = matrixTransformPathPartCurve(currPathString, tr, false);
			case 'S' : return newPath = matrixTransformPathPartSmoothCurve(currPathString, tr, true);
			case 's' : return newPath = matrixTransformPathPartSmoothCurve(currPathString, tr, false);
			case 'Q' : return newPath = matrixTransformPathPartQuad(currPathString, tr, true);
			case 'q' : return newPath = matrixTransformPathPartQuad(currPathString, tr, false);
			case 'T' : return newPath = matrixTransformPathPartSmoothQuad(currPathString, tr, true);
			case 't' : return newPath = matrixTransformPathPartSmoothQuad(currPathString, tr, false);
			case 'A' : return newPath = matrixTransformPathPartArc(currPathString, tr, true);
			case 'a' : return newPath = matrixTransformPathPartArc(currPathString, tr, false);
			case 'Z' :
				prevPathX = prevMoveX;
				prevPathY = prevMoveY;
				return "Z ";
			case 'z' : 
				prevPathX = prevMoveX;
				prevPathY = prevMoveY;
				return "z ";
		}

		if (!newPath.equals("error"))
		{
			return newPath;
		}
		else
		{
			return null;
		}
	}

	//for internal use only
	private static String matrixTransformPathPartArc(String currPathString, Double[] tr, boolean isAbs)
	{
		double xScaleFactor = Math.sqrt((tr[0] * tr[0]) + (tr[2] * tr[2]));
		double yScaleFactor = Math.sqrt((tr[1] * tr[1]) + (tr[3] * tr[3]));
		double rx = Math.abs(getPathParam(currPathString, 1) * xScaleFactor);
		double ry = Math.abs(getPathParam(currPathString, 2) * yScaleFactor);
		double xRot = getPathParam(currPathString, 3);
		int largeArc = (int) getPathParam(currPathString, 4);
		int sweep = (int) getPathParam(currPathString, 5);

		// correcting sweep if scaling is negative
		if ((tr[0] < 0 && tr[3] >= 0) || ( tr[0] >= 0 && tr[3] < 0))
		{
			if (sweep == 1)
			{
				sweep=0;
			}
			else
			{
				sweep=1;
			}
		}

		double x = getPathParam(currPathString, 6);
		double y = getPathParam(currPathString, 7);
		double transformRot = Math.atan2(tr[1], tr[0]);
		transformRot = Math.toDegrees(transformRot);
		xRot = xRot + transformRot; 

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];

			currPathString = "A " + rx + " " + ry + " " + xRot + " " + largeArc + " " + sweep + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			currPathString = "a " + rx + " " + ry + " " + xRot + " " + largeArc + " " + sweep + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartSmoothQuad(String currPathString, Double[] tr, boolean isAbs)
	{
		double x = getPathParam(currPathString, 1);
		double y = getPathParam(currPathString, 2);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];

			currPathString = "T " + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			currPathString = "t " + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartQuad(String currPathString, Double[] tr, boolean isAbs)
	{
		double x1 = getPathParam(currPathString, 1);
		double y1 = getPathParam(currPathString, 2);
		double x = getPathParam(currPathString, 3);
		double y = getPathParam(currPathString, 4);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];
			double x1New = x1 * tr[0] + y1 * tr[2] + tr[4];
			double y1New = x1 * tr[1] + y1 * tr[3] + tr[5];

			currPathString = "Q " + x1New + " " + y1New + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			double oldAbsX1 = prevPathX + x1;
			double oldAbsY1 = prevPathY + y1;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];

			double newAbsX1 = oldAbsX1 * tr[0] + oldAbsY1 * tr[2] + tr[4];
			double newAbsY1 = oldAbsX1 * tr[1] + oldAbsY1 * tr[3] + tr[5];

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;
			double newRelX1 = newAbsX1 - lastPathX;
			double newRelY1 = newAbsY1 - lastPathY;

			currPathString = "q " + newRelX1 + " " + newRelY1 + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartSmoothCurve(String currPathString,	Double[] tr, boolean isAbs)
	{
		double x2 = getPathParam(currPathString, 1);
		double y2 = getPathParam(currPathString, 2);
		double x = getPathParam(currPathString, 3);
		double y = getPathParam(currPathString, 4);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];
			double x2New = x2 * tr[0] + y2 * tr[2] + tr[4];
			double y2New = x2 * tr[1] + y2 * tr[3] + tr[5];

			currPathString = "S " + x2New + " " + y2New + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			double oldAbsX2 = prevPathX + x2;
			double oldAbsY2 = prevPathY + y2;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];
			double newAbsX2 = oldAbsX2 * tr[0] + oldAbsY2 * tr[2] + tr[4];
			double newAbsY2 = oldAbsX2 * tr[1] + oldAbsY2 * tr[3] + tr[5];

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;
			double newRelX2 = newAbsX2 - lastPathX;
			double newRelY2 = newAbsY2 - lastPathY;

			currPathString = "s " + newRelX2 + " " + newRelY2 + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartCurve(String currPathString, Double[] tr, boolean isAbs)
	{
		double x1 = getPathParam(currPathString, 1);
		double y1 = getPathParam(currPathString, 2);
		double x2 = getPathParam(currPathString, 3);
		double y2 = getPathParam(currPathString, 4);
		double x = getPathParam(currPathString, 5);
		double y = getPathParam(currPathString, 6);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];
			double x1New = x1 * tr[0] + y1 * tr[2] + tr[4];
			double y1New = x1 * tr[1] + y1 * tr[3] + tr[5];
			double x2New = x2 * tr[0] + y2 * tr[2] + tr[4];
			double y2New = x2 * tr[1] + y2 * tr[3] + tr[5];

			currPathString = "C " + x1New + " " + y1New + " " + x2New + " " + y2New + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			double oldAbsX1 = prevPathX + x1;
			double oldAbsY1 = prevPathY + y1;
			double oldAbsX2 = prevPathX + x2;
			double oldAbsY2 = prevPathY + y2;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];
			double newAbsX1 = oldAbsX1 * tr[0] + oldAbsY1 * tr[2] + tr[4];
			double newAbsY1 = oldAbsX1 * tr[1] + oldAbsY1 * tr[3] + tr[5];
			double newAbsX2 = oldAbsX2 * tr[0] + oldAbsY2 * tr[2] + tr[4];
			double newAbsY2 = oldAbsX2 * tr[1] + oldAbsY2 * tr[3] + tr[5];

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;
			double newRelX1 = newAbsX1 - lastPathX;
			double newRelY1 = newAbsY1 - lastPathY;
			double newRelX2 = newAbsX2 - lastPathX;
			double newRelY2 = newAbsY2 - lastPathY;

			currPathString = "c " + newRelX1 + " " + newRelY1 + " " + newRelX2 + " " + newRelY2 + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartVerLine(String currPathString,	Double[] tr, boolean isAbs)
	{
		double x = 0;
		double y = getPathParam(currPathString, 1);

		if (isAbs)
		{
			x = prevPathX;
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];

			currPathString = "L " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			currPathString = "l " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartHorLine(String currPathString, Double[] tr, boolean isAbs)
	{
		double x = getPathParam(currPathString, 1);
		double y = 0;


		if (isAbs)
		{
			y = prevPathY;
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];

			currPathString = "L " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			currPathString = "l " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartLine(String currPathString, Double[] tr, boolean isAbs)
	{
		double x = getPathParam(currPathString, 1);
		double y = getPathParam(currPathString, 2);


		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];

			currPathString = "L " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			prevPathX += x;
			prevPathY += y;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			currPathString = "l " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}

		return currPathString;
	}

	//for internal use only
	private static String matrixTransformPathPartMove(String currPathString, Double[] tr, boolean isAbs)
	{
		double x = getPathParam(currPathString, 1);
		double y = getPathParam(currPathString, 2);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			prevMoveX = prevPathX;
			prevMoveY = prevPathY;
			
			double xNew = x * tr[0] + y * tr[2] + tr[4];
			double yNew = x * tr[1] + y * tr[3] + tr[5];

			currPathString = "M " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double oldAbsX = prevPathX + x;
			double oldAbsY = prevPathY + y;
			prevPathX += x;
			prevPathY += y;
			prevMoveX = prevPathX;
			prevMoveY = prevPathY;
			double newAbsX = oldAbsX * tr[0] + oldAbsY * tr[2] + tr[4];
			double newAbsY = oldAbsX * tr[1] + oldAbsY * tr[3] + tr[5];

			currPathString = "M " + newAbsX + " " + newAbsY + " ";
			lastPathX = newAbsX;
			lastPathY = newAbsY;
			
			lastMoveX = lastPathX;
			lastMoveY = lastPathY;
		}

		return currPathString;
	}

	//for internal use only
	public static int nextPartIndex(String path, char lastPathType)
	{
		path = path.replaceAll(",", " ");
		path = path.replaceAll("\\s{2,}", " ");
		path = path.trim();
		
		char currPathType = path.charAt(0);
		
		if (!Character.isLetter(currPathType))
		{
			currPathType = lastPathType;
		}

		int numParams = 0;
		CharSequence pathType = String.valueOf(currPathType);
		String set = "HhVv";
		
		if (set.contains(pathType))
		{
			numParams = 1;
		}
		
		set = "MmLlTt";
		
		if (set.contains(pathType))
		{
			numParams = 2;
		}
		
		set = "SsQq";
		
		if (set.contains(pathType))
		{
			numParams = 4;
		}
		
		set = "Cc";
		
		if (set.contains(pathType))
		{
			numParams = 6;
		}
		
		set = "Aa";
		
		if (set.contains(pathType))
		{
			numParams = 7;
		}
		
		int currIndex = 0;
		
		if (!(Character.isDigit(path.charAt(0)) || path.charAt(0) == '-'))
		{
			numParams++;
		}
		
		for (int i = 0; i < numParams; ++i) 
		{
			currIndex = path.indexOf(" ", currIndex + 1);
		};
		
		return currIndex;
	}

	//for internal use only
	// param # starting from 1
	public static double getPathParam(String pathString, int index)
	{
		pathString = pathString.replaceAll(",", ", ");
		pathString = pathString.replaceAll("  ", " ");
		pathString = pathString.replaceAll("E -", "E-");
		pathString = pathString.replaceAll("e -", "e-");

		int currIndex=0;
		int currParamIndex = 0;

		if (pathString.charAt(1) != ' ')
		{
			pathString = pathString.substring(0, 1) + " " + pathString.substring(1, pathString.length());
		}

		while (currIndex < index)
		{
			currParamIndex = pathString.indexOf(' ', currParamIndex + 1);
			currIndex++;
		}

		int endIndex = pathString.indexOf(' ', currParamIndex + 1);

		if (endIndex == -1)
		{
			endIndex = pathString.length();
		}

		String paramString = pathString.substring(currParamIndex, endIndex);

		double param = Double.valueOf(paramString);

		return param;
	}

	/**
	 * sets the top left of the drawing to vector [x,y], to be used only with path parts
	 * @param path path to transform
	 * @param configDoc target config document 
	 * @return transformed path
	 */
	private static String setPathRoot (String path, XmlConfig configDoc)
	{
		int dn = configDoc.getDecimalsToRound();
		double x = configDoc.getStencilBoundsMinX();
		double y = configDoc.getStencilBoundsMinY();

		String xmlOut = setRootAttr(x, path, " x=\"", dn);
		xmlOut = setRootAttr(x, xmlOut, " x1=\"", dn);
		xmlOut = setRootAttr(x, xmlOut, " x2=\"", dn);
		xmlOut = setRootAttr(x, xmlOut, " x3=\"", dn);
		xmlOut = setRootAttr(y, xmlOut, " y=\"", dn);
		xmlOut = setRootAttr(y, xmlOut, " y1=\"", dn);
		xmlOut = setRootAttr(y, xmlOut, " y2=\"", dn);
		xmlOut = setRootAttr(y, xmlOut, " y3=\"", dn);

		return xmlOut;
	}

	/**
	 * sets the top left of the drawing to vector [x,y]
	 * @param coord source coord
	 * @param xmlCode chuk of code where the translation is applied
	 * @param lookFor what to replace
	 * @param decimalsNum number of decimals to round
	 * @return translated coordinate
	 */
	private static String setRootAttr (double coord, String xmlCode, String lookFor, int decimalsNum)
	{
		int startIndex= -1;
		int startIndexOld = 0;
		int endIndex = 0;
		int endIndexOld = 0;
		String xmlOut = "";

		while ((startIndex = xmlCode.indexOf(lookFor, startIndexOld)) != -1)
		{
			if (startIndex>startIndexOld)
			{
				//find
				startIndex+=lookFor.length();
				endIndex = xmlCode.indexOf("\"", startIndex);
				String doubleValueString = xmlCode.substring(startIndex, endIndex);

				// translate
				double doubleValue = Double.valueOf(doubleValueString);
				doubleValue -= coord;
				doubleValue = roundToDecimals(doubleValue, decimalsNum);
				doubleValueString = String.valueOf(doubleValue);

				Double valueDouble = new Double(doubleValue);
				long valueLong = 0;

				//compose
				if (doubleValue - valueDouble.intValue() == 0)
				{
					doubleValue = roundToDecimals(doubleValue, 0);
					valueLong = valueDouble.intValue();
					xmlOut += xmlCode.substring(endIndexOld, startIndex) + valueLong; 
				}
				else
				{
					xmlOut += xmlCode.substring(endIndexOld, startIndex) + doubleValue; 
				}

				endIndexOld = endIndex;
				startIndexOld = startIndex;
			}
		}
		xmlOut += xmlCode.substring(endIndex, xmlCode.length());

		xmlCode = xmlOut;

		return xmlCode;
	}

	public static String getFirstLevelTextContent(Node node) 
	{
		NodeList list = node.getChildNodes();
		StringBuilder textContent = new StringBuilder();

		for (int i = 0; i < list.getLength(); ++i) 
		{
			Node child = list.item(i);

			if (child.getNodeType() == Node.TEXT_NODE)
			{
				textContent.append(child.getTextContent());
			}
		}
		return textContent.toString();
	}

	public static Double[] getTransform(Element element)
	{
		String trString = element.getAttribute("transform");

		if (trString == null || !trString.contains("matrix"))
		{
			return null;
		}

		double a=0;
		double b=0;
		double c=0;
		double d=0;
		double e=0;
		double f=0;
		String aString;
		String bString;
		String cString;
		String dString;
		String eString;
		String fString;

		trString = trString.replaceAll(",", " ");
		int startCurrIndex = trString.indexOf("matrix(");
		int endCurrIndex = trString.indexOf(" ",startCurrIndex);
		aString = trString.substring(startCurrIndex+7, endCurrIndex);

		startCurrIndex = endCurrIndex+1;
		endCurrIndex = trString.indexOf(" ",startCurrIndex);
		bString = trString.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex+1;
		endCurrIndex = trString.indexOf(" ",startCurrIndex);
		cString = trString.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex+1;
		endCurrIndex = trString.indexOf(" ",startCurrIndex);
		dString = trString.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex+1;
		endCurrIndex = trString.indexOf(" ",startCurrIndex);
		eString = trString.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex+1;
		endCurrIndex = trString.indexOf(")",startCurrIndex);
		fString = trString.substring(startCurrIndex, endCurrIndex);

		a = 0;
		b = 0;
		c = 0;
		d = 0;
		e = 0;
		f = 0;

		if (!aString.equals(""))
		{
			a = Double.valueOf(aString);
		}

		if (!bString.equals(""))
		{
			b = Double.valueOf(bString);
		}

		if (!cString.equals(""))
		{
			c = Double.valueOf(cString);
		}

		if (!dString.equals(""))
		{
			d = Double.valueOf(dString);
		}

		if (!eString.equals(""))
		{
			e = Double.valueOf(eString);
		}

		if (!fString.equals(""))
		{
			f = Double.valueOf(fString);
		}

		Double[] result = {a, b, c, d, e, f};
		return result;
	}

	private static Double getDoubleAttribute(Element element, String attrName)
	{
		if (element.getAttribute(attrName).length() > 0)
		{
			return Double.valueOf(element.getAttribute(attrName));
		}
		else
		{
			return 0.0;
		}
	}
}
