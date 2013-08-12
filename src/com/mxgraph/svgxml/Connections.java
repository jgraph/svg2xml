package com.mxgraph.svgxml;

import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Connections {
	private String name;
	private String id;
	public ArrayList<Constraint> constraints = new ArrayList<Constraint>();
	
	public static ArrayList<Connections> getConstraintsFromXml(String constraintsXml)
	{
		ArrayList<Connections> connectionList = new ArrayList<Connections>();
		if (constraintsXml != null && !constraintsXml.equals(""))
		{
			Document configDoc = mxSvgParser.parseXml(constraintsXml);
			NodeList connectionsRootList = configDoc.getElementsByTagName("connections");

			if (connectionsRootList.getLength()>0)
			{
				for (int i=0; i<connectionsRootList.getLength(); i++)
				{
					Connections currConnection = new Connections();
					Element connectionRoot = (Element) connectionsRootList.item(i);
					currConnection.setName(connectionRoot.getAttribute("name"));
					currConnection.setId(connectionRoot.getAttribute("id"));
					NodeList constraintList = connectionRoot.getChildNodes();
					for (int j=0; j<constraintList.getLength(); j++)
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
								perimeter = true;
							newConstraint.setPerimeter(perimeter);
							currConnection.constraints.add(newConstraint);
						}
					}
					connectionList.add(currConnection);
				}
				return connectionList;
			}
			else
				return null;
		}
		else
			return null;
	}

	public int getConstraintNum() {
		return this.constraints.size();
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
