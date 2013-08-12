package com.mxgraph.svgxml;

public class SvgElementParsingResult {
	private String elementXml = "";
	private String styleXml = "";
	private String prefixXml = "";
	private String suffixXml = "";
	public String getElementXml() {
		return elementXml;
	}
	public void setElementXml(String elementXml) {
		this.elementXml = elementXml;
	}
	public String getStyleXml() {
		return styleXml;
	}
	public void setStyleXml(String styleXml) {
		this.styleXml = styleXml;
	}
	public String getPrefixXml() {
		return prefixXml;
	}
	public void setPrefixXml(String prefixXml) {
		this.prefixXml = prefixXml;
	}
	public String getSuffixXml() {
		return suffixXml;
	}
	public void setSuffixXml(String suffixXml) {
		this.suffixXml = suffixXml;
	}
}
