svg2xml
=======

SVG to XML mxGraph stencil definition translation tool. This was created for internal use, so there are lots of things unfinished.

To compile and run this, use ant or the commands below.

Compile
=======

```
mkdir classes
javac -d classes -classpath lib/mxgraph-core.jar -sourcepath src src/com/mxgraph/svg2xml/Svg2XmlGui.java
```

Run
===

`java -classpath lib/mxgraph-core.jar:classes com.mxgraph.svg2xml.Svg2XmlGui`


Quick start quide
=================

Run Svg2XmlGui. The left file system defines what files or folders you want to convert. The right one, defines the destination.

If you select one file, a single stencil XML file will be generated for just that one stencil. 

If you select multiple files, a single stencil XML file will be generated for the selected stencils. So those stencils will be one library.

If you select a folder, all the files in the folder and all subfolders will be processe. Every folder will get one library created. So at the destination, all folders from the source path will be recreated and libraries will be named after folder names.

Options
=======

NOTE: most of the options aren't implemented yet (as noted in the UI). Also some of the options are not thorougly tested.

Calculate border
================

If checked, stencil borders will be calculated based on content. If unchecked, the borders or viewpoint defined in the SVG will be used.

Use relative scaling
====================

If other than 1.00, the resulting stencils will be bigger or smaller compared to the source.

Round coordinates
=================

If you want to reduce the size, with some compromise to precision, use rounding. For stencils bigger than 100x100 rounding to 2 decimal points is usually a decent choice.
