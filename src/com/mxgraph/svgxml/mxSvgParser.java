/**
 * $Id: mxSvgParser.java,v 1.1 2012-11-15 13:26:47 gaudenz Exp $
 * Copyright (c) 2010, Gaudenz Alder, David Benson
 */
package com.mxgraph.svgxml;

import org.w3c.dom.Node;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.InputSource;

import com.mxgraph.shape.mxStencilShape;
import com.mxgraph.svgxml.svg.PointsParser;

/**
 * Stencil shape drawing that takes an XML definition of the shape 
 * and renders it.
 */
public class mxSvgParser
{
	protected String xmlString;

	protected Node root;

	protected boolean isLastShapeFilled = false;

	// communication between the style parser and the save/restore block generator
	protected boolean needToRedefineStyle = false;
	
	protected Rectangle2D boundingBox;

	protected String lastStrokeColorString = "";

	protected String lastStrokeWidthString = "";

	protected String lastFillString = "";

	protected String lastStrokeLinejoinString = "miter";

	protected String lastStrokeLinecapString = "butt";

	protected String lastStrokeMiterLimitString = "4";

	protected boolean wasLastDashed = false;

	protected boolean someStyleReturnedToDefault = false;
	
	protected String lastDashPatternString = "";

	protected String lastFillOpacityString = "1";

	protected String lastStrokeOpacityString = "1";

	protected String lastAlphaString = "1";
	
	protected String lastFontColorString = "none";
	
	protected int lastFontStyle = 0;
	
	protected String lastFontSizeString = "0";
	
	protected String lastFontFamilyString = "Arial";

	/**
	 * The number of decimals for all the coordinates in the resulting xml 
	 */
	protected final static int decimalsNum = 2;
	
	/**
	 * Indicates if the current shape has a different style than the previous one
	 */
	protected boolean isDefaultStyle = true;

	protected double lastPathX=0; // where the last path part ended
	protected double lastPathY=0; 
	protected double prevPathX=0; // where the last path part ended before transforming
	protected double prevPathY=0;

	/**
	 * Constructs a new stencil for the given Dia shape description.
	 */
	public mxSvgParser(String shapeXml, String shapeName, String shapeGroupConfigXml, String shapeConfigXml)
	{
		Document document = mxSvgParser.parseXml(shapeXml);
		Document configDocument = null;
		Document groupConfigDocument = null;
		if (shapeConfigXml != null)
		{
			configDocument = mxSvgParser.parseXml(shapeConfigXml);
		}
		if (shapeGroupConfigXml != null)
		{
			groupConfigDocument = mxSvgParser.parseXml(shapeGroupConfigXml);
		}

		boolean hasBackground = false;
		String aspectRatio = null;
		String strokeWidth = null;
		ArrayList<Connections> groupConnections = null;
		ArrayList<Connections> shapeConnections = null;

		// local config has higher priority
		if (groupConfigDocument != null)
		{
			Node groupConfigRoot = groupConfigDocument.getElementsByTagName("shape").item(0);
			Element groupConfigRootElement = (Element) groupConfigRoot;
			if (groupConfigRootElement.getAttribute("background").equals("1"))
				hasBackground = true;
			aspectRatio = groupConfigRootElement.getAttribute("aspect");
			strokeWidth = groupConfigRootElement.getAttribute("strokewidth");
			groupConnections = new ArrayList<Connections>(); 
			groupConnections = Connections.getConstraintsFromXml(shapeGroupConfigXml);
		}

		if (configDocument != null)
		{
			Node configRoot = configDocument.getElementsByTagName("shape").item(0);
			Element configRootElement = (Element) configRoot;
			if (configRootElement.getAttribute("background").equals("1"))
				hasBackground = true;
			else if (configRootElement.getAttribute("background").equals("0"))
				hasBackground = false;
			if (!configRootElement.getAttribute("aspect").equals("") && configRootElement.getAttribute("aspect")!=null)
				aspectRatio = configRootElement.getAttribute("aspect");
			if (!configRootElement.getAttribute("strokewidth").equals("") && configRootElement.getAttribute("strokewidth")!=null)
				strokeWidth = configRootElement.getAttribute("strokewidth");
			shapeConnections = new ArrayList<Connections>(); 
			shapeConnections = Connections.getConstraintsFromXml(shapeConfigXml);
		}

		// NOTE: only the first connection block is used in the shape's local config
		Connections shapeConnection = null;
		Connections finalConnection = null;
		if (shapeConnections != null && !shapeConnections.isEmpty())
		{
			shapeConnection = shapeConnections.get(0);
			// determine the correct connections
			finalConnection = shapeConnections.get(0);
		}

		if (shapeConnection != null && shapeConnection.getId() != null && !shapeConnection.getId().equals(""))
		{
			String id = shapeConnection.getId();
			boolean foundConfig = false;
			int i = 0;
			while (!foundConfig && i<groupConnections.size())
			{
				String name = groupConnections.get(i).getName();
				if (name != null && name.equals(id))
				{
					finalConnection = groupConnections.get(i);
					foundConfig = true;
				}
				i++;
			}
			if (!foundConfig)
			{
				System.out.println("Didn't find a config XML with the reference \"" + shapeConnection.getId() + "\" for the shape \"" + shapeName + "\".");
			}
			else
			{
				// if the local config has defined connections and has a reference too, the local connections are added to the group config for this shape
				for (int j=0; j<shapeConnection.getConstraintNum(); j++)
				{
					finalConnection.constraints.add(shapeConnection.constraints.get(j));
				}
			}
		}
		
		this.root = document.getElementsByTagName("svg").item(0);
		shapeName = shapeName.replaceAll("_", " ");
		xmlString = new String("<shape name=\"" + shapeName
				+ "\" h=\"temp\" w=\"temp\"");
		if (aspectRatio != null && !aspectRatio.equals(""))
			xmlString += " aspect=\"" + aspectRatio + "\"";
		if (strokeWidth != null && !strokeWidth.equals(""))
			xmlString += " strokewidth=\"" + strokeWidth + "\"";
		
		xmlString += ">\n";
		xmlString += "<#mxGraph.parsingMarker.connections>\n";
		// Parse 1. Create the mxGraph XML representation of
		// the input SVG
		xmlString = createShape(this.root, xmlString, false, hasBackground);

		xmlString += "</shape>\n";

		// Parse 2. Create Graphics2D representation of the shape
		// to get the bounds

		mxStencilShape newShape = new mxStencilShape(document);
		Rectangle2D bounds = newShape.getBoundingBox();

		double stencilBoundsMinX = bounds.getMinX();
		double stencilBoundsMinY = bounds.getMinY();
		double stencilBoundsMaxX = bounds.getMaxX();
		double stencilBoundsMaxY = bounds.getMaxY();

		double stencilBoundsX = (stencilBoundsMaxX - stencilBoundsMinX);
		double stencilBoundsY = (stencilBoundsMaxY - stencilBoundsMinY);
		stencilBoundsX =  roundToDecimals(stencilBoundsX, decimalsNum);
		stencilBoundsY =  roundToDecimals(stencilBoundsY, decimalsNum);

		if (stencilBoundsX<5)
		{
			stencilBoundsX = 5;
			double oldStencilBoundsMaxX = stencilBoundsMaxX;
			double oldStencilBoundsMinX = stencilBoundsMinX;
			stencilBoundsMaxX = (oldStencilBoundsMaxX + oldStencilBoundsMinX) / 2.0d + 2.5; 
			stencilBoundsMinX = (oldStencilBoundsMaxX + oldStencilBoundsMinX) / 2.0d - 2.5; 
		}
		if (stencilBoundsY<5)
		{
			stencilBoundsY = 5;
			double oldStencilBoundsMaxY = stencilBoundsMaxY;
			double oldStencilBoundsMinY = stencilBoundsMinY;
			stencilBoundsMaxY = (oldStencilBoundsMaxY + oldStencilBoundsMinY) / 2.0d + 2.5; 
			stencilBoundsMinY = (oldStencilBoundsMaxY + oldStencilBoundsMinY) / 2.0d - 2.5; 
		}
		
		xmlString = xmlString.replaceAll("w=\"temp\"", "w=\"" + stencilBoundsX
				+ "\"");
		xmlString = xmlString.replaceAll("h=\"temp\"", "h=\"" + stencilBoundsY
				+ "\"");
		xmlString = this.setRoot(stencilBoundsMinX, stencilBoundsMinY, xmlString);
		
		//enter the connection info
		String connectionString = "";
		if (finalConnection != null && finalConnection.constraints.size()>0)
		{
			connectionString += "<connections>\n";
			for (int i=0; i<finalConnection.constraints.size(); i++)
			{
				Constraint currConstraint = finalConnection.constraints.get(i); 
				Double x = currConstraint.getX();
				Double y = currConstraint.getY();
				int perimeter = 0;
				if (currConstraint.isPerimeter())
					perimeter = 1;
				String name = currConstraint.getName();
				connectionString += "<constraint x=\"" + x + "\" y=\"" + y + "\" perimeter=\"" + perimeter + "\"";
				if (name != null && name.length()>0)
					connectionString += " name=\"" + name + "\"";
				connectionString += "/>\n";
			}
			connectionString += "</connections>\n";
		}
		
		// cleaning real values from the connection block
		connectionString = this.setRootAttr(0, connectionString, " x=\"", 3);
		connectionString = this.setRootAttr(0, connectionString, " y=\"", 3);

		xmlString = xmlString.replaceAll("<#mxGraph.parsingMarker.connections>\n", connectionString);
		System.out.println(xmlString);
	}

