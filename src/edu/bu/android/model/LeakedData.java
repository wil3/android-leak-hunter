package edu.bu.android.model;


import org.apache.commons.codec.digest.DigestUtils;
import org.mongojack.Id;

public class LeakedData {

	@Id
	private String id;
	private String clazz;
	private boolean outgoing;
	private String type;
	private String name;
	private String apk;

	public String getClazz() {
		return clazz;
	}
	public void setClazz(String clazz) {
		this.clazz = clazz;
	}
	public boolean isOutgoing() {
		return outgoing;
	}
	public void setOutgoing(boolean outgoing) {
		this.outgoing = outgoing;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
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
	
	public void makeId(){
		String s = apk + clazz + outgoing + type + name;
		String sha = DigestUtils.sha256Hex(s);
		this.id=sha;
	}
	@Override
	public String toString(){
		String s = "ID: " + id + " APK: " + apk + " Class: " + clazz + " Outgoing? " + outgoing + " Type: " + type + " Name: " + name;

		return s;
	}
	public String getApk() {
		return apk;
	}
	public void setApk(String apk) {
		this.apk = apk;
	}
	
}
