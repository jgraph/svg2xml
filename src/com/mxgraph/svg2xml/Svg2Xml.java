/**
 * $Id: Svg2Xml.java,v 1.16 2013/04/25 07:37:35 mate Exp $
 * Copyright (c) 2013, JGraph
 */

package com.mxgraph.svg2xml;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.mxgraph.shape.mxStencilShape;
import com.mxgraph.svg2xml.XmlConfig.aspectType;

/**
 * Executes what is defined in Svg2XmlGui
 */
public class Svg2Xml
{
	// source SVG document that is parsed
	private Document srcSVGDoc;

	// config doc of the stencil group
	private Document groupConfigDoc;

	// config doc of the individual stencil that is currently parsed
	private Document stencilConfigDoc;

	// configuration of the stencil XML that is currently generated
	private XmlConfig destConfigDoc = null;

	//user identifier, mxGraph is default, but every user should come up with his own identifier, to avoid conflicts with mxGraph standard stencils and shapes
	private final String stencilUserMarker = "mxGraph";

	public Svg2Xml(Svg2XmlGui gui)
	{
		destConfigDoc = new XmlConfig(gui);
		// order of actions:
		//1. Config settings are given default values combined with the settings from the UI.
		//2. check if additional config files exist. Those are the group config and individual stencil config files in XML format. If they exist, they are combined and the config settings are altered accordingly.
		//3. the SVG is flattened. This consists of parsing <defs> blocks and explicitly put them where needed. The second part is flattening groups. Inherit explicit style to all elements that are part of a group. After style inheritance remove group and put back element as individual shapes.
		//4. The destination XML backbone is created.
		//5. The connections are parsed from the optional config files and they are added to the destination XML.
		//6. the default style is formed. (the style settings that are default if nothing is specified otherwise)
		//7. get the first element from the SVG.
		//8. determine its style
		//9. add the needed style changes to the XML
		//11. translate the element into XML format.
		//12. if the stencil has a background then add it to the XML doc in the background block. Else, add it to the foreground block.
		//13. here should begin the loop of translating all other shapes.
		//14. get the next element
		//15. determine its style
		//16. compare the new style with the old one, and add the differences to the XML
		//17. the new style becomes the old style
		//18. translate the SVG element into XML
		//19. determine if restore and or save should be used
		//20. add the new element to the XML
		//21. write the document to a file

		// TODO add SVG viewbox support, or manual setting of viewbox 

		boolean isLastInGroup = true;
		boolean isNewGroup = true;
		String groupXml = new String();
		ByteArrayOutputStream groupBaos = new ByteArrayOutputStream(); 

		// construct destConfigDoc based on default values, groupConfigDoc and stencilConfigDoc
		for (int i = 0; i < gui.sourceFiles.length; i++)
		{
			groupBaos = new ByteArrayOutputStream(); 
			isLastInGroup = false;
			isNewGroup = false;

			String shapeName = gui.sourceFiles[i].getName();
			shapeName = shapeName.substring(0, shapeName.lastIndexOf("."));
			int configCount = 0;

			// looking for a group config file
			String groupConfigString = null;

			String configNameString = gui.sourceFiles[i].getParent();
			configNameString += "_config.xml";
			File testFile = new File(configNameString);

			if (testFile.exists())
			{
				groupConfigString = readFile(configNameString);
				configCount++;
			}

			testFile = null;

			// looking for a stencil config file
			String stencilConfigString = null;

			configNameString = gui.sourceFiles[i].getAbsolutePath();
			int pointIndex = configNameString.lastIndexOf('.');
			configNameString = configNameString.substring(0, pointIndex) + "_config.xml";

			testFile = new File(configNameString);

			if (testFile.exists())
			{
				stencilConfigString = readFile(configNameString);
				configCount++;
			}

			testFile = null;

			// now we have potentially both config files in String format
			System.out.println("parsing " + shapeName + " using " + configCount + " configs");

			ArrayList<Connection> groupConnection = null;

			if (groupConfigString != null)
			{
				groupConfigDoc = parseXml(groupConfigString);

				if (groupConfigDoc.getElementsByTagName("shape").getLength() > 0)
				{
					Node groupConfigRoot = groupConfigDoc.getElementsByTagName("shape").item(0);

					Element groupConfigRootElement = (Element) groupConfigRoot;

					if (groupConfigRootElement.getAttribute("background").equals("1"))
					{
						destConfigDoc.setBackground(true);
					}

					String aspectRatio = groupConfigRootElement.getAttribute("aspect");

					if (aspectRatio.toLowerCase().equals("fixed"))
					{
						destConfigDoc.setAspect(aspectType.FIXED);
					}
					else
					{
						destConfigDoc.setAspect(aspectType.VARIABLE);
					}

					//TODO implement strokewidth reading
					//					strokeWidth = groupConfigRootElement.getAttribute("strokewidth");
					groupConnection = getConstraintsFromXml(groupConfigString);
				}
			}

			ArrayList<Connection> stencilConnection = null;

			if (stencilConfigString != null)
			{
				stencilConfigDoc = parseXml(stencilConfigString);

				if (stencilConfigDoc != null && stencilConfigDoc.getElementsByTagName("shape").getLength() > 0)
				{
					Node stencilConfigRoot = stencilConfigDoc.getElementsByTagName("shape").item(0);
					Element stencilConfigRootElement = (Element) stencilConfigRoot;

					if (stencilConfigRootElement.getAttribute("background").equals("1"))
					{
						destConfigDoc.setBackground(true);
					}
					else if (stencilConfigRootElement.getAttribute("background").equals("0"))
					{
						destConfigDoc.setBackground(false);
					}

					String aspectRatio = stencilConfigRootElement.getAttribute("aspect");

					if (aspectRatio.toLowerCase().equals("fixed"))
					{
						destConfigDoc.setAspect(aspectType.FIXED);
					}
					else if (aspectRatio.toLowerCase().equals("variable"))
					{
						destConfigDoc.setAspect(aspectType.VARIABLE);
					}

					//TODO implement strokewidth reading
					//					strokeWidth = groupConfigRootElement.getAttribute("strokewidth");

					stencilConnection = getConstraintsFromXml(stencilConfigString);
				}
			}

			// NOTE: only the first connection block is used in the shape's local config
			Connection currConnection = null;
			Connection finalConnection = null;

			if (stencilConnection != null && !stencilConnection.isEmpty())
			{
				currConnection = stencilConnection.get(0);
				// determine the correct connections
				finalConnection = stencilConnection.get(0);
			}

			if (currConnection != null && currConnection.getId() != null && !currConnection.getId().equals(""))
			{
				String id = currConnection.getId();
				boolean foundConfig = false;
				int j = 0;

				while (!foundConfig && j < groupConnection.size())
				{
					String name = groupConnection.get(j).getName();

					if (name != null && name.equals(id))
					{
						finalConnection = groupConnection.get(j);
						foundConfig = true;
					}

					j++;
				}

				if (!foundConfig)
				{
					System.out.println("Didn't find a config XML with the reference \""
							+ currConnection.getId()
							+ "\" for the shape \"" + shapeName + "\".");
				}
				else
				{
					// if the local config has defined connections and has a reference too, the local connections are added to the group config for this shape
					for (int k = 0; k < currConnection.getConstraintNum(); k++)
					{
						finalConnection.getConstraints().add(currConnection.getConstraints().get(k));
					}
				}
			}

			destConfigDoc.setConnection(finalConnection);

			//3. the SVG needs to be flattened. This consists of parsing <defs> blocks and explicitly put them where needed. The second part is flattening groups. Inherit explicit style to all elements that are part of a group. After style inheritance remove group and put back element as individual shapes.
			//TODO probably done more elegantly via DOM
			String srcXmlString;

			srcXmlString = readFile(gui.sourceFiles[i].getAbsolutePath());
			int doctypeIndex = srcXmlString.indexOf("<!DOCTYPE");

			if (doctypeIndex>-1)
			{
				// we have the <!DOCTYPE entry>
				int doctypeEndIndex = srcXmlString.indexOf(">", doctypeIndex);
				String tempXml = srcXmlString.substring(0, doctypeIndex) + srcXmlString.substring(doctypeEndIndex+1, srcXmlString.length());
				srcXmlString = tempXml;
			}

			srcSVGDoc = parseXml(srcXmlString);
			srcSVGDoc = flattenSvg(srcSVGDoc);
			
			//TODO remove connection points
			Connection svgConnects = getConnections(srcSVGDoc);
			srcSVGDoc = removeConnections(srcSVGDoc);

			//DEBUG printing source SVG after flattening
//						System.out.println("************************************************");
//						System.out.println("Document after flattening:");
//						Svg2Xml.printDocument(srcSVGDoc, System.out);

			mxStencilShape newShape = new mxStencilShape(srcSVGDoc);
			Rectangle2D bounds = newShape.getBoundingBox();

			//recalculate connections to relative coords
			ArrayList<Constraint> constraints = svgConnects.getConstraints();
			
			for (int j=0; j < constraints.size(); j++)
			{
				Constraint currConstraint = constraints.get(j);
				
				double x = currConstraint.getX() - bounds.getMinX();
				x = Math.round(x * 100.0 / bounds.getWidth()) / 100.0;
				currConstraint.setX(x);

				double y = currConstraint.getY() - bounds.getMinY();
				y = Math.round(y * 100.0 / bounds.getHeight()) / 100.0;
				currConstraint.setY(y);
			}
			
			double stencilBoundsMinX = 0;
			double stencilBoundsMinY = 0;
			double stencilBoundsMaxX = 5;
			double stencilBoundsMaxY = 5;

			if (destConfigDoc.isCalculateBorder() && bounds != null)
			{
				stencilBoundsMinX = bounds.getMinX();
				stencilBoundsMinY = bounds.getMinY();
				stencilBoundsMaxX = bounds.getMaxX();
				stencilBoundsMaxY = bounds.getMaxY();
			}

			double stencilBoundsX = (stencilBoundsMaxX - stencilBoundsMinX);
			double stencilBoundsY = (stencilBoundsMaxY - stencilBoundsMinY);
			stencilBoundsX =  Shape2Xml.roundToDecimals(stencilBoundsX, destConfigDoc.getDecimalsToRound());
			stencilBoundsY =  Shape2Xml.roundToDecimals(stencilBoundsY, destConfigDoc.getDecimalsToRound());

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

			destConfigDoc.setStencilBoundsX(stencilBoundsX);
			destConfigDoc.setStencilBoundsY(stencilBoundsY);
			destConfigDoc.setStencilBoundsMinX(stencilBoundsMinX);
			destConfigDoc.setStencilBoundsMinY(stencilBoundsMinY);

			//4. The destination XML backbone is created.
			//5. The connections are parsed from the optional config files and they are added to the destination XML.
			try
			{
				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuild = docBuilderFactory.newDocumentBuilder();

				Document destDoc = docBuild.newDocument();
				createBackbone(destDoc, destConfigDoc, shapeName, srcSVGDoc, constraints);

				//6. the default style is formed. (the style settings that are default if nothing is specified otherwise)
				//7. get the first element from the SVG.
				Node srcRoot = srcSVGDoc.getElementsByTagName("svg").item(0);
				Element currElement = getFirstChildElement(srcRoot);

				//8. determine its style
				XmlStyle defStyle = new XmlStyle();
				XmlStyle currStyle = getStyle(currElement);
				Map<String, String> styleDiff = getStyleDiff(defStyle, currStyle);

				//9. add the needed style changes to the XML
				if (currElement.getNodeName().equals("text") && destConfigDoc.isBackground())
				{
					appendStyle(destDoc.getElementsByTagName("background").item(0), styleDiff, destConfigDoc, currElement);
				}
				else
				{
					appendStyle(destDoc.getElementsByTagName("foreground").item(0), styleDiff, destConfigDoc, currElement);
				}

				//11. translate the element into XML format.
				//12. if the stencil has a background then add it to the XML doc in the background block. Else, add it to the foreground block.
				if (destConfigDoc.isBackground())
				{
					if (!currElement.getNodeName().equals("text"))
					{
						Node newChild = Shape2Xml.parse(currElement, destDoc, destConfigDoc);

						if (newChild != null)
						{
							destDoc.getElementsByTagName("background").item(0).appendChild(newChild);
							String str = getStrokeString(currStyle);

							if (str != null)
							{
								destDoc.getElementsByTagName("foreground").item(0).appendChild(destDoc.createElement(str));
							}
						}
					}
					else
					{
						//text node
						Shape2Xml.parseText(currElement, destDoc, destConfigDoc, true);
					}
				}
				else
				{
					if (!currElement.getNodeName().equals("text"))
					{
						Element el = Shape2Xml.parse(currElement, destDoc, destConfigDoc);
						if (el != null)
						{
							destDoc.getElementsByTagName("foreground").item(0).appendChild(el);
							String str = getStrokeString(currStyle);

							if (str != null)
							{
								destDoc.getElementsByTagName("foreground").item(0).appendChild(destDoc.createElement(str));
							}
						}
					}
					else
					{
						//text node
						Shape2Xml.parseText(currElement, destDoc, destConfigDoc, false);
					}
				}

				//13. here should begin the loop of translating all other shapes.
				//14. get the next element
				Element nextElement = getNextSiblingElement(currElement);

				while (nextElement != null)
				{
					//15. determine its style
					//17. the new style becomes the old style
					XmlStyle oldStyle = currStyle;
					currStyle = getStyle(nextElement);

					if (isRestoreNeeded(oldStyle, currStyle))
					{
						Node bg = destDoc.getElementsByTagName("background").item(0); 
						Node fg = destDoc.getElementsByTagName("foreground").item(0);

						if (bg != null)
						{
							bg.insertBefore(destDoc.createElement("save"), bg.getFirstChild());
						}
						else
						{
							fg.insertBefore(destDoc.createElement("save"), fg.getFirstChild());
						}

						fg.appendChild(destDoc.createElement("restore"));

						//NOTE next two lines are a workaround for a shadow bug, remove them once the bug is fixed
						fg.appendChild(destDoc.createElement("rect"));
						fg.appendChild(destDoc.createElement("stroke"));

						styleDiff = getStyleDiff(defStyle, currStyle);
					}
					else
					{
						//16. compare the new style with the old one, and add the differences to the XML
						styleDiff = getStyleDiff(oldStyle, currStyle);
					}

					appendStyle(destDoc.getElementsByTagName("foreground").item(0), styleDiff, destConfigDoc, nextElement);

					//18. translate the SVG element into XML
					//20. add the new element to the XML
					if (!nextElement.getNodeName().equals("text"))
					{
						Node newChild = Shape2Xml.parse(nextElement, destDoc, destConfigDoc);

						if (newChild != null)
						{
							destDoc.getElementsByTagName("foreground").item(0).appendChild(newChild);
							String str = getStrokeString(currStyle);

							if (str != null)
							{
								destDoc.getElementsByTagName("foreground").item(0).appendChild(destDoc.createElement(str));
							}
						}
					}
					else
					{
						//text node
						Shape2Xml.parseText(nextElement, destDoc, destConfigDoc, false);
					}

					Element secondElement = getNextSiblingElement(nextElement);
					nextElement = secondElement;
				}

				//21. write the document to a file
				//check if a new group is started
				if (i == 0)
				{
					isNewGroup = true;
				}
				else
				{
					String currParent = gui.sourceFiles[i].getParent();
					String oldParent = gui.sourceFiles[i-1].getParent();

					if(currParent.equals(oldParent))
					{
						isNewGroup = false;
					}
					else
					{
						isNewGroup = true;
					}
				}

				//check if this is the last file in the group
				if (i + 1 == gui.sourceFiles.length)
				{
					isLastInGroup = true;
				}
				else
				{
					String currParent = gui.sourceFiles[i].getParent();
					String nextParent = gui.sourceFiles[i+1].getParent();

					if(currParent.equals(nextParent))
					{
						isLastInGroup = false;
					}
					else
					{
						isLastInGroup = true;
					}
				}

				// here we need some group naming check
				String currentPath = gui.sourceFiles[i].getAbsolutePath();
				currentPath = currentPath.substring(2, currentPath.lastIndexOf("."));

				if (isNewGroup)
				{
					// if new group then we save the old file and open a new one
					String groupName = stencilUserMarker;
					File currFile = new File(gui.sourceFiles[i].getAbsolutePath());
					ArrayList <String> folders = new ArrayList <String>();

					while (!currFile.getParentFile().getName().equals("svgroot") && currFile.getParent().length() > 4)
					{
						currFile = currFile.getParentFile();
						folders.add(0, currFile.getName());
					}

					for (int j = 0; j < folders.size(); j++)
					{
						groupName += "." + folders.get(j);
					}

					groupXml = "<shapes name=\"" + groupName + "\">" + System.getProperty("line.separator");
					String tmp = Svg2Xml.printDocumentString(destDoc, groupBaos);
					tmp = tmp.replaceAll("\\.0\"", "\"");
					groupXml += tmp;
				}
				else
				{
					// if not a new group then we just add the xml to the group xml
					groupXml += Svg2Xml.printDocumentString(destDoc, groupBaos);
				}

				// save the xml
				if(isLastInGroup)
				{
					groupXml += "</shapes>";

					groupXml = groupXml.replaceAll("\\.+0\"", "\"");

					try
					{
						String currentDestPath = gui.destPath.getAbsolutePath();
						currentDestPath += gui.sourceFiles[i].getParent().substring(2, gui.sourceFiles[i].getParent().length()) + ".xml";
						currentDestPath = currentDestPath.toLowerCase();
						currentDestPath = currentDestPath.replaceAll("\\s", "_");
						File myDestFile = new File(currentDestPath);
						File myDestRoot = new File(myDestFile.getParent());
						myDestRoot.mkdirs();
						FileWriter fileWriter = new FileWriter(myDestFile);
						BufferedWriter writer = new BufferedWriter(fileWriter);
						writer.write(groupXml);
						writer.close();

					} 
					catch(Exception ex) 
					{
						ex.printStackTrace();
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

	}

	/**
	 * Checks if a shape is filled and/or stroked
	 * @param currStyle
	 * @param defStyle
	 * @return <b>"stroke"</b> or <b>"fillstroke"</b>, eventually null for none
	 */
	static String getStrokeString(XmlStyle currStyle)
	{
		String fc = currStyle.getFillColor();
		String sc = currStyle.getStrokeColor();
		
		if (!fc.equals("none") && !sc.equals("none"))
		{
			return "fillstroke";
		}
		else if (!fc.equals("none") && sc.equals("none"))
		{
			return "fill";
		}
		else if (fc.equals("none") && !sc.equals("none"))
		{
			return "stroke";
		}
		else
		{
			return null;
		}
	}

	/**
	 * @param node reference node
	 * @return next sibling of <b>node</b> that is <b>Node.ELEMENT_NODE</b>
	 */
	private Element getNextSiblingElement(Element node)
	{
		Node nextSibling = node.getNextSibling();

		while(nextSibling != null)
		{
			if(nextSibling.getNodeType() == Node.ELEMENT_NODE)
			{
				return (Element) nextSibling;
			}

			nextSibling = nextSibling.getNextSibling();
		}

		return null;
	}

	/**
	 * Adds style changes to the stencil XML, based on <b>styleDiff</b>
	 * @param node stencil XML node which last chilld will the style be 
	 * @param styleDiff needed style changes
	 */
	public static void appendStyle(Node node, Map<String, String> styleDiff, XmlConfig configDoc, Element element)
	{
		Document doc = node.getOwnerDocument();
		String elementName = element.getNodeName(); 

		if (styleDiff.containsKey("strokecolor") && !styleDiff.get("strokecolor").equals(""))
		{
			Element el = null;

			if (elementName.equals("text") || elementName.equals("tspan") )
			{
				if(!styleDiff.containsKey("fillcolor") || styleDiff.get("fillcolor").equals(""))
				{
					el = doc.createElement("fontcolor");
					el.setAttribute("color", styleDiff.get("strokecolor"));
					node.appendChild(el);
				}
			}
			else if(!styleDiff.get("strokecolor").equals("none"))
			{
				el = doc.createElement("strokecolor");
				el.setAttribute("color", styleDiff.get("strokecolor"));
				node.appendChild(el);
			}
		}

		if (styleDiff.containsKey("fillcolor") && !styleDiff.get("fillcolor").equals(""))
		{
			Element el = null;

			if (elementName.equals("text") || elementName.equals("tspan") )
			{
				el = doc.createElement("fontcolor");
				el.setAttribute("color", styleDiff.get("fillcolor"));
				node.appendChild(el);
			}
			else  if(!styleDiff.get("fillcolor").equals("none"))
			{
				el = doc.createElement("fillcolor");
				el.setAttribute("color", styleDiff.get("fillcolor"));
				node.appendChild(el);
			}

		}

		if (styleDiff.containsKey("strokewidth"))
		{
			Element el = doc.createElement("strokewidth");
			el.setAttribute("width", styleDiff.get("strokewidth"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("linejoin"))
		{
			Element el = doc.createElement("linejoin");
			el.setAttribute("join", styleDiff.get("linejoin"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("linecap"))
		{
			Element el = doc.createElement("linecap");
			el.setAttribute("cap", styleDiff.get("linecap"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("miterlimit"))
		{
			Element el = doc.createElement("miterlimit");
			el.setAttribute("limit", styleDiff.get("miterlimit"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("dashpattern"))
		{
			Element el = doc.createElement("dashpattern");
			String pattern = styleDiff.get("dashpattern");
			pattern = pattern.replaceAll(",", " ");
			el.setAttribute("pattern", pattern);
			node.appendChild(el);
		}

		if (styleDiff.containsKey("dashed"))
		{
			Element el = doc.createElement("dashed");
			if (styleDiff.get("dashed").equals("true"))
			{
				el.setAttribute("dashed", "1");
			}
			else
			{
				el.setAttribute("dashed", "0");
			}
			
			node.appendChild(el);
		}

		if (styleDiff.containsKey("alpha"))
		{
			Element el = doc.createElement("alpha");
			el.setAttribute("alpha", styleDiff.get("alpha"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("strokealpha"))
		{
			Element el = doc.createElement("strokealpha");
			el.setAttribute("alpha", styleDiff.get("strokealpha"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("fillalpha"))
		{
			Element el = doc.createElement("fillalpha");
			el.setAttribute("alpha", styleDiff.get("fillalpha"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("fontcolor"))
		{
			Element el = doc.createElement("fontcolor");
			el.setAttribute("color", styleDiff.get("fontcolor"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("fontsize"))
		{
			Element el = doc.createElement("fontsize");
			String fontSizeStr = styleDiff.get("fontsize");

			if (!fontSizeStr.equals(""))
			{
				fontSizeStr = removeUnits(fontSizeStr);
				double fs = Double.parseDouble(fontSizeStr) * configDoc.getRelativeScalingRatio();
				el.setAttribute("size", Double.toString(fs));
				node.appendChild(el);
			}
		}

		if (styleDiff.containsKey("fontfamily"))
		{
			Element el = doc.createElement("fontfamily");
			el.setAttribute("family", styleDiff.get("fontfamily"));
			node.appendChild(el);
		}

		if (styleDiff.containsKey("bold") || styleDiff.containsKey("underline") || styleDiff.containsKey("italic"))
		{
			Integer style = 0;

			if (styleDiff.containsKey("bold"))
			{
				if(styleDiff.get("bold").equals("true"))
				{
					style = style + 1;
				}
			}

			if (styleDiff.containsKey("italic"))
			{
				if(styleDiff.get("italic").equals("true"))
				{
					style = style + 2;
				}
			}

			if (styleDiff.containsKey("underline"))
			{
				if(styleDiff.get("underline").equals("true"))
				{
					style = style + 4;
				}
			}

			Element el = doc.createElement("fontstyle");
			el.setAttribute("style", style.toString());
			node.appendChild(el);
		}
	}

	/**
	 * Calculates the style difference between styles for two shapes. For use between the current shape and the previous one.
	 * NOTE: don't swap the two parameters, you'll get a different result.
	 * @param prevStyle previous style
	 * @param currStyle current style
	 * @return the changes that <b>currStyle</b> introduced, compared to <b>prevStyle</b>
	 */
	public static Map<String, String> getStyleDiff(XmlStyle prevStyle, XmlStyle currStyle)
	{
		Map<String, String> styleDiff = new Hashtable<String, String>();

		if (currStyle.getStrokeColor() != null && !prevStyle.getStrokeColor().equals(currStyle.getStrokeColor()))
		{
			styleDiff.put("strokecolor", currStyle.getStrokeColor());
		}

		if (currStyle.getFillColor() != null && !prevStyle.getFillColor().equals(currStyle.getFillColor()))
		{
			styleDiff.put("fillcolor", currStyle.getFillColor());
		}

		if (currStyle.getStrokeWidth() != null && !prevStyle.getStrokeWidth().equals(currStyle.getStrokeWidth()))
		{
			styleDiff.put("strokewidth", currStyle.getStrokeWidth());
		}

		if (currStyle.getLineJoin() != null && !prevStyle.getLineJoin().equals(currStyle.getLineJoin()))
		{
			styleDiff.put("linejoin", currStyle.getLineJoin());
		}

		if (currStyle.getLineCap() != null && !prevStyle.getLineCap().equals(currStyle.getLineCap()))
		{
			styleDiff.put("linecap", currStyle.getLineCap());
		}

		if (currStyle.getMiterLimit() != null && !prevStyle.getMiterLimit().equals(currStyle.getMiterLimit()))
		{
			styleDiff.put("miterlimit", currStyle.getMiterLimit());
		}

		if (currStyle.getDashPattern() != null && !prevStyle.getDashPattern().equals(currStyle.getDashPattern()))
		{
			styleDiff.put("dashpattern", currStyle.getDashPattern());
		}

		if (currStyle.isDashed() != null && !prevStyle.isDashed().equals(currStyle.isDashed()))
		{
			styleDiff.put("dashed", currStyle.isDashed());
		}

		if (currStyle.getAlpha() != null && !prevStyle.getAlpha().equals(currStyle.getAlpha()))
		{
			styleDiff.put("alpha", currStyle.getAlpha());
		}

		if (currStyle.getStrokeAlpha() != null && !prevStyle.getStrokeAlpha().equals(currStyle.getStrokeAlpha()))
		{
			styleDiff.put("strokealpha", currStyle.getStrokeAlpha());
		}

		if (currStyle.getFillAlpha() != null && !prevStyle.getFillAlpha().equals(currStyle.getFillAlpha()))
		{
			styleDiff.put("fillalpha", currStyle.getFillAlpha());
		}

		if (currStyle.getFontColor() != null && !prevStyle.getFontColor().equals(currStyle.getFontColor()))
		{
			styleDiff.put("fontcolor", currStyle.getFontColor());
		}

		if (currStyle.isFontStyleBold() != null && !prevStyle.isFontStyleBold().equals(currStyle.isFontStyleBold()))
		{
			styleDiff.put("bold", currStyle.isFontStyleBold());
		}

		if (currStyle.isFontStyleItalic() != null && !prevStyle.isFontStyleItalic().equals(currStyle.isFontStyleItalic()))
		{
			styleDiff.put("italic", currStyle.isFontStyleItalic());
		}

		if (currStyle.isFontStyleUnderline() != null && !prevStyle.isFontStyleUnderline().equals(currStyle.isFontStyleUnderline()))
		{
			styleDiff.put("underline", currStyle.isFontStyleUnderline());
		}

		if (currStyle.getFontSize() != null && !prevStyle.getFontSize().equals(currStyle.getFontSize()))
		{
			styleDiff.put("fontsize", currStyle.getFontSize());
		}

		if (currStyle.getFontFamily() != null && !prevStyle.getFontFamily().equals(currStyle.getFontFamily()))
		{
			styleDiff.put("fontfamily", currStyle.getFontFamily());
		}

		if (currStyle.getAlign() != null && !prevStyle.getAlign().equals(currStyle.getAlign()))
		{
			styleDiff.put("align", currStyle.getAlign());
		}

		return styleDiff;
	}

	/**
	 * Calculates the style difference between styles for two shapes. For use between the current shape and the previous one.
	 * NOTE: don't swap the two parameters, you'll get a different result.
	 * @param prevStyle previous style
	 * @param currStyle current style
	 * @return the changes that <b>currStyle</b> introduced, compared to <b>prevStyle</b>
	 */
	private boolean isRestoreNeeded(XmlStyle prevStyle, XmlStyle currStyle)
	{
		if ((currStyle.getStrokeColor().equals("") && !prevStyle.getStrokeColor().equals("")) ||
				(currStyle.getFillColor().equals("") && !prevStyle.getFillColor().equals("")) ||
				(currStyle.getStrokeWidth().equals("") && !prevStyle.getStrokeWidth().equals("")) ||
				(currStyle.getLineJoin().equals("") && !prevStyle.getLineJoin().equals("")) ||
				(currStyle.getLineCap().equals("") && !prevStyle.getLineCap().equals("")) ||
				(currStyle.getMiterLimit().equals("") && !prevStyle.getMiterLimit().equals("")) ||
				(currStyle.getDashPattern().equals("") && !prevStyle.getDashPattern().equals("")) ||
				(currStyle.isDashed().equals("") && !prevStyle.isDashed().equals("")) ||
				(currStyle.getAlpha().equals("") && !prevStyle.getAlpha().equals("")) ||
				(currStyle.getStrokeAlpha().equals("") && !prevStyle.getStrokeAlpha().equals("")) ||
				(currStyle.getFillAlpha().equals("") && !prevStyle.getFillAlpha().equals("")) ||
				(currStyle.getFontColor().equals("") && !prevStyle.getFontColor().equals("")) ||
				(currStyle.isFontStyleBold().equals("") && !prevStyle.isFontStyleBold().equals("")) ||
				(currStyle.isFontStyleItalic().equals("") && !prevStyle.isFontStyleItalic().equals("")) ||
				(currStyle.isFontStyleUnderline().equals("") && !prevStyle.isFontStyleUnderline().equals("")) ||
				(currStyle.getFontSize().equals("") && !prevStyle.getFontSize().equals("")) ||
				(currStyle.getFontFamily().equals("") && !prevStyle.getFontFamily().equals("")))
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	/**
	 * @param node
	 * @return a style class that contains a complete SVG style for the node
	 */
	public static XmlStyle getStyle(Node node)
	{
		NamedNodeMap attributes = node.getAttributes();

		XmlStyle style = new XmlStyle();

		if (attributes != null)
		{
			for (int i = 0; i < attributes.getLength(); i++)
			{
				Node currAtt = attributes.item(i);

				String currName = currAtt.getNodeName();
				String nv = currAtt.getNodeValue().replaceAll(";", "");

				if (currName.equals("stroke"))
				{
					style.setStrokeColor(nv);
				}
				else if (currName.equals("fill"))
				{
					style.setFillColor(nv);
				}
				else if (currName.equals("stroke-width"))
				{
					style.setStrokeWidth(nv);
				}
				else if (currName.equals("stroke-linejoin"))
				{
					style.setLineJoin(nv);
				}
				else if (currName.equals("stroke-linecap"))
				{
					style.setLineCap(nv);
				}
				else if (currName.equals("stroke-miterlimit"))
				{
					style.setMiterLimit(nv);
				}
				else if (currName.equals("stroke-dasharray"))
				{
					if (currAtt.getNodeValue() != null && !currAtt.getNodeValue().equals(""))
					{
						style.setDashPattern(nv);
						style.setDashed("true");
					}
					else
					{
						style.setDashed("false");
					}
				}
				else if (currName.equals("opacity"))
				{
					style.setAlpha(nv);
				}
				else if (currName.equals("stroke-opacity"))
				{
					style.setStrokeAlpha(nv);
				}
				else if (currName.equals("fill-opacity"))
				{
					style.setFillAlpha(nv);
				}
				else if (node.getNodeName().equals("text") && currName.equals("stroke"))
				{
					style.setFillColor(nv);
				}
				else if (node.getNodeName().equals("text") && currName.equals("fill"))
				{
					style.setFillColor(nv);
				}
				else if (currName.equals("font-weight"))
				{
					if (currAtt.getNodeValue().equals("bold"))
					{
						style.setFontStyleBold("true");
					}
					else if (currAtt.getNodeValue().equals("normal"))
					{
						style.setFontStyleBold("false");
					}
				}
				else if (currName.equals("font-style"))
				{
					if (currAtt.getNodeValue().equals("italic"))
					{
						style.setFontStyleItalic("true");
					}
					else if (currAtt.getNodeValue().equals("normal"))
					{
						style.setFontStyleItalic("false");
					}
				}
				else if (currName.equals("text-decoration"))
				{
					if (currAtt.getNodeValue().equals("underline"))
					{
						style.setFontStyleUnderline("true");
					}
					else if (currAtt.getNodeValue().equals("normal"))
					{
						style.setFontStyleUnderline("false");
					}
				}
				else if (currName.equals("font-size"))
				{
					String fontSizeString = nv;
					fontSizeString = removeUnits(fontSizeString);
					style.setFontSize(fontSizeString);
				}
				else if (currName.equals("font-family"))
				{
					style.setFontFamily(nv);
				}
				else if (currName.equals("text-anchor"))
				{
					if(currAtt.getNodeValue().equals("start"))
					{
						style.setAlign("left");
					}
					else if (currAtt.getNodeValue().equals("middle"))
					{
						style.setAlign("center");
					}
					else if (currAtt.getNodeValue().equals("end"))
					{
						style.setAlign("right");
					}
				}
			}
		}

		return style;
	}

	private static String removeUnits(String string)
	{
		string = string.replaceAll("em", "");
		string = string.replaceAll("ex", "");
		string = string.replaceAll("px", "");
		string = string.replaceAll("pt", "");
		string = string.replaceAll("pc", "");
		string = string.replaceAll("cm", "");
		string = string.replaceAll("mm", "");
		string = string.replaceAll("in", "");
		return string;
	}

	/**
	 * Reads the file (defined by <b>filename</b>) into a string
	 * @param filename file to be read
	 * @return the file content in a string
	 */
	public static String readFile(String filename)
	{
		BufferedReader reader;
		try
		{
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
			StringBuffer result = new StringBuffer();
			String tmp;

			try
			{
				tmp = reader.readLine();

				while (tmp != null)
				{
					result.append(tmp + System.getProperty("line.separator"));
					try
					{
						tmp = reader.readLine();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}

				try
				{
					reader.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}

				return result.toString();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		catch (FileNotFoundException e1)
		{
			e1.printStackTrace();
		}

		return null;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
	}

	/**
	 * Removes all defs and makes them inline, if visuals are in question, gradient fills are removed and a single color is put inline.
	 * Group style is inherited into individual element and groups are removed.
	 * NOTE: not implemented yet
	 */
	private Document flattenSvg(Document svgDoc)
	{
		Node root = svgDoc.getElementsByTagName("svg").item(0);
		//translate grouped style attributes to singular style attributes
		breakGroupStyle(root);
		// look for defs elements
		Map<String, Node> defsTable = parseDefs(root);
		//parse for fill defs and make them inline where needed
		parseFills(root, root);
		parseStrokes(root, root);
		// look for use elements
		parseUses(root, defsTable);
		flattenGroupsStyle(root);
		removeGroupsSvg(root);
		return svgDoc;
	}

	private void parseFills(Node root, Node svgRoot)
	{
		if (root != null)
		{
			Map<String, String> fillTable = parseFillDefs(svgRoot);
			NodeList children = root.getChildNodes();

			if (children.getLength() > 0)
			{
				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					//					if (currChild.getNodeType() == Node.ELEMENT_NODE || currChild.getNodeName().equals("text"))
					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						Element currEl = (Element) currChild;
						String currFill = currEl.getAttribute("fill");

						if (currFill.contains("url(#"))
						{
							currFill = currFill.replaceAll("url\\(\\#", "");
							currFill = currFill.replaceAll("\\)", "");
							currFill = currFill.replaceAll(" ", "");
							currEl.setAttribute("fill", fillTable.get(currFill));
						}

						parseFills(currChild, svgRoot);
					}
				}
			}
		}
	}

	private void parseStrokes(Node root, Node svgRoot)
	{
		if (root != null)
		{
			Map<String, String> strokeTable = parseStrokeDefs(svgRoot);
			NodeList children = root.getChildNodes();

			if (children.getLength() > 0)
			{
				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					//					if (currChild.getNodeType() == Node.ELEMENT_NODE || currChild.getNodeType() == Node.TEXT_NODE)
					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						Element currEl = (Element) currChild;
						String currStroke = currEl.getAttribute("stroke");

						if (currStroke.contains("url(#"))
						{
							currStroke = currStroke.replaceAll("url\\(\\#", "");
							currStroke = currStroke.replaceAll("\\)", "");
							currStroke = currStroke.replaceAll(" ", "");
							currEl.setAttribute("stroke", strokeTable.get(currStroke));
						}

						parseStrokes(currChild, svgRoot);
					}
				}
			}
		}
	}

	private Map<String, String> parseFillDefs(Node root)
	{
		if (root != null)
		{
			Map<String, String> fillTable = new Hashtable<String, String>();
			NodeList children = root.getChildNodes();

			if (children.getLength() > 0)
			{
				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						Element currEl = (Element) currChild;

						if (currEl.getNodeName().equals("linearGradient") || currEl.getNodeName().equals("radialGradient"))
						{
							String avgColor = getAvgGradientColor(currEl); 
							fillTable.put(currEl.getAttribute("id"), avgColor);
						}

						fillTable.putAll(parseFillDefs(currChild));
					}
				}
			}

			return fillTable;
		}
		else
		{
			return null;
		}
	}

	private Map<String, String> parseStrokeDefs(Node root)
	{
		if (root != null)
		{
			Map<String, String> strokeTable = new Hashtable<String, String>();
			NodeList children = root.getChildNodes();

			if (children.getLength() > 0)
			{
				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						Element currEl = (Element) currChild;

						if (currEl.getNodeName().equals("linearGradient") || currEl.getNodeName().equals("radialGradient"))
						{
							String avgColor = getAvgGradientColor(currEl); 
							strokeTable.put(currEl.getAttribute("id"), avgColor);
						}

						strokeTable.putAll(parseStrokeDefs(currChild));
					}
				}
			}

			return strokeTable;
		}
		else
		{
			return null;
		}
	}

	private String getAvgGradientColor(Element element)
	{
		if (element != null)
		{
			String finalColor;
			ArrayList<Integer> arrR = new ArrayList<Integer>();
			ArrayList<Integer> arrG = new ArrayList<Integer>();
			ArrayList<Integer> arrB = new ArrayList<Integer>();

			NodeList children = element.getChildNodes();

			for (int i = 0; i < children.getLength(); i++)
			{
				Node currChild = children.item(i);

				if (currChild.getNodeType() == Node.ELEMENT_NODE)
				{
					Element currEl = (Element) currChild;

					if (currChild.getNodeName().equals("stop"))
					{
						Color col = new Color(0, 0, 0);
						String colS = currEl.getAttribute("stop-color");

						if (colS.charAt(0) == '#')
						{
							col = Color.decode(colS);
						}
						else if (colS == null || colS.equals("")) 
						{
							return null;
						}
						else 
						{
							col = stringToColor(colS);
						}

						if (col != null)
						{
							arrR.add(0, col.getRed());
							arrG.add(0, col.getGreen());
							arrB.add(0, col.getBlue());
						}
					}
				}
			}

			int r = 0;
			int g = 0;
			int b = 0;

			for (int i = 0; i < arrR.size(); i++)
			{
				r = r + arrR.get(i);
				g = g + arrG.get(i);
				b = b + arrB.get(i);
			}

			if (arrR.size() > 0)
			{
				r = r / arrR.size();
			}
			else
			{
				r = 0;
			}
			
			if (arrR.size() > 0)
			{
				g = g / arrG.size();
			}
			else
			{
				g = 0;
			}
			
			if (arrR.size() > 0)
			{
				b = b / arrB.size();
			}
			else
			{
				b = 0;
			}
			

			//		finalColor = "#" + Integer.toString(r) + Integer.toString(g) + Integer.toString(b);
			finalColor = "#";

			StringBuilder sbr = new StringBuilder();
			sbr.append(Integer.toHexString(r));

			if (sbr.length() < 2) 
			{
				sbr.insert(0, '0'); // pad with leading zero if needed
			}

			finalColor = finalColor + sbr.toString();

			StringBuilder sbg = new StringBuilder();
			sbg.append(Integer.toHexString(g));

			if (sbg.length() < 2) 
			{
				sbg.insert(0, '0'); // pad with leading zero if needed
			}

			finalColor = finalColor + sbg.toString();

			StringBuilder sbb = new StringBuilder();
			sbb.append(Integer.toHexString(b));

			if (sbb.length() < 2) 
			{
				sbb.insert(0, '0'); // pad with leading zero if needed
			}

			finalColor = finalColor + sbb.toString();

			return finalColor;
		}
		else
		{
			return "";
		}
	}

	private Color stringToColor(String col)
	{
		//TODO either add the extended color set too, or find a better solution
		if (col.equals("black"))
		{
			return Color.BLACK;
		}
		else if (col.equals("blue"))
		{
			return Color.BLUE;
		}
		else if (col.equals("cyan"))
		{
			return Color.CYAN;
		}
		else if (col.equals("darkGray"))
		{
			return Color.DARK_GRAY;
		}
		else if (col.equals("gray"))
		{
			return Color.gray;
		}
		else if (col.equals("green"))
		{
			return Color.GREEN;
		}
		else if (col.equals("yellow"))
		{
			return Color.YELLOW;
		}
		else if (col.equals("lightGray"))
		{
			return Color.LIGHT_GRAY;
		}
		else if (col.equals("magenta"))
		{
			return Color.MAGENTA;
		}
		else if (col.equals("orange"))
		{
			return Color.ORANGE;
		}
		else if (col.equals("pink"))
		{
			return Color.PINK;
		}
		else if (col.equals("red"))
		{
			return Color.RED;
		}
		else if (col.equals("white"))
		{
			return Color.WHITE;
		}
		else
		{
			return null;
		}
	}

	private void breakGroupStyle(Node root)
	{
		NodeList children = null;

		if (root != null && root.hasChildNodes())
		{
			children = root.getChildNodes();

			for (int i = 0; i < children.getLength(); i++)
			{
				Node currChild = children.item(i);

				if (currChild.getNodeType() == Node.ELEMENT_NODE)
				{
					breakGroupStyle(currChild);
					Element currEl = (Element) currChild;
					Map<String, String> styles = getStylenames(currEl.getAttribute("style"));
					currEl.removeAttribute("style");

					if (styles != null)
					{
						for (Map.Entry<String, String> entry : styles.entrySet())
						{
							currEl.setAttribute(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		}
	}

	/**
	 * Returns the stylenames in a style of the form stylename[;key=value] or an
	 * empty array if the given style does not contain any stylenames.
	 * 
	 * @param style
	 *            String of the form stylename[;stylename][;key=value].
	 * @return Returns the stylename from the given formatted string.
	 */
	protected static Map<String, String> getStylenames(String style)
	{
		if (style != null && style.length() > 0)
		{
			Map<String, String> result = new Hashtable<String, String>();

			if (style != null)
			{
				String[] pairs = style.split(";");

				for (int i = 0; i < pairs.length; i++)
				{
					String[] keyValue = pairs[i].split(":");

					if (keyValue.length == 2)
					{
						result.put(keyValue[0].trim().replaceAll(";", ""), keyValue[1].trim().replaceAll(";", ""));
					}
				}
			}
			return result;
		}

		return null;
	}

	/**
	 * Recursively appends all styles and transformations of parents to all their children
	 * NOTE: for now, only styles as attributes and matrix transformations are applied (which are the most common)  
	 * @param parent
	 */
	private static void flattenGroupsStyle (Node parent)
	{
		if (parent != null)
		{
			NodeList children = parent.getChildNodes();
			XmlStyle parentStyle = getStyle(parent);
			Element parEl = (Element) parent;
			String parTr = parEl.getAttribute("transform");

			for (int i = 0; i < children.getLength(); i++)
			{
				Node currChild = children.item(i);

				if(currChild.getNodeType() == Node.ELEMENT_NODE)
				{
					XmlStyle currStyle = getStyle(currChild);

					if (currStyle.getStrokeColor().equals(""))
					{
						currStyle.setStrokeColor(parentStyle.getStrokeColor());
					}

					if (currStyle.getFillColor().equals(""))
					{
						currStyle.setFillColor(parentStyle.getFillColor());
					}

					if (currStyle.getStrokeWidth().equals(""))
					{
						currStyle.setStrokeWidth(parentStyle.getStrokeWidth());
					}

					if (currStyle.getLineJoin().equals(""))
					{
						currStyle.setLineJoin(parentStyle.getLineJoin());
					}

					if (currStyle.getLineCap().equals(""))
					{
						currStyle.setLineCap(parentStyle.getLineCap());
					}

					if (currStyle.getMiterLimit().equals(""))
					{
						currStyle.setMiterLimit(parentStyle.getMiterLimit());
					}

					if (currStyle.getDashPattern().equals(""))
					{
						currStyle.setDashPattern(parentStyle.getDashPattern());
					}

					if (currStyle.isDashed().equals(""))
					{
						currStyle.setDashed(parentStyle.isDashed());
					}

					if (currStyle.getAlpha().equals(""))
					{
						currStyle.setAlpha(parentStyle.getAlpha());
					}

					if (currStyle.getStrokeAlpha().equals(""))
					{
						currStyle.setStrokeAlpha(parentStyle.getStrokeAlpha());
					}

					if (currStyle.getFillAlpha().equals(""))
					{
						currStyle.setFillAlpha(parentStyle.getFillAlpha());
					}

					if (currStyle.getFontColor().equals(""))
					{
						currStyle.setFontColor(parentStyle.getFontColor());
					}

					//TODO inherit font style too
					//			if (currStyle.)

					//TODO make sure <tspan> is handled correctly

					if (currStyle.getFontSize().equals(""))
					{
						currStyle.setFontSize(parentStyle.getFontSize());
					}

					if (currStyle.getFontFamily().equals(""))
					{
						currStyle.setFontFamily(parentStyle.getFontFamily());
					}

					Element el = (Element) currChild;
					setSvgStyle(el, currStyle);

					//TODO merge possible transform matrices
					if(parTr.contains("matrix"))
					{
						Element currEl = (Element) currChild;
						String currTr = currEl.getAttribute("transform");

						if(currTr.contains("matrix"))
						{
							//combine matrices
							currEl.setAttribute("transform", multiplyStringMatrices(currTr, parTr, i));
						}
						else
						{
							//copy group matrix
							currEl.setAttribute("transform", parTr);
						}
					}

					if (currChild.getNodeName().equals("g"))
					{
						flattenGroupsStyle(currChild);
					}
				}
			}

			setSvgStyle((Element) parent, new XmlStyle());
		}
	}

	/**
	 * Removes groups from parent node and all of its children
	 * @param parent parent node
	 */
	private static void removeGroupsSvg(Node parent)
	{
		if(parent != null)
		{
			boolean foundGroup = true;

			while (foundGroup)
			{
				NodeList children = parent.getChildNodes();
				foundGroup = false;

				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						if(currChild.getNodeName().equals("g"))
						{
							foundGroup = true;
							Node nextChild = currChild.getFirstChild();

							while (nextChild != null)
							{
								Node childToMove = nextChild;
								nextChild = childToMove.getNextSibling();
								parent.insertBefore(childToMove, currChild);
							}

							parent.removeChild(currChild);
						}
					}
				}
			}
		}
	}

	/**
	 * Applies the desired style to the element, not overriding any existing attribute
	 * @param element the element that the style will be applied to
	 * @param style style being applied to the element
	 */
	private static void setSvgStyle(Element element, XmlStyle style)
	{
		// TODO Auto-generated method stub
		String s = style.getStrokeColor();

		if (s != "")
		{
			element.setAttribute("stroke", s);
		}

		s = style.getFillColor();

		if (s != "")
		{
			element.setAttribute("fill", s);
		}

		s = style.getStrokeWidth();

		if (s != "")
		{
			element.setAttribute("stroke-width", s);
		}

		s = style.getLineJoin();

		if (s != "")
		{
			element.setAttribute("stroke-linejoin", s);
		}

		s = style.getLineCap();

		if (s != "")
		{
			element.setAttribute("stroke-linecap", s);
		}

		s = style.getMiterLimit();

		if (s != "")
		{
			element.setAttribute("stroke-miterlimit", s);
		}

		s = style.getDashPattern();

		if (s != "")
		{
			element.setAttribute("stroke-dasharray", s);
		}

		s = style.getAlpha();

		if (s != "")
		{
			element.setAttribute("opacity", s);
		}

		s = style.getStrokeAlpha();

		if (s != "")
		{
			element.setAttribute("stroke-opacity", s);
		}

		s = style.getFillAlpha();

		if (s != "")
		{
			element.setAttribute("fill-opacity", s);
		}

		//TODO make sure <tspan> is handled correctly
		s = style.getFontColor();

		if (s != "")
		{
			Element e = (Element) element.getElementsByTagName("tspan").item(0);

			if (e != null)
			{
				e.setAttribute("fill", s);
			}
		}

		s = style.getFontSize();

		if (s != "")
		{
			Element e = (Element) element.getElementsByTagName("tspan").item(0);

			if (e != null)
			{
				e.setAttribute("font-size", s);
			}
		}


	}

	/**
	 * @param root root element
	 * @param defsTable
	 * @return removes defs and inserts them inline in use places
	 */
	private static void parseUses(Node root, Map<String, Node> defsTable)
	{
		if (root != null)
		{
			NodeList children = root.getChildNodes();

			if (children.getLength() > 0)
			{
				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						parseUses(currChild, defsTable);
						Element currEl = (Element) currChild; 

						if (currChild.getNodeName().toLowerCase().equals("use"))
						{
							NamedNodeMap attr = currChild.getAttributes();
							Node defNode = attr.getNamedItem("xlink:href");
							String defName = defNode.getNodeValue();
							defName = defName.substring(1);
							Node def = defsTable.get(defName);

							Node newNode = def.cloneNode(true);
							Element newGroup = root.getOwnerDocument().createElement("g");
							String tr = "matrix(1 0 0 1 " + currEl.getAttribute("x") + " " + currEl.getAttribute("y") + ")";
							newGroup.setAttribute("transform", tr);
							Element newElement = (Element) newNode;

							attr.removeNamedItem("xlink:href");

							if (attr.getNamedItem("x") != null && !attr.getNamedItem("x").equals(""))
							{
								attr.removeNamedItem("x");
							}

							if (attr.getNamedItem("y") != null && !attr.getNamedItem("y").equals(""))
							{
								attr.removeNamedItem("y");
							}

							for (int j = 0; j < attr.getLength(); j++)
							{
								newElement.setAttribute(attr.item(j).getNodeName(), attr.item(j).getNodeValue());
							}

							newGroup.appendChild(newNode);
							root.replaceChild(newGroup, currChild);
						}
					}
				}
			}
		}
	}

	/**
	 * @param root root element
	 * @return map of defs found in the document
	 */
	private Map<String, Node> parseDefs(Node root)
	{
		if (root != null)
		{
			Map<String, Node> defsTable = new Hashtable<String, Node>();
			NodeList children = root.getChildNodes();

			if (children.getLength() > 0)
			{
				for (int i = 0; i < children.getLength(); i++)
				{
					Node currChild = children.item(i);

					if (currChild.getNodeType() == Node.ELEMENT_NODE)
					{
						Element currEl = (Element) currChild;

						if (!currEl.getAttribute("id").equals(""))
						{
							defsTable.put(currEl.getAttribute("id"), currEl);
						}

						defsTable.putAll(parseDefs(currChild));
					}
				}
			}

			return defsTable;
		}

		return null;
	}

	/**
	 * Returns a new document for the given XML string.
	 * 
	 * @param xml
	 *            String that represents the XML data.
	 * @return Returns a new XML document.
	 */
	static Document parseXml(String xml)
	{
		try
		{
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
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
	 * Reads constrainst from config doc
	 * @param constraintsXml stencil config doc in string format
	 * @return ArrayList<Connection> of data read from constraintsXml
	 */
	public static ArrayList<Connection> getConstraintsFromXml(String constraintsXml)
	{
		ArrayList<Connection> connectionList = new ArrayList<Connection>();

		if (constraintsXml != null && !constraintsXml.equals(""))
		{
			Document configDoc = parseXml(constraintsXml);
			NodeList connectionsRootList = configDoc.getElementsByTagName("connections");

			if (connectionsRootList != null)
			{
				for (int i = 0; i < connectionsRootList.getLength(); i++)
				{
					Connection currConnection = new Connection();
					Element connectionRoot = (Element) connectionsRootList.item(i);
					currConnection.setName(connectionRoot.getAttribute("name"));
					currConnection.setId(connectionRoot.getAttribute("id"));
					NodeList constraintList = connectionRoot.getChildNodes();

					if (constraintList != null)
					{
						for (int j = 0; j < constraintList.getLength(); j++)
						{
							Node currNode = constraintList.item(j);
							Element currConstraint = null;

							if (currNode instanceof Element)
							{
								currConstraint = (Element) currNode;
								Constraint newConstraint = new Constraint();
								newConstraint.setName(currConstraint.getAttribute("name"));
								newConstraint.setX(Double.valueOf(currConstraint.getAttribute("x")));
								newConstraint.setY(Double.valueOf(currConstraint.getAttribute("y")));
								boolean perimeter = false;

								if (currConstraint.getAttribute("perimeter").equals("1"))
								{
									perimeter = true;
								}

								newConstraint.setPerimeter(perimeter);
								currConnection.addConstraint(newConstraint);
							}
						}
					}

					connectionList.add(currConnection);
				}

				return connectionList;
			}
			else
			{
				return null;
			}
		}
		else
		{
			return null;
		}
	}

	/**
	 * Prints Document <b>doc</b> to <b>out</b> OutputStream
	 * @param doc
	 * @param out
	 */
	public static void printDocument(Document doc, OutputStream out) 
	{
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer;
		try
		{
			transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			try
			{
				transformer.transform(new DOMSource(doc), new StreamResult(new OutputStreamWriter(out, "UTF-8")));
			}
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
			}
			catch (TransformerException e)
			{
				e.printStackTrace();
			}
		}
		catch (TransformerConfigurationException e1)
		{
			e1.printStackTrace();
		}
	}

	/**
	 * Returns the documents as string
	 * @param doc
	 * @param out
	 * @return document in string format
	 */
	public static String printDocumentString(Document doc, OutputStream out) 
	{
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer;
		try
		{
			transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

			try
			{
				transformer.transform(new DOMSource(doc), new StreamResult(new OutputStreamWriter(out, "UTF-8")));
			}
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
			}
			catch (TransformerException e)
			{
				e.printStackTrace();
			}
		}
		catch (TransformerConfigurationException e)
		{
			e.printStackTrace();
		}

		return out.toString();
	}

	/**
	 * Creates the foundation XML for the stencil, based on destConfigDoc (which is generated from the group config XML and stencil config XML)
	 * @param destDoc the backbone is created in destDoc 
	 * @param destConfigDoc configuration of the destination doc
	 * @param srcSVGDoc 
	 * @param shapeName name of the stencil
	 */
	private static void createBackbone(Document destDoc, XmlConfig destConfigDoc, String stencilName, Document srcSVGDoc, ArrayList <Constraint> svgConnections)
	{
		Element root = destDoc.createElement("shape");
		stencilName = stencilName.replaceAll("_", " ");
		root.setAttribute("name", stencilName);
		root.setAttribute("aspect", destConfigDoc.getAspectString());
		root.setAttribute("strokewidth", destConfigDoc.getStrokewidth());
		double s = destConfigDoc.getRelativeScalingRatio();

		if (destConfigDoc.isCalculateBorder())
		{
			double w = destConfigDoc.getStencilBoundsX() * s;
			double h = destConfigDoc.getStencilBoundsY() * s;
			root.setAttribute("w", String.valueOf(w));
			root.setAttribute("h", String.valueOf(h));
		}
		else
		{
			Element svgEl = (Element) srcSVGDoc.getElementsByTagName("svg").item(0);
			String width = svgEl.getAttribute("width");
			String height = svgEl.getAttribute("height");
			width = removeUnits(width);
			height = removeUnits(height);
			double w = Double.valueOf(width) * s;
			double h = Double.valueOf(height) * s;
			root.setAttribute("w", Double.toString(w));
			root.setAttribute("h", Double.toString(h));
		}

		destDoc.appendChild(root);

		Element connRoot = destDoc.createElement("connections");
		root.appendChild(connRoot);

		Connection connection = destConfigDoc.getConnection();
		ArrayList <Constraint> constraint = null;

		if (connection != null)
		{
			constraint = connection.getConstraints();
		}

		if (svgConnections != null)
		{
			if (constraint != null)
			{
				constraint.addAll(svgConnections);
			}
			else
			{
				constraint = svgConnections;
			}
		}
		
		if (constraint != null)
		{
			for (int i = 0; i < constraint.size(); i++)
			{
				Element currConstraint = destDoc.createElement("constraint");
				Constraint srcConstraint = constraint.get(i);
				currConstraint.setAttribute("x", String.valueOf(srcConstraint.getX()));
				currConstraint.setAttribute("y", String.valueOf(srcConstraint.getY()));
				currConstraint.setAttribute("name", srcConstraint.getName());
				currConstraint.setAttribute("perimeter", srcConstraint.getPerimeter());
				connRoot.appendChild(currConstraint);
			}
		}
		
		root.appendChild(connRoot);

		if (destConfigDoc.isBackground())
		{
			root.appendChild(destDoc.createElement("background"));
		}

		root.appendChild(destDoc.createElement("foreground"));
	}

	/**
	 * 
	 * @param node
	 * @return first child element of <b>node</b> or null if no children
	 */
	private Element getFirstChildElement(Node node)
	{
		NodeList children = node.getChildNodes();

		if (children.getLength() > 0)
		{
			for (int i = 0; i < children.getLength(); i++)
			{
				Node child = children.item(i);

				if(child.getNodeType() == Node.ELEMENT_NODE)
				{
					return (Element) child;
				}
			}
		}

		return null;
	}

	/**
	 * @param matrix1
	 * @param matrix2
	 * @param roundDec decimals to round to
	 * @return matrix1 * matrix2
	 */
	private static String multiplyStringMatrices(String matrix1, String matrix2, int roundDec)
	{
		if ((matrix1 == null || !matrix1.contains("matrix")) || (matrix2 == null || !matrix2.contains("matrix")))
		{
			return null;
		}

		double a1=0;
		double b1=0;
		double c1=0;
		double d1=0;
		double e1=0;
		double f1=0;
		String a1String;
		String b1String;
		String c1String;
		String d1String;
		String e1String;
		String f1String;

		matrix1 = matrix1.replaceAll(",", " ");
		matrix1 = matrix1.replaceAll("  ", " ");
		int startCurrIndex = matrix1.indexOf("matrix(");
		int endCurrIndex = matrix1.indexOf(" ", startCurrIndex);
		a1String = matrix1.substring(startCurrIndex+7, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix1.indexOf(" ", startCurrIndex);
		b1String = matrix1.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix1.indexOf(" ", startCurrIndex);
		c1String = matrix1.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix1.indexOf(" ", startCurrIndex);
		d1String = matrix1.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix1.indexOf(" ", startCurrIndex);
		e1String = matrix1.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix1.indexOf(")", startCurrIndex);
		f1String = matrix1.substring(startCurrIndex, endCurrIndex);

		a1 = Double.valueOf(a1String);
		b1 = Double.valueOf(b1String);
		c1 = Double.valueOf(c1String);
		d1 = Double.valueOf(d1String);
		e1 = Double.valueOf(e1String);
		f1 = Double.valueOf(f1String);

		Double[][] A = new Double[3][3];
		A[0][0] = a1;
		A[0][1] = c1;
		A[0][2] = e1;
		A[1][0] = b1;
		A[1][1] = d1;
		A[1][2] = f1;
		A[2][0] = 0.0;
		A[2][1] = 0.0;
		A[2][2] = 1.0;

		double a2=0;
		double b2=0;
		double c2=0;
		double d2=0;
		double e2=0;
		double f2=0;
		String a2String;
		String b2String;
		String c2String;
		String d2String;
		String e2String;
		String f2String;

		matrix2 = matrix2.replaceAll(",", " ");
		matrix2 = matrix2.replaceAll("  ", " ");
		startCurrIndex = matrix2.indexOf("matrix(");
		endCurrIndex = matrix2.indexOf(" ", startCurrIndex);
		a2String = matrix2.substring(startCurrIndex+7, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix2.indexOf(" ", startCurrIndex);
		b2String = matrix2.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix2.indexOf(" ", startCurrIndex);
		c2String = matrix2.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix2.indexOf(" ", startCurrIndex);
		d2String = matrix2.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix2.indexOf(" ", startCurrIndex);
		e2String = matrix2.substring(startCurrIndex, endCurrIndex);

		startCurrIndex = endCurrIndex + 1;
		endCurrIndex = matrix2.indexOf(")", startCurrIndex);
		f2String = matrix2.substring(startCurrIndex, endCurrIndex);

		a2 = Double.valueOf(a2String);
		b2 = Double.valueOf(b2String);
		c2 = Double.valueOf(c2String);
		d2 = Double.valueOf(d2String);
		e2 = Double.valueOf(e2String);
		f2 = Double.valueOf(f2String);

		Double[][] B = new Double[3][3];
		B[0][0] = a2;
		B[0][1] = c2;
		B[0][2] = e2;
		B[1][0] = b2;
		B[1][1] = d2;
		B[1][2] = f2;
		B[2][0] = 0.0;
		B[2][1] = 0.0;
		B[2][2] = 1.0;

		int mA = A.length;
		int nA = A[0].length;
		int mB = B.length;
		int nB = A[0].length;

		if (nA != mB)
		{
			throw new RuntimeException("Illegal matrix dimensions.");
		}

		double[][] C = new double[mA][nB];

		for (int i = 0; i < mA; i++)
		{
			for (int j = 0; j < nB; j++)
			{
				for (int k = 0; k < nA; k++)
				{
					C[i][j] += (A[i][k] * B[k][j]);
				}
			}
		}

		B[0][0] = a2;
		B[0][1] = c2;
		B[0][2] = e2;
		B[1][0] = b2;
		B[1][1] = d2;
		B[1][2] = f2;
		B[2][0] = 0.0;
		B[2][1] = 0.0;
		B[2][2] = 1.0;

		String result = "matrix(" + Shape2Xml.roundToDecimals(C[0][0], roundDec) + " " 
				+ Shape2Xml.roundToDecimals(C[1][0], roundDec) + " "
				+ Shape2Xml.roundToDecimals(C[0][1], roundDec) + " "
				+ Shape2Xml.roundToDecimals(C[1][1], roundDec) + " "
				+ Shape2Xml.roundToDecimals(C[0][2], roundDec) + " "
				+ Shape2Xml.roundToDecimals(C[1][2], roundDec) + ")";
		return result;
	}
	
	/**
	 * Reads out and removes the connection elements from svgDoc
	 * @param svgDoc
	 * @return all the connections (ellipse element with mxConnection="1" attribute)
	 */
	private Connection getConnections(Document svgDoc)
	{
		Connection connections = new Connection();
		
		if (svgDoc != null)
		{
			NodeList ellipseList = svgDoc.getElementsByTagName("ellipse");

			if (ellipseList != null)
			{
				for (int i = 0; i < ellipseList.getLength(); i++)
				{
					Element currEllipse = (Element) ellipseList.item(i);
					if (currEllipse.getAttribute("mxConnection").equals("1"))
					{
						Constraint newConstraint = new Constraint();
						newConstraint.setName(currEllipse.getAttribute("mxName"));
						newConstraint.setX(Double.valueOf(currEllipse.getAttribute("cx")));
						newConstraint.setY(Double.valueOf(currEllipse.getAttribute("cy")));
						boolean perimeter = false;

						if (currEllipse.getAttribute("mxPerimeter").equals("1"))
						{
							perimeter = true;
						}

						newConstraint.setPerimeter(perimeter);
						connections.addConstraint(newConstraint);
					}
				}

				return connections;
			}
		}

		return null;
	}

	/**
	 * Reads out and removes the connection elements from svgDoc
	 * @param svgDoc
	 * @return all the connections (ellipse element with mxConnection="1" attribute)
	 */
	private Document removeConnections(Document svgDoc)
	{
		if (svgDoc != null)
		{
			NodeList ellipseList = svgDoc.getElementsByTagName("ellipse");

			if (ellipseList != null)
			{
				for (int i = 0; i < ellipseList.getLength(); i++)
				{
					Element currEllipse = (Element) ellipseList.item(i);

					if (currEllipse.getAttribute("mxConnection").equals("1"))
					{
						currEllipse.getParentNode().removeChild(currEllipse);
						i--;
					}
				}
			}
		}

		return svgDoc;
	}
};

