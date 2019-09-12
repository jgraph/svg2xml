/**
 * $Id: Svg2XmlGui.java,v 1.5 2013/04/25 13:21:06 mate Exp $
 * Copyright (c) 2013, JGraph
 */

/**
 * A GUI for the Svg2Xml converter
 */
package com.mxgraph.svg2xml;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;

public class Svg2XmlGui implements ActionListener{
	
	//true: calculate border 
	//false: read from source SVG if false
	protected boolean calculateBorder=true;
	
	//true: group stencils from same folder in a single XML
	//false: every stencil has its separate XML
	protected boolean groupStencils=true;
	
	//namespace for group (applies only if no more than one folder is selected)
	protected String groupNamespace = "";
	
	//use relative scaling (used if you want to change the size of resulting XMLs)
	protected boolean relativeScaling=true;

	// scaling ratio used if relativeScaling=true (source SVG / destination XML)
	protected float relativeScaleRatio=1.0f;
	
	//every stencil will be absoluteScalingX in X size, if relativeScaling=false
	protected double absoluteScalingX=100;

	//every stencil will be absoluteScalingY in Y size, if relativeScaling=false
	protected double absoluteScalingY=100;
	
	//round coords to reduce XML size
	protected boolean roundCoords=true;
	
	//round to decimals (used with roundCoords=true)
	protected int decimalsToRound=2;
	
	//i'm not yet sure about the exact functionality of this
	protected boolean overrideLocalConfig=false; //TODO make use of this var
	
	//saves a parsing log of the console, next to the resulting XML(s)
	protected boolean keepLog=true; //TODO make use of this var
	
	protected File[] sourceFiles;
	protected File destPath;

	protected JRadioButton useAbsScaleComponent = new JRadioButton();
	protected JTextField absXScaleComponent = new JTextField();
	protected JCheckBox calculateBorderComponent = new JCheckBox();
	protected JCheckBox groupStencilsComponent = new JCheckBox();
	protected JRadioButton useRelScaleComponent = new JRadioButton();
	protected JTextField relScaleComponent = new JTextField();
	protected JTextField absYScaleComponent = new JTextField();
	protected JCheckBox roundCoordinatesComponent = new JCheckBox();
	protected JCheckBox overrideLocalConfigComponent = new JCheckBox();
	protected JCheckBox keepLogComponent = new JCheckBox();
	protected JTextField roundDecimalNumComponent = new JTextField();
	protected JFileChooser sourceFileListComponent = new JFileChooser();
	protected JFileChooser destinationComponent = new JFileChooser();
	protected JTextField namespaceComponent = new JTextField();
	
	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{
		Svg2XmlGui gui = new Svg2XmlGui();
		gui.startHere(args, gui);
	}

