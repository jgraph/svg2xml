package com.mxgraph.svgxml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;

/**
 * Takes one or more SVG files and outputs the mxGraph equivalent XML
 * stencil format for each SVG definition.
 *
 */
public class svg2xml
{
	private final String stencilUserMarker = "mxGraph";
	public svg2xml()
	{

	}

	/**
	 * @param args the path to the file(s) to convert
	 */
	public static void main(String[] args)
	{
		svg2xml log = new svg2xml();
		if (args == null || args.length == 0)
		{
			JFrame frame = new JFrame();

			JFileChooser fc = new JFileChooser();

			// Create the actions
			Action openAction = new OpenAction(log, frame, fc);

			// Create buttons for the actions
			JButton openButton = new JButton(openAction);

			frame.getContentPane().add(openButton);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.pack();
			frame.setVisible(true);
			frame.setSize(200, 150);
		}
		else
		{
			File[] files = new File[args.length];

			for (int i = 0; i < args.length; i++)
			{
				files[i] = new File(args[i]);
			}

			log.parseFiles(files);
		}
	}

	public void parseFiles(File[] files)
	{
		JFileChooser dc = new JFileChooser();
		dc.setMultiSelectionEnabled(false);
		dc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int saveReturnVal = dc.showSaveDialog(dc);
		if (saveReturnVal == JFileChooser.APPROVE_OPTION)
		{
			File destPath = dc.getSelectedFile();
			
			boolean isNewGroup = true;
			boolean isLastInGroup = true;
			String groupXml = new String();
			
			for (int i = 0; i < files.length; i++)
			{
				String currentDestPath = destPath.getAbsolutePath();
				String nodeXml = null;
				String nodeName = null;
				isNewGroup = false;
				isLastInGroup = false;
				
				try
				{
					nodeXml =  svg2xml.readFile(files[i].getAbsolutePath());
					nodeName = files[i].getName();
					nodeName = nodeName.substring(0, nodeName.lastIndexOf("."));
				}

				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// remove group tags ("flatten") from the SVG to avoid further complications
				// TODO all child elements should inherit the possible group style
				nodeXml = nodeXml.replaceAll("<g>", "");
				nodeXml = nodeXml.replaceAll("</g>", "");
				nodeXml = nodeXml.replaceAll("<g .*>", "");
				nodeXml = nodeXml.replaceAll("</g .*>", "");
				if ((nodeXml != null) && (nodeName != null))
				{
//					nodeXml = nodeXml.replaceAll("\\W","");
					// Some editors place a 3 byte BOM at the start of files
					// Ensure the first char is a "<"
					int lessthanIndex = nodeXml.indexOf("<");
					nodeXml = nodeXml.substring(lessthanIndex);
					int doctypeIndex = nodeXml.indexOf("<!DOCTYPE");
					if (doctypeIndex>-1)
					{
						// we have the <!DOCTYPE entry>
						int doctypeEndIndex = nodeXml.indexOf(">", doctypeIndex);
						String tempXml = nodeXml.substring(0, doctypeIndex) + nodeXml.substring(doctypeEndIndex+1, nodeXml.length());
						nodeXml = tempXml;
					}
					
					// looking for a config file
					String nodeConfigXml = null;
					try
					{
						String configNameString = files[i].getAbsolutePath();
						int pointIndex = configNameString.lastIndexOf('.');
						configNameString = configNameString.substring(0, pointIndex) + "_config.xml";
						
						File testFile = new File(configNameString);
						if (testFile.exists())
							nodeConfigXml =  svg2xml.readFile(configNameString);
					}
					catch (IOException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					// looking for a group config file
					String nodeGroupConfigXml = null;
					try
					{
						String configNameString = files[i].getParent();
						configNameString += "_config.xml";

						File testFile = new File(configNameString);
						if (testFile.exists())
							nodeGroupConfigXml =  svg2xml.readFile(configNameString);
					}
					catch (IOException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					
					mxSvgParser newShape = new mxSvgParser(nodeXml,nodeName, nodeGroupConfigXml, nodeConfigXml);
					
					//check if a new group is started
					if (i==0)
						isNewGroup = true;
					else
					{
						String currParent = files[i].getParent();
						String oldParent = files[i-1].getParent();
						if(currParent.equals(oldParent))
							isNewGroup = false;
						else
							isNewGroup = true;
					}
					
					//check if this is the last file in the group
					if (i+1==files.length)
						isLastInGroup = true;
					else
					{
						String currParent = files[i].getParent();
						String nextParent = files[i+1].getParent();
						if(currParent.equals(nextParent))
							isLastInGroup = false;
						else
							isLastInGroup = true;
					}
					
					// here we need some group naming check
					String currentPath = files[i].getAbsolutePath();
					currentPath = currentPath.substring(2, currentPath.lastIndexOf("."));
					
					// if new group then we save the old file and open a new one
					// if not a new group then we just add the xml to the group xml
					if (isNewGroup)
					{
						// TODO form the group name
						String groupName = stencilUserMarker;
						File currFile = new File(files[i].getAbsolutePath());
						ArrayList <String> folders = new ArrayList <String>();
						while (!currFile.getParentFile().getName().equals("svgroot") && currFile.getParent().length() > 4)
						{
							currFile = currFile.getParentFile();
							folders.add(0, currFile.getName());
						}
						for (int j=0; j<folders.size(); j++)
						{
							groupName += "." + folders.get(j);
						}
						groupXml = "<shapes name=\"" + groupName + "\">\n";
						groupXml += newShape.getXMLString();
					}
					else
					{
						groupXml += newShape.getXMLString();
					}
					
					// save the xml
					if(isLastInGroup)
					{
						groupXml += "</shapes>";
						try
						{
							// TODO determine group file name
							currentDestPath += files[i].getParent().substring(2, files[i].getParent().length()) + ".xml";
							currentDestPath = currentDestPath.toLowerCase();
							currentDestPath = currentDestPath.replaceAll("\\s", "_");
							File myDestFile = new File(currentDestPath);
							File myDestRoot = new File(myDestFile.getParent());
							myDestRoot.mkdirs();
							FileWriter fileWriter = new FileWriter(myDestFile);
							BufferedWriter writer = new BufferedWriter(fileWriter);
							writer.write(groupXml);
							writer.close();

						} catch(Exception ex) 
						{
							ex.printStackTrace();
						}
					}
				}
			}
		}

	}

	public static String readFile(String filename) throws IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(filename)));
		StringBuffer result = new StringBuffer();
		String tmp = reader.readLine();

		while (tmp != null)
		{
			result.append(tmp + "\n");
			tmp = reader.readLine();
		}

		reader.close();

		return result.toString();
	}
}
