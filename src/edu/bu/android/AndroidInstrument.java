package edu.bu.android;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Body;
import soot.BodyTransformer;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Transform;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.AbstractInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.options.Options;
import soot.util.Chain;
import edu.bu.android.model.ClassField;
import edu.bu.android.model.ObjectExtractionQuery;
import edu.bu.android.model.ObjectExtractionResult;


public class AndroidInstrument {
	final static Logger logger = LoggerFactory.getLogger(AndroidInstrument.class);
	
	private final Hashtable<String, ObjectExtractionQuery> extractionPoints;
	private final Hashtable<String, ObjectExtractionQuery> incomingExtractionPoints;

	List<ObjectExtractionResult> results = new ArrayList<ObjectExtractionResult>();

	
	public AndroidInstrument(Hashtable<String, ObjectExtractionQuery> outgoingExtractionPoints,
			Hashtable<String, ObjectExtractionQuery> incomingExtractionPoints){ 
		this.extractionPoints = outgoingExtractionPoints;
		extractionPoints.putAll(incomingExtractionPoints);
		this.incomingExtractionPoints = incomingExtractionPoints;
	}

	public void process(){
		Scene.v().loadNecessaryClasses();
		locateMethodCalls();
		for (ObjectExtractionResult r : results){
		extractFieldsFromClass(r.getClassName());
		}
	}
	private void getClasses(){
		Scene.v().loadNecessaryClasses();
		Chain<SootClass> classes = Scene.v().getClasses();
		for (Iterator<SootClass> it = classes.snapshotIterator(); it.hasNext();){
			SootClass clazz = it.next();
			
			if (clazz.getName().contains("JsonIP")){
				extractFields(clazz);
				logger.info("Class " + clazz.getName());
			}	
		}
	}
	private void extractFieldsFromClass(String clazz){
		logger.info("\tClass " + clazz);

		SootClass sootclass = Scene.v().getSootClass(clazz);//loadClassAndSupport(clazz);
		sootclass.setApplicationClass();
		extractFields(sootclass);
	}

	
	/**
	 * Extract all of the fields from a class
	 * @param clazz
	 * @return	List of extracted fields 
	 */
	private List<ClassField> extractFields(SootClass clazz){
		List<ClassField> extractedFields = new ArrayList<ClassField>();
		Chain<SootField> fields = clazz.getFields();
		for (Iterator<SootField> it = fields.snapshotIterator(); it.hasNext();){
			SootField field = it.next();
		
			String name = field.getName();
			if (name.contains("this$"))
				continue;
			String type = field.getType().toString();
			
			ClassField cf = new ClassField();
			cf.setClazz(clazz.getClass());
			cf.setName(name);
			cf.setType(type);
			extractedFields.add(cf);
			logger.info("\ttype=" + type + " name=" + name );
		}
		return extractedFields;
	}
	
	
	private void analyzeUnit(Unit u){
		//important to use snapshotIterator here
			
			u.apply(new AbstractStmtSwitch() {
				
				 public void caseInvokeStmt(InvokeStmt stmt)
				    {
					 //logger.info(stmt.toString());
				    }
	
				//what if result is passed to method?
				 public void caseAssignStmt(AssignStmt stmt)
			    {
					 if (stmt.toString().contains("fromJson")
							 || stmt.toString().contains("toJson")){
							//logger.info(stmt.toString());
						}			 
					 Value v = stmt.getRightOpBox().getValue();
					 if (v instanceof AbstractInvokeExpr){
						 
						 AbstractInvokeExpr aie = (AbstractInvokeExpr)v;
						 String sig = aie.getMethodRef().getSignature();
						 if (extractionPoints.containsKey(sig)){
							 ObjectExtractionQuery oe = extractionPoints.get(sig);
							 List<ValueBox> boxes = v.getUseBoxes(); //get from here
							 
							 ValueBox vb = boxes.get(oe.getPosition());
							 
							 if (vb != null){
								Value boxVal = vb.getValue();
								String c=null;
								if (boxVal instanceof JimpleLocal){
									 Type argType = vb.getValue().getType(); //Is this the class or a reference?
									c = argType.toString();
									 logger.info(sig + "\t" + c);

								} else if (boxVal instanceof ClassConstant){
									
									ClassConstant rt = (ClassConstant) boxVal;									
									c = rt.getValue().replace("/", ".");
									 logger.info(sig + "\t" + c);

								} else {
									logger.error("Fuck if I know");
								}
								if (c != null){
									 ObjectExtractionResult result = new ObjectExtractionResult();
									 result.setClassName(c);
									 results.add(result);
								}
							 }
						 }
					 
					 }
					 
					
			    }
				public void defaultCase(Object o){
					Stmt s = (Stmt)o;
					if (s.toString().contains("toJson") ||
							s.toString().contains("fromJson")){
					//logger.info(s.toString());
					}
				}
				/*
				public void caseInvokeStmt(InvokeStmt stmt) {
					
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					String name = invokeExpr.getMethodRef().declaringClass().getName();
					logger.info(name);
					
					logger.info("Type=" + invokeExpr.getType().toString());
					logger.info("Method=" + invokeExpr.getMethod().getName());
					String methodName = invokeExpr.getMethod().getName();
					if (methodName.equalsIgnoreCase("fromJson") ||
							methodName.equalsIgnoreCase("toJson")){
						logger.info("Fround it!: ");
	
					}
				}
				*/
			});
		
	}