	/**
	 * Returns a new document for the given XML string.
	 * 
	 * @param xml
	 *            String that represents the XML data.
	 * @return Returns a new XML document.
	 */
	public static Document parseXml(String xml)
	{
		try
		{
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

			return docBuilder.parse(new InputSource(new StringReader(xml)));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * isGroup - set to true only if you're using createShape to parse a group which is a part of a larger drawing.
	 * @param finalConnection 
	 */
	public String createShape(Node root, String xmlOut, boolean isGroup, boolean hasBackground)
	{
		Node child = root.getFirstChild();
		boolean isFirstShape = true;
		boolean backgroundDone = false;
		boolean foregroundOpened = false;
		boolean saveBlockOpen = false;
		/*
		 * TODO: If root is a group element, then we should add it's styles to the children...
		 * Take note that style inheritance is more complex (same as for CSS) than just setting the group style to all it's children
		 * (style can be defined 2 ways, and always need to check not to overwrite a style that is already set at children level and
		 * also combining attributes is possible (font size = 12 and font size = 120%)
		 */
		SvgElementParsingResult elementXml = new SvgElementParsingResult();
		String oldSuffix = new String("");
		while (child != null)
		{
			Node nextChild = child.getNextSibling(); 
			if (isGroup(child.getNodeName()))
			{
				xmlOut = createShape(child, xmlOut, true, (isFirstShape && hasBackground));
				isFirstShape = false;
			}
			else
			{
				elementXml = createElement(child); 
				String element = new String();
				if (elementXml != null && elementXml.getElementXml() != null)
					element = elementXml.getElementXml();
					
				if (element != null && !element.equals("null") && !element.equals(""))
				{
					String xmlPrefix = "";
					String xmlSuffix = "";
					
					// foreground check
					if (!foregroundOpened && (backgroundDone || !hasBackground))
					{
						xmlPrefix+= "<foreground>\n" + oldSuffix;
						foregroundOpened = true;
					}

					// background check
					if (hasBackground && !backgroundDone)
					{
						xmlPrefix+= "<background>\n";
						xmlSuffix+= "</background>\n";
						oldSuffix = elementXml.getSuffixXml();
						backgroundDone = true;
					}
					
					// save check
					// opening a potential save block when we change from default style to a specific
					if (!isDefaultStyle && !saveBlockOpen)
					{
						xmlPrefix+= "<#mxgraph.styleBlock.start>\n";
						saveBlockOpen = true;
					}

					// restore check
					// closing when we change from a specific style to default and then we enable the block
					if (someStyleReturnedToDefault && saveBlockOpen && !isFirstShape)
					{
						needToRedefineStyle = true;
						Element currElement = (Element) child;
						String style = currElement.getAttribute("style");
						Map<String, Object> styleMap = mxSvgParser.getStylenames(style);
						xmlPrefix+= "<restore/>\n" + getStyle(currElement, styleMap);
						xmlOut = xmlOut.replaceAll("<#mxgraph.styleBlock.start>", "<save/>");
						xmlPrefix = xmlPrefix.replaceAll("<#mxgraph.styleBlock.start>", "<save/>");
						// here we enable the save/restore block
						saveBlockOpen = false;
					}

					isFirstShape = false;
					xmlOut += xmlPrefix;
					xmlOut += elementXml.getStyleXml() + elementXml.getElementXml();
					if (foregroundOpened)
						xmlOut += elementXml.getSuffixXml();
					xmlOut += xmlSuffix;
				}
			}
			child = nextChild;
		}

		// if we reached the end of the file and didn't need to save, we delete the save command if there was one
		if(saveBlockOpen)
		{
			// delete the save command
			xmlOut = xmlOut.replaceAll("<#mxgraph.styleBlock.start>", "");
		}
		
		if (foregroundOpened)
			xmlOut += "</foreground>\n";
		else
		{
			xmlOut += "<foreground>\n" + oldSuffix + "</foreground>\n";
		}
		
		return xmlOut;

	}

	/**
	 * Forms an internal representation of the specified SVG element and 
	 * returns that representation
	 * @param root the SVG element to represent
	 * @return the String representation of the XML, or null if an error occurs
	 */
	// TODO implement marker defs from svg
	public SvgElementParsingResult createElement(Node root)
	{
		Element element = null;
		SvgElementParsingResult resultingXml = new SvgElementParsingResult();

		if (root instanceof Element)
		{
			element = (Element) root;
			String style = element.getAttribute("style");
			Map<String, Object> styleMap = mxSvgParser.getStylenames(style);
			element = this.transformShape(element);

			if (isRectangle(element.getNodeName()))
			{
				String rectXML = null;

				try
				{
					String xString = element.getAttribute("x");
					String yString = element.getAttribute("y");
					String widthString = element.getAttribute("width");
					String heightString = element.getAttribute("height");

					String rectString = "";
					// Values default to zero if not specified
					double x = 0;
					double y = 0;
					double width = 0;
					double height = 0;
					double arcsize = 0;

					if (xString.length() > 0)
					{
						x = Double.valueOf(xString);
					}
					if (yString.length() > 0)
					{
						y = Double.valueOf(yString);
					}
					if (widthString.length() > 0)
					{
						width = Double.valueOf(widthString);
						if (width < 0)
						{
							return null; // error in SVG spec
						}
					}
					if (heightString.length() > 0)
					{
						height = Double.valueOf(heightString);
						if (height < 0)
						{
							return null; // error in SVG spec
						}
					}

					String rxString = element.getAttribute("rx");
					String ryString = element.getAttribute("ry");
					double rx = 0;
					double ry = 0;

					if (rxString.length() > 0)
					{
						rx = Double.valueOf(rxString);
						if (rx < 0)
						{
							return null; // error in SVG spec
						}
					}
					if (ryString.length() > 0)
					{
						ry = Double.valueOf(ryString);
						if (ry < 0)
						{
							return null; // error in SVG spec
						}
					}

					if (rx > 0 || ry > 0)
					{
						// Specification rules on rx and ry
						if (rx > 0 && ryString.length() == 0)
						{
							ry = rx;
						}
						else if (ry > 0 && rxString.length() == 0)
						{
							rx = ry;
						}
						if (rx > width / 2.0)
						{
							rx = width / 2.0;
						}
						if (ry > height / 2.0)
						{
							ry = height / 2.0;
						}

						if (rx == 0 || ry == 0) // we have a rectangle
						{
							rectString = "rect";
						}
						else
							// we have a rounded rectangle
						{
							rectString = "roundrect";
							rx = rx / width;
							ry = ry / height;
							arcsize = 100*(rx + ry)/2;
						}

						x = roundToDecimals(x, decimalsNum);
						y = roundToDecimals(y, decimalsNum);
						width = roundToDecimals(width, decimalsNum);
						height = roundToDecimals(height, decimalsNum);

						resultingXml.setStyleXml(this.getStyle(element, styleMap));
						rectXML = new String("<" + rectString
								+ " x=\"" + x + "\" y=\"" + y + "\" w=\""
								+ width + "\" h=\"" + height + "\"");

						if (rectString == "rect")
						{
							rectXML += "/>\n";
						}
						else
						{
							rectXML += " arcsize=\"" + arcsize + "\"/>\n";
						}
						resultingXml.setElementXml(rectXML);
						if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
							resultingXml.setSuffixXml("<fillstroke/>\n");
						else
							resultingXml.setSuffixXml("<stroke/>\n");
					}
					else
					{
						if (rx == 0 || ry == 0) // we have a rectangle
						{
							rectString = "rect";
						}
						else
							// we have a rounded rectangle
						{
							rectString = "roundrect";
							rx = rx / width;
							ry = ry / height;
							arcsize = 100*(rx + ry)/2;
						}

						x = roundToDecimals(x, decimalsNum);
						y = roundToDecimals(y, decimalsNum);
						width = roundToDecimals(width, decimalsNum);
						height = roundToDecimals(height, decimalsNum);

						resultingXml.setStyleXml(this.getStyle(element, styleMap));
						rectXML = new String("<" + rectString
								+ " x=\"" + x + "\" y=\"" + y + "\" w=\""
								+ width + "\" h=\"" + height + "\"");

						if (rectString == "rect")
						{
							rectXML += "/>\n";
						}
						else
						{
							rectXML += " arcsize=\"" + arcsize + "\"/>\n";
						}
						resultingXml.setElementXml(rectXML);
						if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
							resultingXml.setSuffixXml("<fillstroke/>\n");
						else
							resultingXml.setSuffixXml("<stroke/>\n");

					}
				}
				catch (Exception e)
				{
					e.printStackTrace();
					// TODO log something useful
				}

				return resultingXml;
			}
			else if (isLine(element.getNodeName()))
			{
				String x1String = element.getAttribute("x1");
				String x2String = element.getAttribute("x2");
				String y1String = element.getAttribute("y1");
				String y2String = element.getAttribute("y2");

				double x1 = 0;
				double x2 = 0;
				double y1 = 0;
				double y2 = 0;

				if (x1String.length() > 0)
				{
					x1 = Double.valueOf(x1String);
				}
				if (x2String.length() > 0)
				{
					x2 = Double.valueOf(x2String);
				}
				if (y1String.length() > 0)
				{
					y1 = Double.valueOf(y1String);
				}
				if (y2String.length() > 0)
				{
					y2 = Double.valueOf(y2String);
				}

				x1 = roundToDecimals(x1, decimalsNum);
				y1 = roundToDecimals(y1, decimalsNum);
				x2 = roundToDecimals(x2, decimalsNum);
				y2 = roundToDecimals(y2, decimalsNum);

				resultingXml.setStyleXml(this.getStyle(element, styleMap));
				resultingXml.setElementXml("<path>\n<move x=\"" + x1 + "\" y=\"" + y1 + "\"/>\n" + "<line x=\"" + x2 + "\" y=\"" + y2 + "\"/>\n</path>\n");
				resultingXml.setSuffixXml("<stroke/>\n");
				return resultingXml;
			}
			else if (isPolyline(element.getNodeName())
					|| isPolygon(element.getNodeName()))
			{
				String pointsString = element.getAttribute("points");
				String polyXML = "<path>\n";

				mxPolylineProducer ph = new mxPolylineProducer();
				PointsParser p = new PointsParser(ph);
				p.parse(pointsString);
				polyXML += ph.getLines();

				resultingXml.setStyleXml(this.getStyle(element, styleMap));

				if (isPolygon(element.getNodeName()))
				{
					polyXML += "<close/>\n";
					polyXML += "</path>\n";
					resultingXml.setElementXml(polyXML);
					if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
						resultingXml.setSuffixXml("<fillstroke/>\n");
					else
						resultingXml.setSuffixXml("<stroke/>\n");
				}
				else
				{
					polyXML += "</path>\n";
					resultingXml.setElementXml(polyXML);
					if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
						resultingXml.setSuffixXml("<fillstroke/>\n");
					else
						resultingXml.setSuffixXml("<stroke/>\n");
				}

				return resultingXml;
			}
			else if (isCircle(element.getNodeName()))
			{
				double cx = 0;
				double cy = 0;
				double r = 0;

				String cxString = element.getAttribute("cx");
				String cyString = element.getAttribute("cy");
				String rString = element.getAttribute("r");

				if (cxString.length() > 0)
				{
					cx = Double.valueOf(cxString);
				}
				if (cyString.length() > 0)
				{
					cy = Double.valueOf(cyString);
				}
				if (rString.length() > 0)
				{
					r = Double.valueOf(rString);

					if (r < 0)
					{
						return null; // error in SVG spec
					}
				}

				cx = roundToDecimals(cx, decimalsNum);
				cy = roundToDecimals(cy, decimalsNum);
				r = roundToDecimals(r, decimalsNum);

				resultingXml.setStyleXml(this.getStyle(element, styleMap));
				resultingXml.setElementXml("<ellipse x=\""
						+ (cx - r) + "\" y=\"" + (cy - r) + "\" w=\"" + 2 * r
						+ "\" h=\"" + 2 * r + "\"/>\n");
				if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
					resultingXml.setSuffixXml("<fillstroke/>\n");
				else
					resultingXml.setSuffixXml("<stroke/>\n");
				return resultingXml;
			}
			else if (isEllipse(element.getNodeName()))
			{
				double cx = 0;
				double cy = 0;
				double rx = 0;
				double ry = 0;

				String cxString = element.getAttribute("cx");
				String cyString = element.getAttribute("cy");
				String rxString = element.getAttribute("rx");
				String ryString = element.getAttribute("ry");

				if (cxString.length() > 0)
				{
					cx = Double.valueOf(cxString);
				}
				if (cyString.length() > 0)
				{
					cy = Double.valueOf(cyString);
				}
				if (rxString.length() > 0)
				{
					rx = Double.valueOf(rxString);

					if (rx < 0)
					{
						return null; // error in SVG spec
					}
				}
				if (ryString.length() > 0)
				{
					ry = Double.valueOf(ryString);

					if (ry < 0)
					{
						return null; // error in SVG spec
					}
				}

				cx = roundToDecimals(cx, decimalsNum);
				cy = roundToDecimals(cy, decimalsNum);
				rx = roundToDecimals(rx, decimalsNum);
				ry = roundToDecimals(ry, decimalsNum);

				resultingXml.setStyleXml(this.getStyle(element, styleMap));
				resultingXml.setElementXml("<ellipse x=\""
						+ (cx - rx) + "\" y=\"" + (cy - ry) + "\" w=\"" + 2
						* rx + "\" h=\"" + 2 * ry + "\"/>\n");
				if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
					resultingXml.setSuffixXml("<fillstroke/>\n");
				else
					resultingXml.setSuffixXml("<stroke/>\n");
				return resultingXml;
			}
			else if (isPath(element.getNodeName()))
			{
				String d = element.getAttribute("d");
				resultingXml.setElementXml(mxPathProducer.createShape(d));
				resultingXml.setStyleXml(this.getStyle(element, styleMap));
				if (isLastShapeFilled && !lastFillString.toLowerCase().equals("none"))
					resultingXml.setSuffixXml("<fillstroke/>\n");
				else
					resultingXml.setSuffixXml("<stroke/>\n");
				return resultingXml;
			}
			//TODO: check horizontal alignment
			else if (isText(element.getNodeName()))
			{
				//read data for generating the text block such as:
				// text content
				// x coord
				// y coord
				// hor alignment
				// ver alignment
				
				double x = 0;
				double y = 0;
				resultingXml.setStyleXml(this.getStyle(element, styleMap));
				
				String textContent = element.getTextContent();
				String xString = element.getAttribute("x");
				String yString = element.getAttribute("y");

				// read align as an attribute		
				String alignString = element.getAttribute("text-anchor");

				//read align as style		
				if (styleMap != null && styleMap.containsKey("text-anchor"))
				{
					alignString = (String) styleMap.get("text-anchor");
				}

				if (xString.length() > 0)
				{
					x = Double.valueOf(xString);
				}
				if (yString.length() > 0)
				{
					y = Double.valueOf(yString);
				}

				x = roundToDecimals(x, decimalsNum);
				y = roundToDecimals(y, decimalsNum);

				String textXML = new String("<text str=\"" + textContent + "\" x=\"" + x + "\" y=\"" + y); 

				if (alignString.length() > 0)
				{
					//make the conversion from SVG to mxGraph alignments
					if (alignString.toLowerCase().equals("start"))
					{
						alignString = "left";
					}
					else if (alignString.toLowerCase().equals("middle"))
					{
						alignString = "center";
					}
					else if (alignString.toLowerCase().equals("end"))
					{
						alignString = "right";
					}

					textXML += "\" align=\"" + alignString;
				}

				textXML += "\" valign=\"bottom";
				textXML += "\"/>\n";
				resultingXml.setElementXml(textXML);
				return resultingXml;
				
			}
		}

		return null;
	}

	/*
	 *
	 */
	private boolean isRectangle(String tag)
	{
		return tag.equals("svg:rect") || tag.equals("rect");
	}

	/*
	 *
	 */
	private boolean isPath(String tag)
	{
		return tag.equals("svg:path") || tag.equals("path");
	}

	/*
	 *
	 */
	private boolean isEllipse(String tag)
	{
		return tag.equals("svg:ellipse") || tag.equals("ellipse");
	}

	/*
	 *
	 */
	private boolean isLine(String tag)
	{
		return tag.equals("svg:line") || tag.equals("line");
	}

	/*
	 *
	 */
	private boolean isPolyline(String tag)
	{
		return tag.equals("svg:polyline") || tag.equals("polyline");
	}

	/*
	 *
	 */
	private boolean isCircle(String tag)
	{
		return tag.equals("svg:circle") || tag.equals("circle");
	}

	/*
	 *
	 */
	private boolean isPolygon(String tag)
	{
		return tag.equals("svg:polygon") || tag.equals("polygon");
	}

	private boolean isGroup(String tag)
	{
		return tag.equals("svg:g") || tag.equals("g");
	}

	private boolean isText(String tag)
	{
		return tag.equals("svg:text") || tag.equals("text");
	}
	
	/**
	 * Returns the stylenames in a style of the form stylename[;key=value] or an
	 * empty array if the given style does not contain any stylenames.
	 * 
	 * @param style
	 *            String of the form stylename[;stylename][;key=value].
	 * @return Returns the stylename from the given formatted string.
	 */
	protected static Map<String, Object> getStylenames(String style)
	{
		if (style != null && style.length() > 0)
		{
			Map<String, Object> result = new Hashtable<String, Object>();

			if (style != null)
			{
				String[] pairs = style.split(";");

				for (int i = 0; i < pairs.length; i++)
				{
					String[] keyValue = pairs[i].split(":");

					if (keyValue.length == 2)
					{
						result.put(keyValue[0].trim(), keyValue[1].trim());
					}
				}
			}
			return result;
		}

		return null;
	}

	private String getStyle(Element element, Map<String, Object> styleMap)
	{
		someStyleReturnedToDefault = false;
		String styleXML = "";
		isDefaultStyle = true;
		boolean isTextShape = isText(element.getNodeName());
		// parsing stroke color
		// read stroke as an attribute		
		String strokeString = element.getAttribute("stroke");

		//read stroke as style		
		if (styleMap != null && styleMap.containsKey("stroke"))
		{
			strokeString = (String) styleMap.get("stroke");
		}
		
		if (strokeString.length() > 0 && (!lastStrokeColorString.equals(strokeString) || needToRedefineStyle))
		{
			if (!isTextShape)
			{
				styleXML = "<strokecolor color=\"" + strokeString + "\"/>\n";
			}
		}
		
		if(strokeString.length() == 0 && lastStrokeColorString.length() > 0)
		{
			someStyleReturnedToDefault = true;
		}

		lastStrokeColorString = strokeString;

		// parsing stroke width
		//read stroke width as style
		String strokeWidthString="";
		if (styleMap != null && styleMap.containsKey("stroke-width"))
		{
			strokeWidthString = (String) styleMap.get("stroke-width");
		}
		
		// read stroke width as an attribute		
		String strokeWidthStringAttr = element.getAttribute("stroke-width");
		
		if (strokeWidthStringAttr.length()>0)
		{
			double strokeWidthInt = Double.parseDouble(strokeWidthStringAttr);
			strokeWidthInt = roundToDecimals(strokeWidthInt, decimalsNum);
			strokeWidthString = String.valueOf(strokeWidthInt);
		}

		if(strokeWidthString.length() == 0 && lastStrokeWidthString.length() > 0)
		{
			someStyleReturnedToDefault = true;
		}

		if (strokeWidthString.length() > 0 && (!lastStrokeWidthString.equals(strokeString) || needToRedefineStyle))
		{
			if (!isTextShape)
			{
				styleXML += "<strokewidth width=\"" + strokeWidthString + "\"/>\n";
			}
		}
		else if ((strokeWidthString.length() == 0)
				&& (lastStrokeWidthString.length() > 0)
				&& (!lastStrokeWidthString.equals("1")))
		{
			if (!isTextShape)
			{
				styleXML += "<strokewidth width=\"1\"/>\n";
			}
		}
		lastStrokeWidthString = strokeWidthString;

		// parsing fill style
		// read fill style as an attribute		
		String fillString = element.getAttribute("fill");

		//read fill style as style		
		if (styleMap != null && styleMap.containsKey("fill"))
		{
			fillString = (String) styleMap.get("fill");
		}

		if(fillString.length() == 0 && lastFillString.length() > 0)
		{
			someStyleReturnedToDefault = true;
		}

		if (fillString.length() > 0 && !fillString.equals("none") && (!lastFillString.equals(fillString) || needToRedefineStyle))
		{
			// TODO: solution for gradient fill
			if (fillString.startsWith("url"))
			{
				//				isInBackground = true;			// it's a gradient fill, so we need to put it to a background block
			}
			else if ((!fillString.equals(lastFillString) || needToRedefineStyle) && !isTextShape)
			{
				styleXML += "<fillcolor color=\"" + fillString + "\"/>\n";
			}
		}
		lastFillString = fillString;

		if (fillString.equals("none"))
			isLastShapeFilled = false;
		else
			isLastShapeFilled = true;
		

		// parsing stroke linejoin
		// read linejoin as an attribute		
		String strokeLinejoinString = element.getAttribute("stroke-linejoin");

		//read linejoin as style		
		if (styleMap != null && styleMap.containsKey("stroke-linejoin"))
		{
			strokeLinejoinString = (String) styleMap.get("stroke-linejoin");
		}

		if(strokeLinejoinString.length() == 0 && lastStrokeLinejoinString.length() > 0)
		{
			someStyleReturnedToDefault = true;
		}

		if (strokeLinejoinString.length() > 0 && (!lastStrokeLinejoinString.equals(strokeLinejoinString) || needToRedefineStyle))
		{
			styleXML += "<linejoin join=\"" + strokeLinejoinString + "\"/>\n";
			lastStrokeLinejoinString = strokeLinejoinString;
		}
		else if ((strokeLinejoinString.length() == 0)
				&& (lastStrokeLinejoinString.length() > 0)
				&& (!lastStrokeLinejoinString.equals("miter")))
		{
			styleXML += "<linejoin join=\"miter\"/>\n";
			lastStrokeLinejoinString = "miter";
		}
		lastStrokeLinejoinString = strokeLinejoinString;

		// parsing stroke linecap
		// read linecap as an attribute		
		String strokeLinecapString = element.getAttribute("stroke-linecap");

		//read linecap as style		
		if (styleMap != null && styleMap.containsKey("stroke-linecap"))
		{
			strokeLinecapString = (String) styleMap.get("stroke-linecap");
		}

		if(strokeLinecapString.length() == 0 && lastStrokeLinecapString.length() > 0)
		{
			someStyleReturnedToDefault = true;
		}

		if (strokeLinecapString.length() > 0 && (!lastStrokeLinecapString.equals(strokeLinecapString) || needToRedefineStyle))
		{
			styleXML += "<linecap cap=\"" + strokeLinecapString + "\"/>\n";
			lastStrokeLinecapString = strokeLinecapString;
		}
		else if ((strokeLinecapString.length() == 0)
				&& (lastStrokeLinecapString.length() > 0)
				&& (!lastStrokeLinecapString.equals("butt")))
		{
			styleXML += "<linecap cap=\"butt\"/>\n";
			lastStrokeLinecapString = "butt";
		}
		lastStrokeLinecapString = strokeLinecapString;

		// parsing stroke miter limit
		// read miter limit as an attribute		
		String strokeMiterLimitString = element
				.getAttribute("stroke-miterlimit");

		//read miter limit as style		
		if (styleMap != null && styleMap.containsKey("stroke-miterlimit"))
		{
			strokeMiterLimitString = (String) styleMap.get("stroke-miterlimit");
		}

		if(strokeMiterLimitString.length() == 0 && lastStrokeMiterLimitString.length() > 0)
		{
			someStyleReturnedToDefault = true;
		}

		if (strokeMiterLimitString.length() > 0 && (!lastStrokeMiterLimitString.equals(strokeMiterLimitString) || needToRedefineStyle))
		{
			styleXML += "<miterlimit limit=\"" + strokeMiterLimitString
					+ "\"/>\n";
			lastStrokeMiterLimitString = strokeMiterLimitString;
		}
		else if ((strokeMiterLimitString.length() == 0)
				&& (lastStrokeMiterLimitString.length() > 0)
				&& (!lastStrokeMiterLimitString.equals("4")))
		{
			styleXML += "<miterlimit limit=\"4\"/>\n";
			lastStrokeMiterLimitString = "4";
		}
		lastStrokeMiterLimitString = strokeMiterLimitString;

		// parsing stroke dash pattern 
		// read dash array as an attribute
		// TODO: the dash pattern doesn't work, only dashed or not
		String dashPatternString = element.getAttribute("stroke-dasharray");

		//read dash array as style		
		if (styleMap != null && styleMap.containsKey("stroke-dasharray"))
		{
			dashPatternString = (String) styleMap.get("stroke-dasharray");
		}

		if (dashPatternString.length() > 0
				&& !lastDashPatternString.equals(dashPatternString)
				&& !dashPatternString.equals("none"))
		{
			dashPatternString = dashPatternString.replace(',', ' ');
			dashPatternString = dashPatternString.trim().replaceAll(" +", " ");

			styleXML += "<dashed dashed=\"1\"/>\n<dashpattern pattern=\""
					+ dashPatternString + "\"/>\n";
			lastDashPatternString = dashPatternString;
			wasLastDashed = true;
		}
		else if (wasLastDashed
				&& (dashPatternString.length() == 0 || dashPatternString
				.equals("none")))
		{
			styleXML += "<dashed dashed=\"0\"/>\n";
			wasLastDashed = false;
			lastDashPatternString = "";
		}

		// since mx doesn't support separate stroke and fill opacity, they are merged

		// read fill opacity as an attribute		
		String fillOpacityString = element.getAttribute("fill-opacity");
		// read stroke opacity as an attribute		
		String strokeOpacityString = element.getAttribute("stroke-opacity");

		//read fill opacity as style		
		if (styleMap != null && styleMap.containsKey("fill-opacity"))
		{
			fillOpacityString = (String) styleMap.get("fill-opacity");
		}

		//read stroke opacity as style		
		if (styleMap != null && styleMap.containsKey("stroke-opacity"))
		{
			strokeOpacityString = (String) styleMap.get("stroke-opacity");
		}

		// check if stroke opacity is defined and differs from previous
		if ((strokeOpacityString.length() > 0)
				&& ((!lastFillOpacityString.equals(fillOpacityString) || (!lastStrokeOpacityString
						.equals(strokeOpacityString)))))
		{
			if (!strokeOpacityString.equals(lastAlphaString)
					&& !strokeOpacityString.equals("0"))
			{
				styleXML += "<alpha alpha=\"" + strokeOpacityString + "\"/>\n";
				lastStrokeOpacityString = strokeOpacityString;
				lastAlphaString = strokeOpacityString;
			}
		}
		// check if fill opacity is defined and differs from previous
		else if ((fillOpacityString.length() > 0)
				&& ((!lastFillOpacityString.equals(fillOpacityString) || (!lastStrokeOpacityString
						.equals(strokeOpacityString)))))
		{
			if (!fillOpacityString.equals(lastAlphaString)
					&& !fillOpacityString.equals("0"))
			{
				styleXML += "<alpha alpha=\"" + fillOpacityString + "\"/>\n";
				lastFillOpacityString = fillOpacityString;
				lastAlphaString = fillOpacityString;
			}
		}
		else if ((fillOpacityString.equals("1") || strokeOpacityString.length() == 0)
				&& (strokeOpacityString.length() == 0 || strokeOpacityString
				.equals("1")))
		{
			if (!lastAlphaString.equals("1"))
			{
				styleXML += "<alpha alpha=\"1\"/>\n";
				lastStrokeOpacityString = "1";
				lastAlphaString = "1";
			}
		}

		if (isTextShape)
		{
			//stroke, fill, style, font, size
			
			int currFontStyle = 0;
			//parsing font color
			if (fillString.length()==0 || fillString.equals("none"))
			{
				// strokecolor becomes font color
				if (strokeString.length() > 0
						&& (!strokeString.equals(lastFontColorString)))
				{
					styleXML = "<fontcolor color=\"" + strokeString + "\"/>\n";
					lastFontColorString = strokeString;
				}
			}
			else
			{
				// fillcolor becomes font color
				if (fillString.length() > 0
						&& (!fillString.equals(lastFontColorString)))
				{
					styleXML = "<fontcolor color=\"" + fillString + "\"/>\n";
					lastFontColorString = fillString;
				}
			}
			
			// if neither fill nor stroke are specified for text, we assume black
			if ((fillString.length()==0 || fillString.equals("none")) && (strokeString.length()==0 || strokeString.equals("none")) && (!lastFontColorString.equals("#000000")))
			{
				styleXML = "<fontcolor color=\"#000000\"/>\n";
				lastFontColorString = "#000000";
			}
			
			// parsing font style as plain, bold, italic and underlined (since svg doesn't have a shadowed font style, we don't check for it)

			// read font weight as an attribute		
			String fontWeightString = element.getAttribute("font-weight");

			//read font weight as style		
			if (styleMap != null && styleMap.containsKey("font-weight"))
			{
				fontWeightString = (String) styleMap.get("font-weight");
			}

			if (fontWeightString.equals("bold"))
			{
				currFontStyle += 1;
			}

			// read font style as an attribute		
			String fontStyleString = element.getAttribute("font-style");

			//read font style as style		
			if (styleMap != null && styleMap.containsKey("font-style"))
			{
				fontStyleString = (String) styleMap.get("font-style");
			}

			if (fontStyleString.equals("italic"))
			{
				currFontStyle += 2;
			}
			
			// FIXME: this part is commented out, because underline style doesn't work (the text disappears if used)
/*
			// read font decoration as an attribute		
			String fontDecorationString = element.getAttribute("text-decoration");

			//read font decoration as style		
			if (styleMap != null && styleMap.containsKey("text-decoration"))
			{
				fontDecorationString = (String) styleMap.get("text-decoration");
			}

			if (fontDecorationString.equals("underline") || fontDecorationString.equals("overline") ||fontDecorationString.equals("line-through"))
			{
				currFontStyle += 4;
			}
*/			
			if (currFontStyle != lastFontStyle)
			{
				styleXML += "<fontstyle style=\"" + currFontStyle + "\"/>\n";
				lastFontStyle = currFontStyle;
			}
			
			// read font size as an attribute		
			String fontSizeString = element.getAttribute("font-size");

			//read font size as style		
			if (styleMap != null && styleMap.containsKey("font-size"))
			{
				fontSizeString = (String) styleMap.get("font-size");
			}
			fontSizeString = fontSizeString.replaceAll("pt", "");
			fontSizeString = fontSizeString.replaceAll("px", "");

			// setting default font size
			if (fontSizeString.length() == 0)
				fontSizeString="12";
			
			if (fontSizeString.length() > 0 && (!fontSizeString.equals(lastFontSizeString)))
			{
				styleXML += "<fontsize size=\"" + fontSizeString + "\"/>\n";
				lastFontSizeString = fontSizeString;
			}
			
			// read font family as an attribute		
			String fontFamilyString = element.getAttribute("font-family");

			//read font family as style		
			if (styleMap != null && styleMap.containsKey("font-family"))
			{
				fontFamilyString = (String) styleMap.get("font-family");
			}

			// setting default font family
			if (fontFamilyString.length() == 0)
				fontFamilyString="Arial";
			
			if (fontFamilyString.length() > 0 && (!fontFamilyString.equals(lastFontFamilyString)))
			{
				styleXML += "<fontfamily family=\"" + fontFamilyString + "\"/>\n";
				lastFontFamilyString = fontFamilyString;
			}
		}
		
		if ((strokeString.length() > 0) ||
			(strokeWidthString.length() > 0) ||
			(fillString.length() > 0 && !fillString.equals("none")) ||
			(strokeLinejoinString.length() > 0) ||
			(strokeLinecapString.length() > 0) ||
			(strokeMiterLimitString.length() > 0) ||
			(dashPatternString.length() > 0) ||
			(strokeOpacityString.length() > 0) ||
			(fillOpacityString.length() > 0))
			isDefaultStyle = false;	

		needToRedefineStyle = false;
		return styleXML;
	}

	public String getXMLString()
	{
		return this.xmlString;
	}

	protected class svgShape
	{
		protected Shape shape;

		/**
		 * Contains an array of key, value pairs that represent the style of the
		 * cell.
		 */
		protected Map<String, Object> style;

		protected List<svgShape> subShapes;

		/**
		 * Holds the current value to which the shape is scaled in X
		 */
		protected double currentXScale;

		/**
		 * Holds the current value to which the shape is scaled in Y
		 */
		protected double currentYScale;

		public svgShape(Shape shape, Map<String, Object> style)
		{
			this.shape = shape;
			this.style = style;
			subShapes = new ArrayList<svgShape>();
		}

		public double getCurrentXScale()
		{
			return currentXScale;
		}

		public void setCurrentXScale(double currentXScale)
		{
			this.currentXScale = currentXScale;
		}

		public double getCurrentYScale()
		{
			return currentYScale;
		}

		public void setCurrentYScale(double currentYScale)
		{
			this.currentYScale = currentYScale;
		}
	}

	// set the top left of the drawing to vector [x,y]
	public String setRoot (double x, double y, String xmlCode)
	{
		
		// TODO a possible optimization would be that instead of re-parsing for these values, they could be inserted at start with a corresponding simple value, which would need much more complex code
		String xmlOut = this.setRootAttr(x, xmlCode, " x=\"", decimalsNum);
		xmlOut = this.setRootAttr(x, xmlOut, " x1=\"", decimalsNum);
		xmlOut = this.setRootAttr(x, xmlOut, " x2=\"", decimalsNum);
		xmlOut = this.setRootAttr(x, xmlOut, " x3=\"", decimalsNum);
		xmlOut = this.setRootAttr(y, xmlOut, " y=\"", decimalsNum);
		xmlOut = this.setRootAttr(y, xmlOut, " y1=\"", decimalsNum);
		xmlOut = this.setRootAttr(y, xmlOut, " y2=\"", decimalsNum);
		xmlOut = this.setRootAttr(y, xmlOut, " y3=\"", decimalsNum);

		// cleaning other real values
		xmlOut = this.setRootAttr(0, xmlOut, " h=\"", decimalsNum);
		xmlOut = this.setRootAttr(0, xmlOut, " w=\"", decimalsNum);
		xmlOut = this.setRootAttr(0, xmlOut, " width=\"", decimalsNum);
		xmlOut = this.setRootAttr(0, xmlOut, " rx=\"", decimalsNum);
		xmlOut = this.setRootAttr(0, xmlOut, " ry=\"", decimalsNum);
		xmlOut = this.setRootAttr(0, xmlOut, " x-axis-rotation=\"", decimalsNum);
		
		return xmlOut;
	}

	// set the top left of the drawing to vector [x,y]
	private String setRootAttr (double coord, String xmlCode, String lookFor, int decimalsNum)
	{
		int startIndex=-1;
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

				
				


				endIndexOld=endIndex;
				startIndexOld = startIndex;
			}
		}
		xmlOut += xmlCode.substring(endIndex, xmlCode.length());

		xmlCode = xmlOut;

		return xmlCode;
	}

