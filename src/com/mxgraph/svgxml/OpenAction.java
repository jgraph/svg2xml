package com.mxgraph.svgxml;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

//This action creates and shows a modal open-file dialog.
public class OpenAction extends AbstractAction
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 5429984636714971859L;

	public JFrame frame;

	public JFileChooser chooser;

	public svg2xml prop;

	OpenAction(svg2xml prop, JFrame frame, JFileChooser chooser)
	{
		super("Open...");
		this.chooser = chooser;
		chooser.setMultiSelectionEnabled(true);
		this.frame = frame;
		this.prop = prop;
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		FileFilter svgFilter = new FileNameExtensionFilter("svg", "Svg", "SVG");
		 chooser.addChoosableFileFilter(svgFilter);
	}

	public void actionPerformed(ActionEvent evt)
	{
		// Show dialog; this method does not return until dialog is closed
		chooser.showOpenDialog(frame);

		// Get the selected file
		File[] files = chooser.getSelectedFiles();
		ArrayList <File> allFilesList = new ArrayList<File>();
		for ( File f : files )
		{
			if (f.isDirectory())
			{
				allFilesList.addAll(this.walk(f.getAbsolutePath()));
			}
			else
			{
				// use next line for parsing only *.svg files - a temporary solution until the parser correctly handles a non-svg exception
    			if (f.getName().toLowerCase().endsWith("svg"))
    				allFilesList.add(f);
			}
		}
//		System.out.println(allFilesList);
		File[] endFiles = allFilesList.toArray(new File[allFilesList.size()]);
		
		prop.parseFiles(endFiles);
	}
	
	// reads all files from the selected path
	public ArrayList<File> walk( String path ) {

        File root = new File( path );
        File[] list = root.listFiles();
        ArrayList <File> endList = new ArrayList<File>();

        for ( File f : list ) {
            if ( f.isDirectory() ) {
                endList.addAll(walk( f.getAbsolutePath()));
            }
            else
            {
				// use next line for parsing only *.svg files - a temporary solution until the parser correctly handles a non-svg exception
    			if (f.getName().toLowerCase().endsWith("svg"))
                	endList.add(f);
            }
        }
        return endList;
    }
}