	public void startHere(String[] args, Svg2XmlGui gui)
	{
		if (args.length>0)
		{
			Svg2Xml.main(args);
		}

		JFrame frame = new JFrame();

		JPanel filePanel = new JPanel(new GridLayout(1,2));
		JPanel settingsPanel = new JPanel();
		JPanel buttonsPanel = new JPanel();
		JPanel rightPanel = new JPanel();

		JLabel sourceLabel = new JLabel("Source file(s) or folder");
		JLabel destinationLabel = new JLabel("Destination folder");
		JPanel sourcePanel = new JPanel();
		JPanel destinationPanel = new JPanel();

		JButton startButton = new JButton("Start");
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}});

		sourceFileListComponent.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		sourceFileListComponent.setMultiSelectionEnabled(true);

		destinationComponent.setPreferredSize(new Dimension(400,500));
		destinationComponent.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		sourceFileListComponent.setControlButtonsAreShown(false);
		destinationComponent.setControlButtonsAreShown(false);

		sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.Y_AXIS));
		sourcePanel.add(sourceLabel);
		sourcePanel.add(sourceFileListComponent);

		destinationPanel.setLayout(new BoxLayout(destinationPanel, BoxLayout.Y_AXIS));
		destinationPanel.add(destinationLabel);
		destinationPanel.add(destinationComponent);

		calculateBorderComponent = new JCheckBox("Calculate border (or read from SVG)", calculateBorder);
		groupStencilsComponent = new JCheckBox("(TODO)Group stencils (or each in a separate file)", groupStencils);

		JPanel relScalePanel = new JPanel();
		useRelScaleComponent = new JRadioButton("Use relative scaling (dest/src):", relativeScaling);
		relScaleComponent = new JTextField("1.00", 4);
		relScalePanel.add(useRelScaleComponent);
		relScalePanel.add(relScaleComponent);

		JPanel absScalePanel = new JPanel();
		useAbsScaleComponent = new JRadioButton("(TODO)Use absolute scaling:", !relativeScaling);
		JLabel absScaleLabel1 = new JLabel("x:");
		absXScaleComponent = new JTextField(Double.toString(absoluteScalingX), 4);
		JLabel absScaleLabel2 = new JLabel("px   y:");
		absYScaleComponent = new JTextField(Double.toString(absoluteScalingY), 4);
		JLabel absScaleLabel3 = new JLabel("px");
		absScalePanel.add(useAbsScaleComponent);
		absScalePanel.add(absScaleLabel1);
		absScalePanel.add(absXScaleComponent);
		absScalePanel.add(absScaleLabel2);
		absScalePanel.add(absYScaleComponent);
		absScalePanel.add(absScaleLabel3);

		//Group the radio buttons.
		ButtonGroup scaleGroup = new ButtonGroup();
		scaleGroup.add(useRelScaleComponent);
		scaleGroup.add(useAbsScaleComponent);

		JPanel roundPanel = new JPanel();
		roundCoordinatesComponent = new JCheckBox("Round coordinates to ", roundCoords);
		roundDecimalNumComponent = new JTextField("2", 4);
		JLabel decimalLabel1 = new JLabel("decimals");
		roundPanel.add(roundCoordinatesComponent);
		roundPanel.add(roundDecimalNumComponent);
		roundPanel.add(decimalLabel1);

		overrideLocalConfigComponent = new JCheckBox("(TODO)Override local config", true);
		keepLogComponent = new JCheckBox("(TODO)Keep log", true);

		filePanel.add(BorderLayout.CENTER, sourcePanel);
		filePanel.add(BorderLayout.CENTER, destinationPanel);

		settingsPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = GridBagConstraints.RELATIVE;
		gbc.anchor = GridBagConstraints.WEST;
		settingsPanel.add(calculateBorderComponent, gbc);
		settingsPanel.add(groupStencilsComponent, gbc);
		settingsPanel.add(relScalePanel, gbc);
		settingsPanel.add(absScalePanel, gbc);
		settingsPanel.add(roundPanel, gbc);
		settingsPanel.add(overrideLocalConfigComponent, gbc);
		settingsPanel.add(keepLogComponent, gbc);

		settingsPanel.setPreferredSize(new Dimension(400,250));
		rightPanel.setPreferredSize(new Dimension(400,400));

		buttonsPanel.add(cancelButton);
		buttonsPanel.add(startButton);
		rightPanel.add(BorderLayout.NORTH, settingsPanel);
		rightPanel.add(BorderLayout.SOUTH, buttonsPanel);
		frame.add(BorderLayout.CENTER, filePanel);
		frame.add(BorderLayout.EAST, rightPanel);
		startButton.addActionListener(gui);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		frame.setSize(1200,800);

		// Centering the window
		frame.setLocationRelativeTo(null);

		frame.setVisible(true);

	}

	// the parser is started with args. They are parsed and sent to the svg parser class 
	public void parseArgs(String args[])
	{
		if (args.length>0)
		{
			Svg2Xml.main(args);
		}
	}

	// gather the selected files, the settings and send it to the parser class
	public void actionPerformed(ActionEvent event)
	{
		applyValues();

		// Get the selected file
		File[] files = sourceFileListComponent.getSelectedFiles();
		ArrayList <File> allFilesList = new ArrayList<File>();
		
		for ( File f : files )
		{
			if (!f.isDirectory())
			{
				// use next line for parsing only *.svg files - a temporary solution until the parser correctly handles a non-svg exception
    			if (f.getName().toLowerCase().endsWith("svg"))
    			{
    				allFilesList.add(f);
    			}
			}
		}

		for ( File f : files )
		{
			if (f.isDirectory())
			{
				allFilesList.addAll(this.walk(f.getAbsolutePath()));
			}
		}

		sourceFiles = allFilesList.toArray(new File[allFilesList.size()]);
		destPath = destinationComponent.getSelectedFile();
		
		if (destPath==null)
		{
			FileSystemView fw = destinationComponent.getFileSystemView();
			destPath = fw.getDefaultDirectory();
			System.out.println("Destination folder:" + destPath);
		}

		new Svg2Xml(this); 
	}


	/**
	 * 
	 */
	protected void applyValues()
	{
		setCalculateBorder(this.calculateBorderComponent.isSelected());
		setGroupStencils(this.groupStencilsComponent.isSelected());
		setRelativeScaling(this.useRelScaleComponent.isSelected());
		setRelativeScaleRatio(Float.parseFloat(this.relScaleComponent.getText()));
		setAbsoluteScalingX(Float.parseFloat(this.absXScaleComponent.getText()));
		setAbsoluteScalingY(Float.parseFloat(this.absYScaleComponent.getText()));
		setRoundCoords(this.roundCoordinatesComponent.isSelected());
		setDecimalsToRound(Integer.parseInt(this.roundDecimalNumComponent.getText()));
		setOverrideLocalConfig(this.overrideLocalConfigComponent.isSelected());
		setKeepLog(this.keepLogComponent.isSelected());
	}

	public boolean isCalculateBorder() 
	{
		return calculateBorder;
	}

	public boolean isOverrideLocalConfig() 
	{
		return overrideLocalConfig;
	}

	public boolean isKeepLog() 
	{
		return keepLog;
	}

	public void setCalculateBorder(boolean calculateBorder) 
	{
		this.calculateBorder = calculateBorder;
	}

	public void setOverrideLocalConfig(boolean overrideLocalConfig) 
	{
		this.overrideLocalConfig = overrideLocalConfig;
	}

	public void setKeepLog(boolean keepLog) 
	{
		this.keepLog = keepLog;
	}

	public boolean isGroupStencils() 
	{
		return groupStencils;
	}

	public void setGroupStencils(boolean groupStencils) 
	{
		this.groupStencils = groupStencils;
	}

	public boolean isRelativeScaling() 
	{
		return relativeScaling;
	}

	public void setRelativeScaling(boolean relativeScaling) 
	{
		this.relativeScaling = relativeScaling;
	}

	public double getRelativeScaleRatio() 
	{
		return relativeScaleRatio;
	}

	public void setRelativeScaleRatio(float relativeScaleRatio) 
	{
		this.relativeScaleRatio = relativeScaleRatio;
	}

	public double getAbsoluteScalingX() 
	{
		return absoluteScalingX;
	}

	public void setAbsoluteScalingX(double absoluteScalingX) 
	{
		this.absoluteScalingX = absoluteScalingX;
	}

	public double getAbsoluteScalingY() 
	{
		return absoluteScalingY;
	}

	public void setAbsoluteScalingY(double absoluteScalingY) 
	{
		this.absoluteScalingY = absoluteScalingY;
	}

	public boolean isRoundCoords() 
	{
		return roundCoords;
	}

	public void setRoundCoords(boolean roundCoords) 
	{
		this.roundCoords = roundCoords;
	}

	public int getDecimalsToRound() 
	{
		return decimalsToRound;
	}

	public void setDecimalsToRound(int decimalsToRound) 
	{
		this.decimalsToRound = decimalsToRound;
	}

	public JRadioButton getUseAbsScaleComponent() 
	{
		return useAbsScaleComponent;
	}

	public void setUseAbsScaleComponent(JRadioButton useAbsScaleComponent) 
	{
		this.useAbsScaleComponent = useAbsScaleComponent;
	}

	public JTextField getAbsXScaleComponent() 
	{
		return absXScaleComponent;
	}

	public void setAbsXScaleComponent(JTextField absXScaleComponent) 
	{
		this.absXScaleComponent = absXScaleComponent;
	}

	public JCheckBox getCalculateBorderComponent() 
	{
		return calculateBorderComponent;
	}

	public void setCalculateBorderComponent(JCheckBox calculateBorderComponent) 
	{
		this.calculateBorderComponent = calculateBorderComponent;
	}

	public JCheckBox getGroupStencilsComponent() 
	{
		return groupStencilsComponent;
	}

	public void setGroupStencilsComponent(JCheckBox groupStencilsComponent) 
	{
		this.groupStencilsComponent = groupStencilsComponent;
	}

	public JRadioButton getUseRelScaleComponent() 
	{
		return useRelScaleComponent;
	}

	public void setUseRelScaleComponent(JRadioButton useRelScaleComponent) 
	{
		this.useRelScaleComponent = useRelScaleComponent;
	}

	public JTextField getRelScaleComponent() 
	{
		return relScaleComponent;
	}

	public void setRelScaleComponent(JTextField relScaleComponent) 
	{
		this.relScaleComponent = relScaleComponent;
	}

	public JTextField getAbsYScaleComponent() 
	{
		return absYScaleComponent;
	}

	public void setAbsYScaleComponent(JTextField absYScaleComponent) 
	{
		this.absYScaleComponent = absYScaleComponent;
	}

	public JCheckBox getRoundCorrdinatesComponent() 
	{
		return roundCoordinatesComponent;
	}

	public void setRoundCorrdinatesComponent(JCheckBox roundCorrdinatesComponent) 
	{
		this.roundCoordinatesComponent = roundCorrdinatesComponent;
	}

	public JTextField getRoundDecimalNumComponent() 
	{
		return roundDecimalNumComponent;
	}

	public void setRoundDecimalNumComponent(JTextField roundDecimalNumComponent) 
	{
		this.roundDecimalNumComponent = roundDecimalNumComponent;
	}

	// reads all files from the selected path
	public ArrayList<File> walk( String path ) 
	{
        File root = new File( path );
        File[] list = root.listFiles();
        ArrayList <File> endList = new ArrayList<File>();

        for ( File f : list ) 
        {
            if (!f.isDirectory()) 
            {
				// use next line for parsing only *.svg files - a temporary solution until the parser correctly handles a non-svg exception
    			if (f.getName().toLowerCase().endsWith("svg"))
    			{
                	endList.add(f);
    			}
            }
        }
        
        for ( File f : list ) 
        {
            if ( f.isDirectory() ) 
            {
                endList.addAll(walk( f.getAbsolutePath()));
            }
        }
        
        return endList;
    }
}
