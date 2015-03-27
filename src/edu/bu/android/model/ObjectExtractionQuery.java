package edu.bu.android.model;

public class ObjectExtractionQuery {

	/**
	 * The argument position for which the object will be extracted
	 */
	private int position;
	
	/**
	 * The signature of the method for which the parameter will be extractd
	 */
	private String signature;

	public int getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}

	public String getSignature() {
		return signature;
	}

	/**
	 * Signature of the method to search in format:
	 * <[Method package]: [Full class name of return object] [method name]([Full class path of parameter 1],...)>
	 * 
	 * For example,
	 * <com.google.gson.Gson: java.lang.String toJson(java.lang.Object)>
	 */
	public void setSignature(String signature) {
		this.signature = signature;
	}
	
}
