/**
 * $Id: mxSvgBounds.java,v 1.1 2012-11-15 13:26:47 gaudenz Exp $
 * Copyright (c) 2007-2012, JGraph Ltd
 */
package com.mxgraph.svgxml;

import java.awt.geom.Rectangle2D;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mxgraph.shape.mxStencilShape;
import com.mxgraph.util.mxXmlUtils;

public class mxSvgBounds extends mxStencilShape
{
	protected Node root;

	protected svgShape rootShape;

	protected Rectangle2D boundingBox;

	protected String name;

	protected String iconPath;

	public mxSvgBounds(String shapeXml)
	{
		Document document = mxXmlUtils.parseXml(shapeXml);

		if (document != null)
		{
			NodeList svgList = document.getElementsByTagName("svg");

			if (svgList != null && svgList.getLength() > 0)
			{
				this.root = svgList.item(0);
			}

			if (this.root != null)
			{
				rootShape = new svgShape(null, null);
				createShape(this.root, rootShape);
			}
		}


	}

	public void createShape(Node root, svgShape shape)
	{
		Node child = root.getFirstChild();
		/*
		 * If root is a group element, then we should add it's styles to the
		 * childrens...
		 */
		while (child != null)
		{
			svgShape subShape = createElement(child);

			if (subShape != null)
			{
				shape.subShapes.add(subShape);
			}
			child = child.getNextSibling();
		}

		// TODO: take into account shape transforms affecting bounds
		for (svgShape subShape : shape.subShapes)
		{
			if (subShape != null && subShape.shape != null)
			{
				if (boundingBox == null)
				{
					boundingBox = subShape.shape.getBounds2D();
				}
				else
				{
					boundingBox.add(subShape.shape.getBounds2D());
				}
			}
		}

		// If the shape does not butt up against either or both axis,
		// ensure it is flush against both
		if (boundingBox != null && (boundingBox.getX() != 0
				|| boundingBox.getY() != 0))
		{
			for (svgShape subShape : shape.subShapes)
			{
				if (subShape != null && subShape.shape != null)
				{
					transformShape(subShape.shape, -boundingBox.getX(),
							-boundingBox.getY(), 1.0, 1.0);
				}
			}
		}
	}

}
