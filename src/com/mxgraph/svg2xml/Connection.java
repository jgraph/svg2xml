/**
 * $Id: Connection.java,v 1.2 2013/04/08 07:14:16 mate Exp $
 * Copyright (c) 2013, JGraph
 */

package com.mxgraph.svg2xml;

import java.util.ArrayList;

/**
 * Complete connection data for a single stencils <connections> block
 */
public class Connection 
{
	private String name;
	private String id;
	private ArrayList<Constraint> constraints = new ArrayList<Constraint>();

	public String getName() 
	{
		return name;
	}

	public void setName(String name) 
	{
		this.name = name;
	}

	public String getId() 
	{
		return id;
	}

	public void setId(String id) 
	{
		id = id.replaceAll(" ", "");
		this.id = id;
	}

	public ArrayList<Constraint> getConstraints() 
	{
		return constraints;
	}

	/*
	returns the index of the constraint if it's added
	if a constraint with that name already exists, the new one isn't added and -1 is returned
	 */
	public int addConstraint(Constraint constraint) 
	{
		boolean isExistingName = false;
		String currName = constraint.getName();

		if (constraints != null && !currName.equals(""))
		{
			for (int i = 0; i < constraints.size(); i++)
			{
				if (constraints.get(i).getName().equals(currName))
				{
					isExistingName = true;
				}
			}
		}

		if (isExistingName == false)
		{
			constraints.add(constraint);
			return constraints.indexOf(constraint);
		}
		else
		{
			return -1;
		}
	}

	public int getConstraintNum() 
	{
		return this.constraints.size();
	}
}