	private Element transformShape(Element element)
	{
		// while the transform string isn't empty, read the first one and call the appropriate method for the transform and shape
		String transformString = element.getAttribute("transform");
		int endIndex;
		String currentTransformString;
		transformString = transformString.replaceAll("\\s{2,}", " ");

		if (element.getNodeName()=="path")
		{
			String pathString = element.getAttribute("d");
			pathString = pathString.replaceAll(",", " ");
			pathString = pathString.replaceAll("\\s{2,}", " ");
			element.setAttribute("d", pathString);
		}

		while (transformString.indexOf(")")!=-1)
		{
			endIndex = transformString.indexOf(")");
			currentTransformString = transformString.substring(0, endIndex+1);
			//translate
			if (currentTransformString.contains("translate"))
			{
				double tx=0;
				double ty=0;
				int startCurrIndex = currentTransformString.indexOf("translate(");
				int endCurrIndex = currentTransformString.indexOf(",",startCurrIndex);
				String xString = currentTransformString.substring(startCurrIndex+10, endCurrIndex);
				startCurrIndex = endCurrIndex;
				endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
				String yString = currentTransformString.substring(startCurrIndex+1, endCurrIndex);
				tx = Double.valueOf(xString);
				ty = Double.valueOf(yString);

				// calling the translation for the appropriate shape
				String shapeName = element.getNodeName();
				if (shapeName.equals("line"))
					element = translateLine(element,tx,ty);
				else if (shapeName.equals("rect"))
					element = translateRect(element,tx,ty);
				else if (shapeName.equals("ellipse")||shapeName.equals("circle"))
					element = translateEllipse(element,tx,ty);
				else if (shapeName.equals("polygon")||shapeName.equals("polyline"))
					element = translatePoly(element,tx,ty);
				else if (shapeName.equals("path"))
					element = translatePath(element,tx,ty);
			}
			//scale
			else if (currentTransformString.contains("scale"))
			{
				double sx=0;
				double sy=0;
				int startCurrIndex = currentTransformString.indexOf("scale(");
				int endCurrIndex = currentTransformString.indexOf(",",startCurrIndex);
				String xString;
				String yString;
				if (endCurrIndex==-1)
				{
					endCurrIndex = currentTransformString.length()-1;
					xString = currentTransformString.substring(startCurrIndex+6, endCurrIndex);
					yString = currentTransformString.substring(startCurrIndex+6, endCurrIndex);
				}
				else
				{
					xString = currentTransformString.substring(startCurrIndex+6, endCurrIndex);
					startCurrIndex = endCurrIndex;
					endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
					yString = currentTransformString.substring(startCurrIndex+1, endCurrIndex);
				}
				sx = Double.valueOf(xString);
				sy = Double.valueOf(yString);

				//scaling stroke width for all shapes
				String style = element.getAttribute("style");
				Map<String, Object> styleMap = mxSvgParser.getStylenames(style);

				String oldStrokeString = element.getAttribute("stroke-width");

				if (styleMap != null && styleMap.containsKey("stroke-width"))
				{
					oldStrokeString = (String) styleMap.get("stroke-width");
				}
				if (oldStrokeString.length() > 0)
				{
					double oldStroke = Double.valueOf(oldStrokeString);
					double strokeX = Math.abs(sx * oldStroke);
					double strokeY = Math.abs(sy * oldStroke);
					double newStroke = Math.min(strokeX, strokeY);
					String newStrokeString = String.valueOf(newStroke);
						element.setAttribute("stroke-width", newStrokeString);
				}

				// calling the scaling for the appropriate shape
				String shapeName = element.getNodeName();
				if (shapeName.equals("line"))
					element = scaleLine(element,sx,sy);
				else if (shapeName.equals("rect"))
					element = scaleRect(element,sx,sy);
				else if (shapeName.equals("ellipse")||shapeName.equals("circle"))
					element = scaleEllipse(element,sx,sy);
				else if (shapeName.equals("polygon")||shapeName.equals("polyline"))
					element = scalePoly(element,sx,sy);
				else if (shapeName.equals("path"))
					element = scalePath(element,sx,sy);
			}
			// skewX
			else if (currentTransformString.contains("skewX"))
			{
				double skewX=0;
				int startCurrIndex = currentTransformString.indexOf("skewX(");
				int endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
				String skewAngleString = currentTransformString.substring(startCurrIndex+6, endCurrIndex);
				skewX = Double.valueOf(skewAngleString);
				skewX = Math.toRadians(skewX);
				// generate a skew matrix and call a matrix transform
				double a = 1;
				double b = 0;
				double c = Math.tan(skewX);
				double d = 1;
				double e = 0;
				double f = 0;

				String shapeName = element.getNodeName();
				if (shapeName.equals("line"))
					element = matrixTransformLine(element, a, b, c, d, e, f);
				else if (shapeName.equals("rect"))
					element = matrixTransformRect(element, a, b, c, d, e, f);
				else if (shapeName.equals("ellipse")||shapeName.equals("circle"))
					element = matrixTransformEllipse(element, a, b, c, d, e, f);
				else if (shapeName.equals("polygon")||shapeName.equals("polyline"))
					element = matrixTransformPoly(element, a, b, c, d, e, f);
				else if (shapeName.equals("path"))
					element = matrixTransformPath(element, a, b, c, d, e, f);

			}
			// skewY
			else if (currentTransformString.contains("skewY"))
			{
				double skewY=0;
				int startCurrIndex = currentTransformString.indexOf("skewY(");
				int endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
				String skewAngleString = currentTransformString.substring(startCurrIndex+6, endCurrIndex);
				skewY = Double.valueOf(skewAngleString);
				skewY = Math.toRadians(skewY);
				// generate a skew matrix and call a matrix transform
				double a = 1;
				double b = Math.tan(skewY);
				double c = 0;
				double d = 1;
				double e = 0;
				double f = 0;

				String shapeName = element.getNodeName();
				if (shapeName.equals("line"))
					element = matrixTransformLine(element, a, b, c, d, e, f);
				else if (shapeName.equals("rect"))
					element = matrixTransformRect(element, a, b, c, d, e, f);
				else if (shapeName.equals("ellipse")||shapeName.equals("circle"))
					element = matrixTransformEllipse(element, a, b, c, d, e, f);
				else if (shapeName.equals("polygon")||shapeName.equals("polyline"))
					element = matrixTransformPoly(element, a, b, c, d, e, f);
				else if (shapeName.equals("path"))
					element = matrixTransformPath(element, a, b, c, d, e, f);

			}
			else if (currentTransformString.contains("rotate"))
			{
				double rotateAngle=0;
				double pivotX = 0;
				double pivotY = 0;
				String rotateAngleString;
				int startCurrIndex = currentTransformString.indexOf("rotate(");
				int endCurrIndex = currentTransformString.indexOf(",",startCurrIndex);

				if (endCurrIndex==-1) // default pivot point
				{
					endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
					rotateAngleString = currentTransformString.substring(startCurrIndex+7, endCurrIndex);
				}
				else // read pivot points too
				{
					rotateAngleString = currentTransformString.substring(startCurrIndex+7, endCurrIndex);
					startCurrIndex = endCurrIndex+1;
					endCurrIndex = currentTransformString.indexOf(",",startCurrIndex);
					String pivotXString = currentTransformString.substring(startCurrIndex, endCurrIndex);
					startCurrIndex = endCurrIndex+1;
					endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
					String pivotYString = currentTransformString.substring(startCurrIndex, endCurrIndex);
					pivotX = Double.valueOf(pivotXString);
					pivotY = Double.valueOf(pivotYString);
				}
				rotateAngle = Double.valueOf(rotateAngleString);
				rotateAngle = Math.toRadians(rotateAngle);
				// calling the translation for the appropriate shape
				String shapeName = element.getNodeName();
				if (shapeName.equals("line"))
					element = rotateLine(element, rotateAngle, pivotX, pivotY);
				else if (shapeName.equals("rect"))
					element = rotateRect(element, rotateAngle, pivotX, pivotY);
				else if (shapeName.equals("ellipse")||shapeName.equals("circle"))
					element = rotateEllipse(element, rotateAngle, pivotX, pivotY);
				else if (shapeName.equals("polygon")||shapeName.equals("polyline"))
					element = rotatePoly(element, rotateAngle, pivotX, pivotY);
				else if (shapeName.equals("path"))
					element = rotatePath(element, rotateAngle, pivotX, pivotY);

			}
			else if (currentTransformString.contains("matrix"))
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
				currentTransformString = currentTransformString.replaceAll(",", "");
				int startCurrIndex = currentTransformString.indexOf("matrix(");
				int endCurrIndex = currentTransformString.indexOf(" ",startCurrIndex);
				aString = currentTransformString.substring(startCurrIndex+7, endCurrIndex);

				startCurrIndex = endCurrIndex+1;
				endCurrIndex = currentTransformString.indexOf(" ",startCurrIndex);
				bString = currentTransformString.substring(startCurrIndex, endCurrIndex);

				startCurrIndex = endCurrIndex+1;
				endCurrIndex = currentTransformString.indexOf(" ",startCurrIndex);
				cString = currentTransformString.substring(startCurrIndex, endCurrIndex);

				startCurrIndex = endCurrIndex+1;
				endCurrIndex = currentTransformString.indexOf(" ",startCurrIndex);
				dString = currentTransformString.substring(startCurrIndex, endCurrIndex);

				startCurrIndex = endCurrIndex+1;
				endCurrIndex = currentTransformString.indexOf(" ",startCurrIndex);
				eString = currentTransformString.substring(startCurrIndex, endCurrIndex);

				startCurrIndex = endCurrIndex+1;
				endCurrIndex = currentTransformString.indexOf(")",startCurrIndex);
				fString = currentTransformString.substring(startCurrIndex, endCurrIndex);

				a = Double.valueOf(aString);
				b = Double.valueOf(bString);
				c = Double.valueOf(cString);
				d = Double.valueOf(dString);
				e = Double.valueOf(eString);
				f = Double.valueOf(fString);

				//scaling stroke width for all shapes
				String style = element.getAttribute("style");
				Map<String, Object> styleMap = mxSvgParser.getStylenames(style);

				String oldStrokeString = element.getAttribute("stroke-width");

				if (styleMap != null && styleMap.containsKey("stroke-width"))
				{
					oldStrokeString = (String) styleMap.get("stroke-width");
				}
				if (oldStrokeString.length() > 0)
				{
					double oldStroke = Double.valueOf(oldStrokeString);
					double strokeX = Math.abs(a * oldStroke);
					double strokeY = Math.abs(d * oldStroke);
					double newStroke = Math.min(strokeX, strokeY);
					String newStrokeString = String.valueOf(newStroke);
						element.setAttribute("stroke-width", newStrokeString);
				}
				
				String shapeName = element.getNodeName();
				if (shapeName.equals("line"))
					element = matrixTransformLine(element, a, b, c, d, e, f);
				else if (shapeName.equals("rect"))
					element = matrixTransformRect(element, a, b, c, d, e, f);
				else if (shapeName.equals("ellipse")||shapeName.equals("circle"))
					element = matrixTransformEllipse(element, a, b, c, d, e, f);
				else if (shapeName.equals("polygon")||shapeName.equals("polyline"))
					element = matrixTransformPoly(element, a, b, c, d, e, f);
				else if (shapeName.equals("path"))
					element = matrixTransformPath(element, a, b, c, d, e, f);

			}
			transformString = transformString.substring(endIndex+1);
		}
		return element;
	}

	private Element matrixTransformPath(Element element, double a, double b,
			double c, double d, double e, double f)
	{
		String pathString = element.getAttribute("d");
		pathString = pathString.replaceAll("\n", "");
		pathString = pathString.replaceAll(" {2,}", " ");
		String newPathString = "";
		int nextPartStartIndex; 

		do
		{
			// remove leading space
			while (pathString.charAt(0)==' ')
				pathString=pathString.substring(1, pathString.length());

			nextPartStartIndex = this.nextPartIndex(pathString);
			String currPathString = pathString.substring(0, nextPartStartIndex);
			newPathString += parseMatrixTransformPathPart(currPathString, a, b, c, d, e, f);
			pathString = pathString.substring(nextPartStartIndex, pathString.length());
		} while (pathString.length()>0);

		newPathString = newPathString.substring(0, (newPathString.length()-1));
		element.setAttribute("d", newPathString);
		return element;
	}

	private String parseMatrixTransformPathPart(String currPathString,
			double a, double b, double c, double d, double e, double f)
	{
		char pathType = currPathString.charAt(0);
		String newPath="error"; // if it doesn't get changes, the path type isn't recognized, so it's an error
		switch (pathType)
		{
			case 'M' : 
				return newPath = this.matrixTransformPathPartMove(currPathString, a, b, c, d, e, f, true);
			case 'm' : 
				return newPath = this.matrixTransformPathPartMove(currPathString, a, b, c, d, e, f, false);
			case 'L' : 
				return newPath = this.matrixTransformPathPartLine(currPathString, a, b, c, d, e, f, true);
			case 'l' : 
				return newPath = this.matrixTransformPathPartLine(currPathString, a, b, c, d, e, f, false);
			case 'H' : 
				return newPath = this.matrixTransformPathPartHorLine(currPathString, a, b, c, d, e, f, true);
			case 'h' : 
				return newPath = this.matrixTransformPathPartHorLine(currPathString, a, b, c, d, e, f, false);
			case 'V' : 
				return newPath = this.matrixTransformPathPartVerLine(currPathString, a, b, c, d, e, f, true);
			case 'v' : 
				return newPath = this.matrixTransformPathPartVerLine(currPathString, a, b, c, d, e, f, false);
			case 'C' : 
				return newPath = this.matrixTransformPathPartCurve(currPathString, a, b, c, d, e, f, true);
			case 'c' : 
				return newPath = this.matrixTransformPathPartCurve(currPathString, a, b, c, d, e, f, false);
			case 'S' : 
				return newPath = this.matrixTransformPathPartSmoothCurve(currPathString, a, b, c, d, e, f, true);
			case 's' : 
				return newPath = this.matrixTransformPathPartSmoothCurve(currPathString, a, b, c, d, e, f, false);
			case 'Q' : 
				return newPath = this.matrixTransformPathPartQuad(currPathString, a, b, c, d, e, f, true);
			case 'q' : 
				return newPath = this.matrixTransformPathPartQuad(currPathString, a, b, c, d, e, f, false);
			case 'T' : 
				return newPath = this.matrixTransformPathPartSmoothQuad(currPathString, a, b, c, d, e, f, true);
			case 't' : 
				return newPath = this.matrixTransformPathPartSmoothQuad(currPathString, a, b, c, d, e, f, false);
			case 'A' : 
				return newPath = this.matrixTransformPathPartArc(currPathString, a, b, c, d, e, f, true);
			case 'a' : 
				return newPath = this.matrixTransformPathPartArc(currPathString, a, b, c, d, e, f, false);
			case 'Z' : 
				return "Z ";
			case 'z' : 
				return "z ";
		}
		return newPath;	
	}
	private String matrixTransformPathPartArc(String currPathString, double a,
			double b, double c, double d, double e, double f, boolean isAbs)
	{
		double xScaleFactor = Math.sqrt((a*a) + (c*c));
		double yScaleFactor = Math.sqrt((b*b) + (d*d));
		double rx = Math.abs(getPathParam(currPathString,1) * xScaleFactor);
		double ry = Math.abs(getPathParam(currPathString,2) * yScaleFactor);
		double xRot = getPathParam(currPathString,3);
		int largeArc = (int) getPathParam(currPathString,4);
		int sweep = (int) getPathParam(currPathString,5);

		// correcting sweep if scaling is negative
		if ((a<0 && d>=0) || (a>=0 && d<0))
		{
			if (sweep==1)
				sweep=0;
			else
				sweep=1;
		}
			
		double x = getPathParam(currPathString,6);
		double y = getPathParam(currPathString,7);

		double transformRot = Math.atan2(b, a);
		transformRot = Math.toDegrees(transformRot);
		xRot = xRot + transformRot; 
		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			rx = roundToDecimals(rx, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			xRot = roundToDecimals(xRot, decimalsNum);
			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			rx = roundToDecimals(rx, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			xRot = roundToDecimals(xRot, decimalsNum);
			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "a " + rx + " " + ry + " " + xRot + " " + largeArc + " " + sweep + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}


	private String matrixTransformPathPartSmoothQuad(String currPathString,
			double a, double b, double c, double d, double e, double f,
			boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = getPathParam(currPathString,2);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "t " + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartQuad(String currPathString, double a,
			double b, double c, double d, double e, double f, boolean isAbs)
	{
		double x1 = getPathParam(currPathString,1);
		double y1 = getPathParam(currPathString,2);
		double x = getPathParam(currPathString,3);
		double y = getPathParam(currPathString,4);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;
			double x1New = x1 * a + y1 * c + e;
			double y1New = x1 * b + y1 * d + f;

			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);
			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newAbsX1 = oldAbsX1 * a + oldAbsY1 * c + e;
			double newAbsY1 = oldAbsX1 * b + oldAbsY1 * d + f;

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;
			double newRelX1 = newAbsX1 - lastPathX;
			double newRelY1 = newAbsY1 - lastPathY;

			newRelX1 = roundToDecimals(newRelX1, decimalsNum);
			newRelY1 = roundToDecimals(newRelY1, decimalsNum);
			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "q " + newRelX1 + " " + newRelY1 + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartSmoothCurve(String currPathString,
			double a, double b, double c, double d, double e, double f,
			boolean isAbs)
	{
		double x2 = getPathParam(currPathString,1);
		double y2 = getPathParam(currPathString,2);
		double x = getPathParam(currPathString,3);
		double y = getPathParam(currPathString,4);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;
			double x2New = x2 * a + y2 * c + e;
			double y2New = x2 * b + y2 * d + f;

			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);
			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newAbsX2 = oldAbsX2 * a + oldAbsY2 * c + e;
			double newAbsY2 = oldAbsX2 * b + oldAbsY2 * d + f;

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;
			double newRelX2 = newAbsX2 - lastPathX;
			double newRelY2 = newAbsY2 - lastPathY;

			newRelX2 = roundToDecimals(newRelX2, decimalsNum);
			newRelY2 = roundToDecimals(newRelY2, decimalsNum);
			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "s " + newRelX2 + " " + newRelY2 + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartCurve(String currPathString,
			double a, double b, double c, double d, double e, double f,
			boolean isAbs)
	{
		double x1 = getPathParam(currPathString,1);
		double y1 = getPathParam(currPathString,2);
		double x2 = getPathParam(currPathString,3);
		double y2 = getPathParam(currPathString,4);
		double x = getPathParam(currPathString,5);
		double y = getPathParam(currPathString,6);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;
			double x1New = x1 * a + y1 * c + e;
			double y1New = x1 * b + y1 * d + f;
			double x2New = x2 * a + y2 * c + e;
			double y2New = x2 * b + y2 * d + f;

			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);
			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newAbsX1 = oldAbsX1 * a + oldAbsY1 * c + e;
			double newAbsY1 = oldAbsX1 * b + oldAbsY1 * d + f;
			double newAbsX2 = oldAbsX2 * a + oldAbsY2 * c + e;
			double newAbsY2 = oldAbsX2 * b + oldAbsY2 * d + f;

			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;
			double newRelX1 = newAbsX1 - lastPathX;
			double newRelY1 = newAbsY1 - lastPathY;
			double newRelX2 = newAbsX2 - lastPathX;
			double newRelY2 = newAbsY2 - lastPathY;

			newRelX1 = roundToDecimals(newRelX1, decimalsNum);
			newRelY1 = roundToDecimals(newRelY1, decimalsNum);
			newRelX2 = roundToDecimals(newRelX2, decimalsNum);
			newRelY2 = roundToDecimals(newRelY2, decimalsNum);
			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "c " + newRelX1 + " " + newRelY1 + " " + newRelX2 + " " + newRelY2 + " " + newRelX + " " + newRelY + " ";

			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartVerLine(String currPathString,
			double a, double b, double c, double d, double e, double f,
			boolean isAbs)
	{
		double x = 0;
		double y = getPathParam(currPathString,1);


		if (isAbs)
		{
			x = prevPathX;
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "l " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartHorLine(String currPathString,
			double a, double b, double c, double d, double e, double f,
			boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = 0;


		if (isAbs)
		{
			y = prevPathY;
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "l " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartLine(String currPathString, double a,
			double b, double c, double d, double e, double f, boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = getPathParam(currPathString,2);


		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "l " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private String matrixTransformPathPartMove(String currPathString, double a,
			double b, double c, double d, double e, double f, boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = getPathParam(currPathString,2);


		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

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
			double newAbsX = oldAbsX * a + oldAbsY * c + e;
			double newAbsY = oldAbsX * b + oldAbsY * d + f;
			double newRelX = newAbsX - lastPathX;
			double newRelY = newAbsY - lastPathY;

			newRelX = roundToDecimals(newRelX, decimalsNum);
			newRelY = roundToDecimals(newRelY, decimalsNum);

			currPathString = "m " + newRelX + " " + newRelY + " ";
			lastPathX += newRelX;
			lastPathY += newRelY;
		}
		return currPathString;
	}

	private Element matrixTransformPoly(Element element, double a, double b,
			double c, double d, double e, double f)
	{
		String pointsString = element.getAttribute("points");
		String newPointsString = "";
		double x = 0;
		double y = 0;
		String xString;
		String yString;

		//a loop that reads the coords
		int commaIndex = pointsString.indexOf(",");
		int spaceIndex = pointsString.indexOf(" ", commaIndex+1);
		if (spaceIndex==-1)
			spaceIndex = pointsString.length();

		while ((commaIndex!=-1) && (spaceIndex>commaIndex))
		{
			//read x
			xString = pointsString.substring(0, commaIndex);
			//read y
			yString = pointsString.substring(commaIndex+1, spaceIndex);

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);


			// the actual transform
			double xNew = x * a + y * c + e;
			double yNew = x * b + y * d + f;
			// add the new coords to the new string

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			newPointsString += xNew + "," + yNew + " ";

			pointsString = pointsString.substring(spaceIndex, pointsString.length());

			commaIndex = pointsString.indexOf(",");
			spaceIndex = pointsString.indexOf(" ", commaIndex+1);
			if (spaceIndex==-1)
				spaceIndex = pointsString.length();
		}
		newPointsString = newPointsString.substring(0, (newPointsString.length()-1));
		element.setAttribute("points", newPointsString);
		return element;
	}
	private Element matrixTransformEllipse(Element element, double a, double b,
			double c, double d, double e, double f)
	{
		String cxString = element.getAttribute("cx");
		String cyString = element.getAttribute("cy");
		String rxString = element.getAttribute("rx");
		String ryString = element.getAttribute("ry");

		double cx = 0;
		double cy = 0;
		double rx = 0;
		double ry = 0;

		if (cxString.length() > 0)
			cx = Double.valueOf(cxString);
		if (cyString.length() > 0)
			cy = Double.valueOf(cyString);
		if (rxString.length() > 0)
			rx = Double.valueOf(rxString);
		if (ryString.length() > 0)
			ry = Double.valueOf(ryString);

		double x1 = cx;
		double y1 = cy-ry;
		double x2 = cx+rx;
		double y2 = cy;
		double x3 = cx;
		double y3 = cy+ry;
		double x4 = cx-rx;
		double y4 = cy;

		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);
		x3 = roundToDecimals(x3, decimalsNum);
		y3 = roundToDecimals(y3, decimalsNum);
		x4 = roundToDecimals(x4, decimalsNum);
		y4 = roundToDecimals(y4, decimalsNum);
		rx = roundToDecimals(rx, decimalsNum);
		ry = roundToDecimals(ry, decimalsNum);

		String pathString = "M " + x1 + " " + y1 + 
				" A " + rx + " " + ry + " 0 0 1 " + x2 + " " + y2 + 
				" A " + rx + " " + ry + " 0 0 1 " + x3 + " " + y3 + 
				" A " + rx + " " + ry + " 0 0 1 " + x4 + " " + y4 + 
				" A " + rx + " " + ry + " 0 0 1 " + x1 + " " + y1 + " Z ";

		element.removeAttribute("cx");
		element.removeAttribute("cy");
		element.removeAttribute("rx");
		element.removeAttribute("ry");

		// this chunk simply renames the element from "rect" to "polygon" since there is no other way
		Document doc = element.getOwnerDocument();
		Element newElement = doc.createElement("path");
		NamedNodeMap attrs = element.getAttributes();
		for (int i=0; i<attrs.getLength(); i++) {
			Attr attr2 = (Attr)doc.importNode(attrs.item(i), true);
			newElement.getAttributes().setNamedItem(attr2);
		}
		while (element.hasChildNodes()) {
			newElement.appendChild(element.getFirstChild());
		}
		// Replace the old node with the new node
		element.getParentNode().replaceChild(newElement, element);
		newElement.setAttribute("d", pathString);
		newElement = this.transformShape(newElement);
		return newElement;
	}

	private Element matrixTransformRect(Element element, double a, double b,
			double c, double d, double e, double f)
	{
		String xString = element.getAttribute("x");
		String yString = element.getAttribute("y");
		String wString = element.getAttribute("width");
		String hString = element.getAttribute("height");
		String rxString = element.getAttribute("rx");
		String ryString = element.getAttribute("ry");

		double x = 0;
		double y = 0;
		double w = 0;
		double h = 0;
		double rx = 0;
		double ry = 0;

		if (xString.length() > 0)
			x = Double.valueOf(xString);
		if (yString.length() > 0)
			y = Double.valueOf(yString);
		if (wString.length() > 0)
			w = Double.valueOf(wString);
		if (hString.length() > 0)
			h = Double.valueOf(hString);
		if (rxString.length() > 0)
			rx = Double.valueOf(rxString);
		if (ryString.length() > 0)
			ry = Double.valueOf(ryString);

		double x1 = x;
		double y1 = y;
		double x2 = x;
		double y2 = y+h;
		double x3 = x + w;
		double y3 = y+h;
		double x4 = x + w;
		double y4 = y;

		if (rx==0 && ry==0)
		{
			//rect

			double x1New = x1 * a + y1 * c + e;
			double y1New = x1 * b + y1 * d + f;
			double x2New = x2 * a + y2 * c + e;
			double y2New = x2 * b + y2 * d + f;
			double x3New = x3 * a + y3 * c + e;
			double y3New = x3 * b + y3 * d + f;
			double x4New = x4 * a + y4 * c + e;
			double y4New = x4 * b + y4 * d + f;

			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);
			x3New = roundToDecimals(x3New, decimalsNum);
			y3New = roundToDecimals(y3New, decimalsNum);
			x4New = roundToDecimals(x4New, decimalsNum);
			y4New = roundToDecimals(y4New, decimalsNum);

			String pointsString = x1New + "," + y1New + " " + x2New + "," + y2New + " " + x3New + "," + y3New + " " + x4New + "," + y4New + " ";

			element.removeAttribute("x");
			element.removeAttribute("y");
			element.removeAttribute("width");
			element.removeAttribute("height");

			// this chunk simply renames the element from "rect" to "polygon" since there is no other way
			Document doc = element.getOwnerDocument();
			Element newElement = doc.createElement("polygon");
			NamedNodeMap attrs = element.getAttributes();
			for (int i=0; i<attrs.getLength(); i++) {
				Attr attr2 = (Attr)doc.importNode(attrs.item(i), true);
				newElement.getAttributes().setNamedItem(attr2);
			}
			while (element.hasChildNodes()) {
				newElement.appendChild(element.getFirstChild());
			}
			// Replace the old node with the new node
			element.getParentNode().replaceChild(newElement, element);
			newElement.setAttribute("points", pointsString);
			return newElement;
		}
		else // roundrect
		{

			x = roundToDecimals(x, decimalsNum);
			y1 = roundToDecimals(y1, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			h = roundToDecimals(h, decimalsNum);
			rx = roundToDecimals(rx, decimalsNum);
			w = roundToDecimals(w, decimalsNum);

			String pathString =  "M " + x + " " + (y1+ry) + 
					" v " + (h - 2 * ry) +
					" a " + rx + " " + ry + " 0 0 0 " + rx + " " + ry +
					" h " + (w - 2 * rx) +
					" a " + rx + " " + ry + " 0 0 0 " + rx + " " + (-ry) +
					" v " + (2 * ry - h) + 
					" a " + rx + " " + ry + " 0 0 0 " + (-rx) + " " + (-ry) +
					" h " + (2 * rx - w) +
					" a " + rx + " " + ry + " 0 0 0 " + (-rx) + " " + ry +
					" Z ";

			element.removeAttribute("x");
			element.removeAttribute("y");
			element.removeAttribute("height");
			element.removeAttribute("width");
			element.removeAttribute("rx");
			element.removeAttribute("ry");

			// this chunk simply renames the element from "rect" to "path" since there is no other way
			Document doc = element.getOwnerDocument();
			Element newElement = doc.createElement("path");
			NamedNodeMap attrs = element.getAttributes();
			for (int i=0; i<attrs.getLength(); i++) {
				Attr attr2 = (Attr)doc.importNode(attrs.item(i), true);
				newElement.getAttributes().setNamedItem(attr2);
			}
			while (element.hasChildNodes()) {
				newElement.appendChild(element.getFirstChild());
			}
			// Replace the old node with the new node
			element.getParentNode().replaceChild(newElement, element);
			newElement.setAttribute("d", pathString);
			newElement = this.transformShape(newElement);
			return newElement;
		}
	}

	private Element matrixTransformLine(Element element, double a, double b,
			double c, double d, double e, double f)
	{
		String x1String = element.getAttribute("x1");
		String y1String = element.getAttribute("y1");
		String x2String = element.getAttribute("x2");
		String y2String = element.getAttribute("y2");

		double x1 = 0;
		double y1 = 0;
		double x2 = 0;
		double y2 = 0;

		if (x1String.length() > 0)
			x1 = Double.valueOf(x1String);
		if (y1String.length() > 0)
			y1 = Double.valueOf(y1String);
		if (x2String.length() > 0)
			x2 = Double.valueOf(x2String);
		if (y2String.length() > 0)
			y2 = Double.valueOf(y2String);

		double x1new = x1 * a + y1 * c + e;
		double y1new = x1 * b + y1 * d + f;

		double x2new = x2 * a + y2 * c + e;
		double y2new = x2 * b + y2 * d + f;

		x1new = roundToDecimals(x1new, decimalsNum);
		y1new = roundToDecimals(y1new, decimalsNum);
		x2new = roundToDecimals(x2new, decimalsNum);
		y2new = roundToDecimals(y2new, decimalsNum);

		element.setAttribute("x1", Double.toString(x1new));
		element.setAttribute("y1", Double.toString(y1new));
		element.setAttribute("x2", Double.toString(x2new));
		element.setAttribute("y2", Double.toString(y2new));		
		return element;
	}

	private Element rotateEllipse(Element element, double rotateAngle,
			double pivotX, double pivotY)
	{
		String cxString = element.getAttribute("cx");
		String cyString = element.getAttribute("cy");
		String rxString = element.getAttribute("rx");
		String ryString = element.getAttribute("ry");

		double cx = 0;
		double cy = 0;
		double rx = 0;
		double ry = 0;

		if (cxString.length() > 0)
			cx = Double.valueOf(cxString);
		if (cyString.length() > 0)
			cy = Double.valueOf(cyString);
		if (rxString.length() > 0)
			rx = Double.valueOf(rxString);
		if (ryString.length() > 0)
			ry = Double.valueOf(ryString);

		double x1 = cx;
		double y1 = cy-ry;
		double x2 = cx+rx;
		double y2 = cy;
		double x3 = cx;
		double y3 = cy+ry;
		double x4 = cx-rx;
		double y4 = cy;

		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);
		x3 = roundToDecimals(x3, decimalsNum);
		y3 = roundToDecimals(y3, decimalsNum);
		x4 = roundToDecimals(x4, decimalsNum);
		y4 = roundToDecimals(y4, decimalsNum);
		rx = roundToDecimals(rx, decimalsNum);
		ry = roundToDecimals(ry, decimalsNum);

		String pathString = "M " + x1 + " " + y1 + 
				" A " + rx + " " + ry + " 0 0 1 " + x2 + " " + y2 + 
				" A " + rx + " " + ry + " 0 0 1 " + x3 + " " + y3 + 
				" A " + rx + " " + ry + " 0 0 1 " + x4 + " " + y4 + 
				" A " + rx + " " + ry + " 0 0 1 " + x1 + " " + y1 + " Z ";

		element.removeAttribute("cx");
		element.removeAttribute("cy");
		element.removeAttribute("rx");
		element.removeAttribute("ry");

		// this chunk simply renames the element from "rect" to "polygon" since there is no other way
		Document doc = element.getOwnerDocument();
		Element newElement = doc.createElement("path");
		NamedNodeMap attrs = element.getAttributes();
		for (int i=0; i<attrs.getLength(); i++) {
			Attr attr2 = (Attr)doc.importNode(attrs.item(i), true);
			newElement.getAttributes().setNamedItem(attr2);
		}
		while (element.hasChildNodes()) {
			newElement.appendChild(element.getFirstChild());
		}
		// Replace the old node with the new node
		element.getParentNode().replaceChild(newElement, element);
		newElement.setAttribute("d", pathString);
		newElement = this.transformShape(newElement);
		return newElement;
	}

	private Element rotatePath(Element element, double rotateAngle,
			double pivotX, double pivotY)
	{
		String pathString = element.getAttribute("d");
		pathString = pathString.replaceAll("\n", "");
		pathString = pathString.replaceAll(" {2,}", " ");
		String newPathString = "";
		int nextPartStartIndex; 

		do
		{
			// remove leading space
			while (pathString.charAt(0)==' ')
				pathString=pathString.substring(1, pathString.length());

			nextPartStartIndex = this.nextPartIndex(pathString);
			String currPathString = pathString.substring(0, nextPartStartIndex);
			newPathString += parseRotatePathPart(currPathString, rotateAngle, pivotX, pivotY);
			pathString = pathString.substring(nextPartStartIndex, pathString.length());
		} while (pathString.length()>0);

		newPathString = newPathString.substring(0, (newPathString.length()-1));
		element.setAttribute("d", newPathString);
		return element;
	}

	private String parseRotatePathPart(String currPathString,
			double rotateAngle, double pivotX, double pivotY)
	{
		char pathType = currPathString.charAt(0);
		String newPath="error"; // if it doesn't get changes, the path type isn't recognized, so it's an error
		switch (pathType)
		{
			case 'M' : 
				return newPath = this.rotatePathPartMove(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'm' : 
				return newPath = this.rotatePathPartMove(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'L' : 
				return newPath = this.rotatePathPartLine(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'l' : 
				return newPath = this.rotatePathPartLine(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'H' : 
				return newPath = this.rotatePathPartHorLine(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'h' : 
				return newPath = this.rotatePathPartHorLine(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'V' : 
				return newPath = this.rotatePathPartVerLine(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'v' : 
				return newPath = this.rotatePathPartVerLine(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'C' : 
				return newPath = this.rotatePathPartCurve(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'c' : 
				return newPath = this.rotatePathPartCurve(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'S' : 
				return newPath = this.rotatePathPartSmoothCurve(currPathString, rotateAngle, pivotX, pivotY, true);
			case 's' : 
				return newPath = this.rotatePathPartSmoothCurve(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'Q' : 
				return newPath = this.rotatePathPartQuad(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'q' : 
				return newPath = this.rotatePathPartQuad(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'T' : 
				return newPath = this.rotatePathPartSmoothQuad(currPathString, rotateAngle, pivotX, pivotY, true);
			case 't' : 
				return newPath = this.rotatePathPartSmoothQuad(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'A' : 
				return newPath = this.rotatePathPartArc(currPathString, rotateAngle, pivotX, pivotY, true);
			case 'a' : 
				return newPath = this.rotatePathPartArc(currPathString, rotateAngle, pivotX, pivotY, false);
			case 'Z' : 
				return "Z ";
			case 'z' : 
				return "z ";
		}
		return newPath;
	}

	private String rotatePathPartArc(String currPathString, double rotateAngle,
			double pivotX, double pivotY, boolean isAbs)
	{
		double rx = getPathParam(currPathString,1);
		double ry = getPathParam(currPathString,2);
		double xRot = getPathParam(currPathString,3);
		int largeArc = (int) getPathParam(currPathString,4);
		int sweep = (int) getPathParam(currPathString,5);
		double x = getPathParam(currPathString,6);
		double y = getPathParam(currPathString,7);


		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;

			rotateAngle = Math.toDegrees(rotateAngle);

			rx = roundToDecimals(rx, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			xRot = roundToDecimals(xRot, decimalsNum);
			rotateAngle = roundToDecimals(rotateAngle, decimalsNum);
			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "A " + rx + " " + ry + " " + (xRot+rotateAngle) + " " + largeArc + " " + sweep + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);

			rotateAngle = Math.toDegrees(rotateAngle);

			rx = roundToDecimals(rx, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			rotateAngle = roundToDecimals(rotateAngle, decimalsNum);
			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "a " + rx + " " + ry + " " + rotateAngle + " " + largeArc + " " + sweep + " " + xNew + " " + yNew + " ";

			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartSmoothQuad(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = getPathParam(currPathString,2);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "T " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "t " + xNew + " " + yNew + " ";

			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartQuad(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x1 = getPathParam(currPathString,1);
		double y1 = getPathParam(currPathString,2);
		double x = getPathParam(currPathString,3);
		double y = getPathParam(currPathString,4);
		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			x1 -= pivotX;
			y1 -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			double x1New = x1 * Math.cos(rotateAngle) - y1 * Math.sin(rotateAngle);
			double y1New = x1 * Math.sin(rotateAngle) + y1 * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;
			x1New += pivotX;
			y1New += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);
			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);

			currPathString = "Q " + x1New + " " + y1New + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			double x1New = x1 * Math.cos(rotateAngle) - y1 * Math.sin(rotateAngle);
			double y1New = x1 * Math.sin(rotateAngle) + y1 * Math.cos(rotateAngle);

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);
			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);

			currPathString = "q " + x1New + " " + y1New + " " + xNew + " " + yNew + " ";

			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartSmoothCurve(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x2 = getPathParam(currPathString,1);
		double y2 = getPathParam(currPathString,2);
		double x = getPathParam(currPathString,3);
		double y = getPathParam(currPathString,4);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			x2 -= pivotX;
			y2 -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			double x2New = x2 * Math.cos(rotateAngle) - y2 * Math.sin(rotateAngle);
			double y2New = x2 * Math.sin(rotateAngle) + y2 * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;
			x2New += pivotX;
			y2New += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);

			currPathString = "S " + x2New + " " + y2New + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			double x2New = x2 * Math.cos(rotateAngle) - y2 * Math.sin(rotateAngle);
			double y2New = x2 * Math.sin(rotateAngle) + y2 * Math.cos(rotateAngle);

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);

			currPathString = "s " + x2New + " " + y2New + " " + xNew + " " + yNew + " ";

			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartCurve(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x1 = getPathParam(currPathString,1);
		double y1 = getPathParam(currPathString,2);
		double x2 = getPathParam(currPathString,3);
		double y2 = getPathParam(currPathString,4);
		double x = getPathParam(currPathString,5);
		double y = getPathParam(currPathString,6);

		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			x1 -= pivotX;
			y1 -= pivotY;
			x2 -= pivotX;
			y2 -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			double x1New = x1 * Math.cos(rotateAngle) - y1 * Math.sin(rotateAngle);
			double y1New = x1 * Math.sin(rotateAngle) + y1 * Math.cos(rotateAngle);
			double x2New = x2 * Math.cos(rotateAngle) - y2 * Math.sin(rotateAngle);
			double y2New = x2 * Math.sin(rotateAngle) + y2 * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;
			x1New += pivotX;
			y1New += pivotY;
			x2New += pivotX;
			y2New += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);
			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);

			currPathString = "C " + x1New + " " + y1New + " " + x2New + " " + y2New + " " + xNew + " " + yNew + " ";

			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			double x1New = x1 * Math.cos(rotateAngle) - y1 * Math.sin(rotateAngle);
			double y1New = x1 * Math.sin(rotateAngle) + y1 * Math.cos(rotateAngle);
			double x2New = x2 * Math.cos(rotateAngle) - y2 * Math.sin(rotateAngle);
			double y2New = x2 * Math.sin(rotateAngle) + y2 * Math.cos(rotateAngle);

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);
			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);

			currPathString = "c " + x1New + " " + y1New + " " + x2New + " " + y2New + " " + xNew + " " + yNew + " ";

			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartVerLine(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x = 0;
		double y = getPathParam(currPathString,1);

		if (isAbs)
		{
			x = prevPathX;
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			prevPathX = x + pivotX;
			prevPathY = y + pivotY;
			xNew += pivotX;
			yNew += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "L " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			prevPathX += x + pivotX;
			prevPathY += y + pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "l " + xNew + " " + yNew + " ";
			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartHorLine(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = 0;

		if (isAbs)
		{
			y = prevPathY;
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			prevPathX = x + pivotX;
			prevPathY = y + pivotY;
			xNew += pivotX;
			yNew += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "L " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			prevPathX += x + pivotX;
			prevPathY += y + pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "l " + xNew + " " + yNew + " ";
			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartLine(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = getPathParam(currPathString,2);


		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "L " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "l " + xNew + " " + yNew + " ";
			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private String rotatePathPartMove(String currPathString,
			double rotateAngle, double pivotX, double pivotY, boolean isAbs)
	{
		double x = getPathParam(currPathString,1);
		double y = getPathParam(currPathString,2);


		if (isAbs)
		{
			prevPathX = x;
			prevPathY = y;
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "M " + xNew + " " + yNew + " ";
			lastPathX = xNew;
			lastPathY = yNew;
		}
		else
		{
			prevPathX += x;
			prevPathY += y;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			currPathString = "m " + xNew + " " + yNew + " ";
			lastPathX += xNew;
			lastPathY += yNew;
		}
		return currPathString;
	}

	private Element rotateRect(Element element, double rotateAngle,
			double pivotX, double pivotY)
	{
		String xString = element.getAttribute("x");
		String yString = element.getAttribute("y");
		String wString = element.getAttribute("width");
		String hString = element.getAttribute("height");
		String rxString = element.getAttribute("rx");
		String ryString = element.getAttribute("ry");

		double x = 0;
		double y = 0;
		double w = 0;
		double h = 0;
		double rx = 0;
		double ry = 0;

		if (xString.length() > 0)
			x = Double.valueOf(xString);
		if (yString.length() > 0)
			y = Double.valueOf(yString);
		if (wString.length() > 0)
			w = Double.valueOf(wString);
		if (hString.length() > 0)
			h = Double.valueOf(hString);
		if (rxString.length() > 0)
			rx = Double.valueOf(rxString);
		if (ryString.length() > 0)
			ry = Double.valueOf(ryString);

		double x1 = x;
		double y1 = y;
		double x2 = x;
		double y2 = y+h;
		double x3 = x + w;
		double y3 = y+h;
		double x4 = x + w;
		double y4 = y;

		if (rx==0 && ry==0)
		{
			//rect

			x1 -= pivotX;
			y1 -= pivotY;
			x2 -= pivotX;
			y2 -= pivotY;
			x3 -= pivotX;
			y3 -= pivotY;
			x4 -= pivotX;
			y4 -= pivotY;
			double x1New = x1 * Math.cos(rotateAngle) - y1 * Math.sin(rotateAngle);
			double y1New = x1 * Math.sin(rotateAngle) + y1 * Math.cos(rotateAngle);
			double x2New = x2 * Math.cos(rotateAngle) - y2 * Math.sin(rotateAngle);
			double y2New = x2 * Math.sin(rotateAngle) + y2 * Math.cos(rotateAngle);
			double x3New = x3 * Math.cos(rotateAngle) - y3 * Math.sin(rotateAngle);
			double y3New = x3 * Math.sin(rotateAngle) + y3 * Math.cos(rotateAngle);
			double x4New = x4 * Math.cos(rotateAngle) - y4 * Math.sin(rotateAngle);
			double y4New = x4 * Math.sin(rotateAngle) + y4 * Math.cos(rotateAngle);
			x1New += pivotX;
			y1New += pivotY;
			x2New += pivotX;
			y2New += pivotY;
			x3New += pivotX;
			y3New += pivotY;
			x4New += pivotX;
			y4New += pivotY;

			x1New = roundToDecimals(x1New, decimalsNum);
			y1New = roundToDecimals(y1New, decimalsNum);
			x2New = roundToDecimals(x2New, decimalsNum);
			y2New = roundToDecimals(y2New, decimalsNum);
			x3New = roundToDecimals(x3New, decimalsNum);
			y3New = roundToDecimals(y3New, decimalsNum);
			x4New = roundToDecimals(x4New, decimalsNum);
			y4New = roundToDecimals(y4New, decimalsNum);

			String pointsString = x1New + "," + y1New + " " + x2New + "," + y2New + " " + x3New + "," + y3New + " " + x4New + "," + y4New + " ";

			element.removeAttribute("x");
			element.removeAttribute("y");
			element.removeAttribute("width");
			element.removeAttribute("height");

			// this chunk simply renames the element from "rect" to "polygon" since there is no other way
			Document doc = element.getOwnerDocument();
			Element newElement = doc.createElement("polygon");
			NamedNodeMap attrs = element.getAttributes();
			for (int i=0; i<attrs.getLength(); i++) {
				Attr attr2 = (Attr)doc.importNode(attrs.item(i), true);
				newElement.getAttributes().setNamedItem(attr2);
			}
			while (element.hasChildNodes()) {
				newElement.appendChild(element.getFirstChild());
			}
			// Replace the old node with the new node
			element.getParentNode().replaceChild(newElement, element);
			newElement.setAttribute("points", pointsString);
			return newElement;
		}
		else // roundrect
		{

			y1 = roundToDecimals(y1, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			h = roundToDecimals(h, decimalsNum);
			rx = roundToDecimals(rx, decimalsNum);
			ry = roundToDecimals(ry, decimalsNum);
			w = roundToDecimals(w, decimalsNum);

			String pathString =  "M " + x + " " + (y1+ry) + 
					" v " + (h - 2 * ry) +
					" a " + rx + " " + ry + " 0 0 0 " + rx + " " + ry +
					" h " + (w - 2 * rx) +
					" a " + rx + " " + ry + " 0 0 0 " + rx + " " + (-ry) +
					" v " + (2 * ry - h) + 
					" a " + rx + " " + ry + " 0 0 0 " + (-rx) + " " + (-ry) +
					" h " + (2 * rx - w) +
					" a " + rx + " " + ry + " 0 0 0 " + (-rx) + " " + ry +
					" Z ";

			element.removeAttribute("x");
			element.removeAttribute("y");
			element.removeAttribute("height");
			element.removeAttribute("width");
			element.removeAttribute("rx");
			element.removeAttribute("ry");

			// this chunk simply renames the element from "rect" to "path" since there is no other way
			Document doc = element.getOwnerDocument();
			Element newElement = doc.createElement("path");
			NamedNodeMap attrs = element.getAttributes();
			for (int i=0; i<attrs.getLength(); i++) {
				Attr attr2 = (Attr)doc.importNode(attrs.item(i), true);
				newElement.getAttributes().setNamedItem(attr2);
			}
			while (element.hasChildNodes()) {
				newElement.appendChild(element.getFirstChild());
			}
			// Replace the old node with the new node
			element.getParentNode().replaceChild(newElement, element);
			newElement.setAttribute("d", pathString);
			newElement = this.transformShape(newElement);
			return newElement;
		}
	}

	private Element rotatePoly(Element element, double rotateAngle,
			double pivotX, double pivotY)
	{
		String pointsString = element.getAttribute("points");
		String newPointsString = "";
		double x = 0;
		double y = 0;
		String xString;
		String yString;

		//a loop that reads the coords
		int commaIndex = pointsString.indexOf(",");
		int spaceIndex = pointsString.indexOf(" ", commaIndex+1);
		if (spaceIndex==-1)
			spaceIndex = pointsString.length();

		while ((commaIndex!=-1) && (spaceIndex>commaIndex))
		{
			//read x
			xString = pointsString.substring(0, commaIndex);
			//read y
			yString = pointsString.substring(commaIndex+1, spaceIndex);

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);


			// the actual transform
			x -= pivotX;
			y -= pivotY;
			double xNew = x * Math.cos(rotateAngle) - y * Math.sin(rotateAngle);
			double yNew = x * Math.sin(rotateAngle) + y * Math.cos(rotateAngle);
			xNew += pivotX;
			yNew += pivotY;

			xNew = roundToDecimals(xNew, decimalsNum);
			yNew = roundToDecimals(yNew, decimalsNum);

			// add the new coords to the new string
			newPointsString += xNew + "," + yNew + " ";

			pointsString = pointsString.substring(spaceIndex, pointsString.length());

			commaIndex = pointsString.indexOf(",");
			spaceIndex = pointsString.indexOf(" ", commaIndex+1);
			if (spaceIndex==-1)
				spaceIndex = pointsString.length();
		}
		newPointsString = newPointsString.substring(0, (newPointsString.length()-1));
		element.setAttribute("points", newPointsString);
		return element;
	}

	private Element rotateLine(Element element, double rotateAngle, double pivotX, double pivotY)
	{
		String x1String = element.getAttribute("x1");
		String y1String = element.getAttribute("y1");
		String x2String = element.getAttribute("x2");
		String y2String = element.getAttribute("y2");

		double x1 = 0;
		double y1 = 0;
		double x2 = 0;
		double y2 = 0;

		if (x1String.length() > 0)
			x1 = Double.valueOf(x1String);
		if (y1String.length() > 0)
			y1 = Double.valueOf(y1String);
		if (x2String.length() > 0)
			x2 = Double.valueOf(x2String);
		if (y2String.length() > 0)
			y2 = Double.valueOf(y2String);

		x1 = x1 - pivotX;
		y1 = y1 - pivotY;
		x2 = x2 - pivotX;
		y2 = y2 - pivotY;
		double x1new = x1 * Math.cos(rotateAngle) - y1 * Math.sin(rotateAngle);
		double y1new = x1 * Math.sin(rotateAngle) + y1 * Math.cos(rotateAngle);

		double x2new = x2 * Math.cos(rotateAngle) - y2 * Math.sin(rotateAngle);
		double y2new = x2 * Math.sin(rotateAngle) + y2 * Math.cos(rotateAngle);

		x1new += pivotX;
		y1new += pivotY;
		x2new += pivotX;
		y2new += pivotY;

		x1new = roundToDecimals(x1new, decimalsNum);
		y1new = roundToDecimals(y1new, decimalsNum);
		x2new = roundToDecimals(x2new, decimalsNum);
		y2new = roundToDecimals(y2new, decimalsNum);

		element.setAttribute("x1", Double.toString(x1new));
		element.setAttribute("y1", Double.toString(y1new));
		element.setAttribute("x2", Double.toString(x2new));
		element.setAttribute("y2", Double.toString(y2new));		
		return element;
	}

	private Element scalePath(Element element, double sx, double sy)
	{

		String pathString = element.getAttribute("d");
		pathString = pathString.replaceAll("\n", "");
		pathString = pathString.replaceAll(" {2,}", " ");
		String newPathString = "";
		int nextPartStartIndex; 

		do
		{
			// remove leading space
			while (pathString.charAt(0)==' ')
				pathString=pathString.substring(1, pathString.length());

			nextPartStartIndex = this.nextPartIndex(pathString);
			String currPathString = pathString.substring(0, nextPartStartIndex);
			newPathString += parseScalePathPart(currPathString, sx, sy);
			pathString = pathString.substring(nextPartStartIndex, pathString.length());
		} while (pathString.length()>0);

		newPathString = newPathString.substring(0, (newPathString.length()-1));
		element.setAttribute("d", newPathString);
		return element;
	}

	private String parseScalePathPart(String currPathString, double sx,
			double sy)
	{
		char pathType = currPathString.charAt(0);
		String newPath="error"; // if it doesn't get changes, the path type isn't recognized, so it's an error
		switch (pathType)
		{
			case 'M' : 
				return newPath = this.scalePathPartMove(currPathString, sx, sy, true);
			case 'm' : 
				return newPath = this.scalePathPartMove(currPathString, sx, sy, false);
			case 'L' : 
				return newPath = this.scalePathPartLine(currPathString, sx, sy, true);
			case 'l' : 
				return newPath = this.scalePathPartLine(currPathString, sx, sy, false);
			case 'H' : 
				return newPath = this.scalePathPartHorLine(currPathString, sx, sy, true);
			case 'h' : 
				return newPath = this.scalePathPartHorLine(currPathString, sx, sy, false);
			case 'V' : 
				return newPath = this.scalePathPartVerLine(currPathString, sx, sy, true);
			case 'v' : 
				return newPath = this.scalePathPartVerLine(currPathString, sx, sy, false);
			case 'C' : 
				return newPath = this.scalePathPartCurve(currPathString, sx, sy, true);
			case 'c' : 
				return newPath = this.scalePathPartCurve(currPathString, sx, sy, false);
			case 'S' : 
				return newPath = this.scalePathPartSmoothCurve(currPathString, sx, sy, true);
			case 's' : 
				return newPath = this.scalePathPartSmoothCurve(currPathString, sx, sy, false);
			case 'Q' : 
				return newPath = this.scalePathPartQuad(currPathString, sx, sy, true);
			case 'q' : 
				return newPath = this.scalePathPartQuad(currPathString, sx, sy, false);
			case 'T' : 
				return newPath = this.scalePathPartSmoothQuad(currPathString, sx, sy, true);
			case 't' : 
				return newPath = this.scalePathPartSmoothQuad(currPathString, sx, sy, false);
			case 'A' : 
				return newPath = this.scalePathPartArc(currPathString, sx, sy, true);
			case 'a' : 
				return newPath = this.scalePathPartArc(currPathString, sx, sy, false);
			case 'Z' : 
			case 'z' : 
				return currPathString;
		}
		return newPath;
	}

	private String scalePathPartArc(String currPathString, double sx, double sy, boolean isAbs)
	{
		double rx = Math.abs(getPathParam(currPathString,1) * sx);
		double ry = Math.abs(getPathParam(currPathString,2)* sy);
		double xRot = getPathParam(currPathString,3);
		int largeArc = (int) getPathParam(currPathString,4);
		int sweep = (int) getPathParam(currPathString,5);

		// correcting sweep is scaling is negative
		if ((sx<0 && sy>0) || (sx>0 && sy<0))
		{
			if (sweep==1)
				sweep=0;
			else
				sweep=1;
		}

		double x = getPathParam(currPathString,6) * sx;
		double y = getPathParam(currPathString,7) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		rx = roundToDecimals(rx, decimalsNum);
		ry = roundToDecimals(ry, decimalsNum);
		xRot = roundToDecimals(xRot, decimalsNum);

		if (isAbs)
			currPathString = "A " + rx + " " + ry + " " + xRot + " " + largeArc + " " + sweep + " " + x + " " + y + " ";
		else
			currPathString = "a " + rx + " " + ry + " " + xRot + " " + largeArc + " " + sweep + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartSmoothQuad(String currPathString, double sx,
			double sy, boolean isAbs)
	{
		double x = getPathParam(currPathString,1) * sx;
		double y = getPathParam(currPathString,2) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		if (isAbs)
			currPathString = "T " + x + " " + y + " ";
		else
			currPathString = "t " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartQuad(String currPathString, double sx, double sy, boolean isAbs)
	{
		double x1 = getPathParam(currPathString,1) * sx;
		double y1 = getPathParam(currPathString,2) * sy;
		double x = getPathParam(currPathString,3) * sx;
		double y = getPathParam(currPathString,4) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);

		if (isAbs)
			currPathString = "Q " + x1 + " " + y1 + " " + x + " " + y + " ";
		else
			currPathString = "q " + x1 + " " + y1 + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartSmoothCurve(String currPathString, double sx,
			double sy, boolean isAbs)
	{
		double x2 = getPathParam(currPathString,1) * sx;
		double y2 = getPathParam(currPathString,2) * sy;
		double x = getPathParam(currPathString,3) * sx;
		double y = getPathParam(currPathString,4) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);

		if (isAbs)
			currPathString = "S " + x2 + " " + y2 + " " + x + " " + y + " ";
		else
			currPathString = "s " + x2 + " " + y2 + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartCurve(String currPathString, double sx, double sy, boolean isAbs)
	{
		double x1 = getPathParam(currPathString,1) * sx;
		double y1 = getPathParam(currPathString,2) * sy;
		double x2 = getPathParam(currPathString,3) * sx;
		double y2 = getPathParam(currPathString,4) * sy;
		double x = getPathParam(currPathString,5) * sx;
		double y = getPathParam(currPathString,6) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);

		if (isAbs)
			currPathString = "C " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x + " " + y + " ";
		else
			currPathString = "c " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartVerLine(String currPathString, double sx, double sy, boolean isAbs)
	{
		double y = getPathParam(currPathString,1) * sx;

		y = roundToDecimals(y, decimalsNum);

		if (isAbs)
			currPathString = "V " + y + " ";
		else
			currPathString = "v " + y + " ";
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartHorLine(String currPathString, double sx, double sy, boolean isAbs)
	{
		double x = getPathParam(currPathString,1) * sx;

		x = roundToDecimals(x, decimalsNum);

		if (isAbs)
			currPathString = "H " + x + " ";
		else
			currPathString = "h " + x + " ";
		lastPathX = x;
		return currPathString;
	}

	private String scalePathPartLine(String currPathString, double sx, double sy, boolean isAbs)
	{
		double x = getPathParam(currPathString,1) * sx;
		double y = getPathParam(currPathString,2) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		if (isAbs)
			currPathString = "L " + x + " " + y + " ";
		else
			currPathString = "l " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private String scalePathPartMove(String currPathString, double sx, double sy, boolean isAbs)
	{
		double x = getPathParam(currPathString,1) * sx;
		double y = getPathParam(currPathString,2) * sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		if (isAbs)
			currPathString = "M " + x + " " + y + " ";
		else
			currPathString = "m " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return currPathString;
	}

	private Element scalePoly(Element element, double sx, double sy)
	{
		String pointsString = element.getAttribute("points");
		String newPointsString = "";
		double x = 0;
		double y = 0;
		String xString;
		String yString;

		//a loop that reads the coords
		int commaIndex = pointsString.indexOf(",");
		int spaceIndex = pointsString.indexOf(" ", commaIndex+1);
		if (spaceIndex==-1)
			spaceIndex = pointsString.length();

		while ((commaIndex!=-1) && (spaceIndex>commaIndex))
		{
			//read x
			xString = pointsString.substring(0, commaIndex);
			//read y
			yString = pointsString.substring(commaIndex+1, spaceIndex);

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);

			x = x * sx;
			y = y * sy;

			x = roundToDecimals(x, decimalsNum);
			y = roundToDecimals(y, decimalsNum);

			// add the new coords to the new string
			newPointsString += x + "," + y + " ";

			pointsString = pointsString.substring(spaceIndex, pointsString.length());

			commaIndex = pointsString.indexOf(",");
			spaceIndex = pointsString.indexOf(" ", commaIndex+1);
			if (spaceIndex==-1)
				spaceIndex = pointsString.length();
		}
		newPointsString = newPointsString.substring(0, (newPointsString.length()-1));
		element.setAttribute("points", newPointsString);
		return element;
	}

	private Element scaleEllipse(Element element, double sx, double sy)
	{
		String cxString = element.getAttribute("cx");
		String cyString = element.getAttribute("cy");
		String rxString = element.getAttribute("rx");
		String ryString = element.getAttribute("ry");

		double cx = 0;
		double cy = 0;
		double rx = 0;
		double ry = 0;

		if (cxString.length() > 0)
			cx = Double.valueOf(cxString);
		if (cyString.length() > 0)
			cy = Double.valueOf(cyString);
		if (rxString.length() > 0)
			rx = Double.valueOf(rxString);
		if (ryString.length() > 0)
			ry = Double.valueOf(ryString);

		cx = cx * sx;
		cy = cy * sy;
		rx = rx * sx;
		ry = ry * sy;

		cx = roundToDecimals(cx, decimalsNum);
		cy = roundToDecimals(cy, decimalsNum);
		rx = roundToDecimals(rx, decimalsNum);
		ry = roundToDecimals(ry, decimalsNum);

		element.setAttribute("cx", Double.toString(cx));
		element.setAttribute("cy", Double.toString(cy));
		element.setAttribute("rx", Double.toString(rx));
		element.setAttribute("ry", Double.toString(ry));
		return element;
	}

	private Element scaleRect(Element element, double sx, double sy)
	{
		String xString = element.getAttribute("x");
		String yString = element.getAttribute("y");
		String wString = element.getAttribute("width");
		String hString = element.getAttribute("height");
		String rxString = element.getAttribute("rx");
		String ryString = element.getAttribute("ry");

		double x = 0;
		double y = 0;
		double w = 0;
		double h = 0;
		double rx = 0;
		double ry = 0;

		if (xString.length() > 0)
			x = Double.valueOf(xString);
		if (yString.length() > 0)
			y = Double.valueOf(yString);
		if (wString.length() > 0)
			w = Double.valueOf(wString);
		if (hString.length() > 0)
			h = Double.valueOf(hString);
		if (rxString.length() > 0)
			rx = Double.valueOf(rxString);
		if (ryString.length() > 0)
			ry = Double.valueOf(ryString);

		x=x*sx;
		y=y*sy;
		w=w*sx;
		h=h*sy;
		rx=rx*sx;
		ry=ry*sy;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		w = roundToDecimals(w, decimalsNum);
		h = roundToDecimals(h, decimalsNum);
		rx = roundToDecimals(rx, decimalsNum);
		ry = roundToDecimals(ry, decimalsNum);

		element.setAttribute("x", Double.toString(x));
		element.setAttribute("y", Double.toString(y));
		element.setAttribute("width", Double.toString(w));
		element.setAttribute("height", Double.toString(h));
		if(rx!=0)
			element.setAttribute("rx", Double.toString(rx));
		if(ry!=0)
			element.setAttribute("ry", Double.toString(ry));
		return element;
	}

	private Element scaleLine(Element element, double sx, double sy)
	{
		String x1String = element.getAttribute("x1");
		String y1String = element.getAttribute("y1");
		String x2String = element.getAttribute("x2");
		String y2String = element.getAttribute("y2");

		double x1 = 0;
		double y1 = 0;
		double x2 = 0;
		double y2 = 0;

		if (x1String.length() > 0)
			x1 = Double.valueOf(x1String);
		if (y1String.length() > 0)
			y1 = Double.valueOf(y1String);
		if (x2String.length() > 0)
			x2 = Double.valueOf(x2String);
		if (y2String.length() > 0)
			y2 = Double.valueOf(y2String);

		x1=x1*sx;
		y1=y1*sy;
		x2=x2*sx;
		y2=y2*sy;

		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);

		element.setAttribute("x1", Double.toString(x1));
		element.setAttribute("y1", Double.toString(y1));
		element.setAttribute("x2", Double.toString(x2));
		element.setAttribute("y2", Double.toString(y2));
		return element;
	}

	private Element translateLine(Element element, double tx , double ty)
	{

		String x1String = element.getAttribute("x1");
		String y1String = element.getAttribute("y1");
		String x2String = element.getAttribute("x2");
		String y2String = element.getAttribute("y2");

		double x1 = 0;
		double y1 = 0;
		double x2 = 0;
		double y2 = 0;

		if (x1String.length() > 0)
			x1 = Double.valueOf(x1String);
		if (y1String.length() > 0)
			y1 = Double.valueOf(y1String);
		if (x2String.length() > 0)
			x2 = Double.valueOf(x2String);
		if (y2String.length() > 0)
			y2 = Double.valueOf(y2String);

		x1+=tx;
		y1+=ty;
		x2+=tx;
		y2+=ty;

		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);

		element.setAttribute("x1", Double.toString(x1));
		element.setAttribute("y1", Double.toString(y1));
		element.setAttribute("x2", Double.toString(x2));
		element.setAttribute("y2", Double.toString(y2));
		return element;
	}

	private Element translateRect(Element element, double tx , double ty)
	{
		String xString = element.getAttribute("x");
		String yString = element.getAttribute("y");

		double x = 0;
		double y = 0;

		if (xString.length() > 0)
			x = Double.valueOf(xString);
		if (yString.length() > 0)
			y = Double.valueOf(yString);

		x+=tx;
		y+=ty;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		element.setAttribute("x", Double.toString(x));
		element.setAttribute("y", Double.toString(y));
		return element;
	}

	private Element translateEllipse(Element element, double tx , double ty)
	{

		String cxString = element.getAttribute("cx");
		String cyString = element.getAttribute("cy");

		double cx = 0;
		double cy = 0;

		if (cxString.length() > 0)
			cx = Double.valueOf(cxString);
		if (cyString.length() > 0)
			cy = Double.valueOf(cyString);

		cx+=tx;
		cy+=ty;

		cx = roundToDecimals(cx, decimalsNum);
		cy = roundToDecimals(cy, decimalsNum);

		element.setAttribute("cx", Double.toString(cx));
		element.setAttribute("cy", Double.toString(cy));
		return element;
	}

	private Element translatePoly(Element element, double tx , double ty)
	{

		String pointsString = element.getAttribute("points");
		String newPointsString = "";
		double x = 0;
		double y = 0;
		String xString;
		String yString;

		//a loop that reads the coords
		int commaIndex = pointsString.indexOf(",");
		int spaceIndex = pointsString.indexOf(" ", commaIndex+1);
		if (spaceIndex==-1)
			spaceIndex = pointsString.length();

		while ((commaIndex!=-1) && (spaceIndex>commaIndex))
		{
			//read x
			xString = pointsString.substring(0, commaIndex);
			//read y
			yString = pointsString.substring(commaIndex+1, spaceIndex);

			x = Double.valueOf(xString);
			y = Double.valueOf(yString);

			x+=tx;
			y+=ty;
			
			x = roundToDecimals(x, decimalsNum);
			y = roundToDecimals(y, decimalsNum);

			// add the new coords to the new string
			newPointsString += x + "," + y + " ";

			pointsString = pointsString.substring(spaceIndex, pointsString.length());

			commaIndex = pointsString.indexOf(",");
			spaceIndex = pointsString.indexOf(" ", commaIndex+1);
			if (spaceIndex==-1)
				spaceIndex = pointsString.length();
		}
		newPointsString = newPointsString.substring(0, (newPointsString.length()-1));
		element.setAttribute("points", newPointsString);
		return element;
	}

	private Element translatePath(Element element, double tx , double ty)
	{

		String pathString = element.getAttribute("d");
		pathString = pathString.replaceAll("\n", "");
		pathString = pathString.replaceAll(" {2,}", " ");
		String newPathString = "";
		int nextPartStartIndex; 

		do
		{
			// remove leading space
			while (pathString.charAt(0)==' ')
				pathString=pathString.substring(1, pathString.length());

			nextPartStartIndex = this.nextPartIndex(pathString);
			String currPathString = pathString.substring(0, nextPartStartIndex);
			newPathString += parseTranslatePathPart(currPathString, tx, ty);
			pathString = pathString.substring(nextPartStartIndex, pathString.length());
		} while (pathString.length()>0);

		newPathString = newPathString.substring(0, (newPathString.length()-1));
		element.setAttribute("d", newPathString);
		return element;
	}

	private String parseTranslatePathPart(String pathString, double tx, double ty)
	{
		char pathType = pathString.charAt(0);
		String newPath="error"; // if it doesn't get changes, the path type isn't recognized, so it's an error
		switch (pathType)
		{
			case 'M' : 
				return newPath = this.translateMoveAbs(pathString, tx, ty);
			case 'L' : 
				return newPath = this.translateLineAbs(pathString, tx, ty);
			case 'H' : 
				return newPath = this.translateHorLineAbs(pathString, tx, ty);
			case 'V' : 
				return newPath = this.translateVerLineAbs(pathString, tx, ty);
			case 'C' : 
				return newPath = this.translateCurveAbs(pathString, tx, ty);
			case 'S' : 
				return newPath = this.translateSmoothCurveAbs(pathString, tx, ty);
			case 'Q' : 
				return newPath = this.translateQuadAbs(pathString, tx, ty);
			case 'T' : 
				return newPath = this.translateSmoothQuadAbs(pathString, tx, ty);
			case 'A' : 
				return newPath = this.translateArcAbs(pathString, tx, ty);
			case 'm' : 
			case 'l' : 
			case 'h' : 
			case 'v' : 
			case 'c' : 
			case 's' : 
			case 'q' : 
			case 't' : 
			case 'a' : 
			case 'Z' : 
			case 'z' : 
				return pathString;
		}
		return newPath;
	}



	private String translateArcAbs(String pathString, double tx, double ty)
	{
		double rx = getPathParam(pathString,1);
		double ry = getPathParam(pathString,2);
		double xRot = getPathParam(pathString,3);
		int largeArc = (int) getPathParam(pathString,4);
		int sweep = (int) getPathParam(pathString,5);
		double x = getPathParam(pathString,6) + tx;
		double y = getPathParam(pathString,7) + ty;
		
		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		rx = roundToDecimals(rx, decimalsNum);
		ry = roundToDecimals(ry, decimalsNum);
		xRot = roundToDecimals(xRot, decimalsNum);

		pathString = "A " + rx + " " + ry + " " + xRot + " " + largeArc + " " + sweep + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	private String translateSmoothQuadAbs(String pathString, double tx, double ty)
	{
		double x = getPathParam(pathString,1) + tx;
		double y = getPathParam(pathString,2) + ty;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		pathString = "T " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	private String translateQuadAbs(String pathString, double tx, double ty)
	{
		double x1 = getPathParam(pathString,1) + tx;
		double y1 = getPathParam(pathString,2) + ty;
		double x = getPathParam(pathString,3) + tx;
		double y = getPathParam(pathString,4) + ty;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);

		pathString = "Q " + x1 + " " + y1 + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	private String translateSmoothCurveAbs(String pathString, double tx,
			double ty)
	{
		double x2 = getPathParam(pathString,1) + tx;
		double y2 = getPathParam(pathString,2) + ty;
		double x = getPathParam(pathString,3) + tx;
		double y = getPathParam(pathString,4) + ty;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);

		pathString = "S " + x2 + " " + y2 + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	private String translateCurveAbs(String pathString, double tx, double ty)
	{
		double x1 = getPathParam(pathString,1) + tx;
		double y1 = getPathParam(pathString,2) + ty;
		double x2 = getPathParam(pathString,3) + tx;
		double y2 = getPathParam(pathString,4) + ty;
		double x = getPathParam(pathString,5) + tx;
		double y = getPathParam(pathString,6) + ty;
		
		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);
		x1 = roundToDecimals(x1, decimalsNum);
		y1 = roundToDecimals(y1, decimalsNum);
		x2 = roundToDecimals(x2, decimalsNum);
		y2 = roundToDecimals(y2, decimalsNum);

		pathString = "C " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	private String translateVerLineAbs(String pathString, double tx, double ty)
	{
		double y = getPathParam(pathString,1) + tx;

		y = roundToDecimals(y, decimalsNum);

		pathString = "V " + y + " ";
		lastPathY = y;
		return pathString;
	}

	private String translateHorLineAbs(String pathString, double tx, double ty)
	{
		double x = getPathParam(pathString,1) + tx;

		x = roundToDecimals(x, decimalsNum);

		pathString = "H " + x + " ";
		lastPathX = x;
		return pathString;
	}

	private String translateLineAbs(String pathString, double tx, double ty)
	{
		double x = getPathParam(pathString,1) + tx;
		double y = getPathParam(pathString,2) + ty;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		pathString = "L " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	private String translateMoveAbs(String pathString, double tx, double ty)
	{
		double x = getPathParam(pathString,1) + tx;
		double y = getPathParam(pathString,2) + ty;

		x = roundToDecimals(x, decimalsNum);
		y = roundToDecimals(y, decimalsNum);

		pathString = "M " + x + " " + y + " ";
		lastPathX = x;
		lastPathY = y;
		return pathString;
	}

	// param # starting from 1
	private double getPathParam(String pathString, int index)
	{
		int currIndex=0;
		int currParamIndex = 0;
		if (pathString.charAt(1) != ' ')
		{
			pathString = pathString.substring(0, 1) + " " + pathString.substring(1, pathString.length());
		}
		
		while (currIndex<index)
		{
			currParamIndex = pathString.indexOf(' ', currParamIndex+1);
			currIndex++;
		}
		int endIndex = pathString.indexOf(' ', currParamIndex+1);
		if (endIndex==-1)
			endIndex = pathString.length();

		String paramString = pathString.substring(currParamIndex, endIndex);

		double param = Double.valueOf(paramString);

		return param;
	}
	private int nextPartIndex(String path)
	{
		int nextPartStartIndex=path.length();
		int currIndex = path.indexOf("M",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("m",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("L",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("l",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("H",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("h",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("V",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("v",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("C",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("c",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("S",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("s",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("Q",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("q",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("T",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("t",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("A",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("a",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("Z",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;
		currIndex = path.indexOf("z",1);
		if ((currIndex>0) && (currIndex<nextPartStartIndex))
			nextPartStartIndex = currIndex;

		return nextPartStartIndex;
	}
	
	public static double roundToDecimals(double d, int c) 
	{
		BigDecimal temp = new BigDecimal(Double.toString(d));
		temp = temp.setScale(c, RoundingMode.HALF_EVEN);
		return temp.doubleValue();
	}
}