	private void locateMethodCalls() {
		
		//compiler pass will hit every method. A body is the code of a method
		//jtp=jimple transformation pack
        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {

			@Override
			protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {

					final PatchingChain<Unit> units = b.getUnits();
					for(Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
						final Unit u = iter.next();
						analyzeUnit(u);

					}
				}
		}));
        PackManager.v().runPacks();
	}
	
	
	public static Hashtable<String,ObjectExtractionQuery> getOutgoing(){
		Hashtable<String, ObjectExtractionQuery> l = new Hashtable<String,ObjectExtractionQuery>();//new ArrayList<ObjectExtraction>();
		
		ObjectExtractionQuery oe = new ObjectExtractionQuery();
		oe.setPosition(0);
		oe.setSignature("<com.google.gson.Gson: java.lang.String toJson(java.lang.Object)>");
		
		l.put("<com.google.gson.Gson: java.lang.String toJson(java.lang.Object)>", oe);
		
		ObjectExtractionQuery oe2 = new ObjectExtractionQuery();
		oe2.setPosition(0);
		oe.setSignature("flexjson: java.lang.String serialize(java.lang.Object)>");
		l.put("flexjson: java.lang.String serialize(java.lang.Object)>", oe2);
		
		return l;
	}
	
	
	
	public static Hashtable<String,ObjectExtractionQuery> getIncoming(){
		Hashtable<String, ObjectExtractionQuery> l = new Hashtable<String,ObjectExtractionQuery>();//new ArrayList<ObjectExtraction>();
		
		ObjectExtractionQuery oe = new ObjectExtractionQuery();
		oe.setPosition(1);
		oe.setSignature("<com.google.gson.Gson: java.lang.Object fromJson(java.lang.String,java.lang.Class)>");
		l.put("<com.google.gson.Gson: java.lang.Object fromJson(java.lang.String,java.lang.Class)>", oe);
		return l;
	}
	
/**
 * -allow-phantom-refs -process-dir /home/wil/Documents/school/phd/2015_spring/defence_vulnerability_malware/2/test_apps/simple_v1.1.apk -android-jars /home/wil/libs/android/android-sdk-linux_x86/platforms
 * @param args
 */
	public static void main(String[] args) {
		String apk = args[0];
		String androidJars = args[1];

		List<String> processDirs = new ArrayList<String>();
		processDirs.add(apk);
		Options.v().set_process_dir(processDirs);
		Options.v().set_android_jars(androidJars);
		
		Options.v().set_allow_phantom_refs(true);
		//prefer Android APK files// -src-prec apk
		Options.v().set_src_prec(Options.src_prec_apk);
				
		AndroidInstrument instra = new AndroidInstrument(getOutgoing(),getIncoming());
		//instra.run(args);
		//instra.locateMethodCalls();
		//instra.getClasses();
		instra.process();
	}
}
