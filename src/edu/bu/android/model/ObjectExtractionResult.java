package edu.bu.android.model;

/**
 * The class extracted from the JSON library
 * 
 * @author wil
 *
 */
public class ObjectExtractionResult {

	private boolean outgoing;
	private String className;

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public boolean isOutgoing() {
		return outgoing;
	}

	public void setOutgoing(boolean outgoing) {
		this.outgoing = outgoing;
	}
}
