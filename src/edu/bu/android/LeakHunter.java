package edu.bu.android;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Body;
import soot.BodyTransformer;
import soot.G;
import soot.Hierarchy;
import soot.PackManager;
import soot.PatchingChain;
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
import soot.jimple.FieldRef;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.AbstractInvokeExpr;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JimpleLocal;
import soot.util.Chain;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

import edu.bu.android.model.ClassField;
import edu.bu.android.model.LeakedData;
import edu.bu.android.model.ObjectExtractionQuery;
import edu.bu.android.model.ObjectExtractionResult;

public class LeakHunter {

	private final static Logger logger = LoggerFactory.getLogger(LeakHunter.class);
	private final static int MONGO_PORT = 27017;
	private final static String MONGO_DB = "apk";
	private final static String MONGO_COLL = "leakages";
	private final Hashtable<String, ObjectExtractionQuery> extractionPoints;
	List<ObjectExtractionResult> extractedObjects = new ArrayList<ObjectExtractionResult>();
	MongoClient mongoClient;
	private String apkName;
	
	public LeakHunter(String apkName, Hashtable<String, ObjectExtractionQuery> extractionPoints){ 
		this.extractionPoints = extractionPoints;
		this.apkName = apkName;
	}

	private MongoClient getDatabaseClient(){
		try {
			if (mongoClient == null){
				mongoClient = new MongoClient( "localhost" , MONGO_PORT );
			}
			return mongoClient;
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}
	private void closeDatabase(){
		MongoClient client = getDatabaseClient();
		if (client != null){
			client.close();
		}
	}
	public int process(){
		int numProccessed = 0;
		try {
			Scene.v().loadNecessaryClasses();
			locateMethodCalls();
			for (ObjectExtractionResult r : extractedObjects){
				numProccessed += extractFieldsFromClassandStore(r);
			}
		} catch (RuntimeException e){
		logger.error(e.getMessage());
	} finally {
			closeDatabase();
			Scene.v().releaseActiveHierarchy();
			Scene.v().releaseCallGraph();
			Scene.v().releaseReachableMethods();
			Scene.v().releasePointsToAnalysis();
			Scene.v().releaseSideEffectAnalysis();
			G.reset();
		}
		return numProccessed;
	}

	private int extractFieldsFromClassandStore(ObjectExtractionResult r){
		int numProccessed = 0;

		DB db = getDatabaseClient().getDB(MONGO_DB);
		DBCollection dbCollection = db.getCollection(MONGO_COLL);
		JacksonDBCollection<LeakedData, String> coll = JacksonDBCollection.wrap(dbCollection, LeakedData.class,
		        String.class);
		
		
		String clazz = r.getClassName();
		logger.info("\tClass " + clazz);
		SootClass sootclass = Scene.v().getSootClass(clazz);//loadClassAndSupport(clazz);
		sootclass.setApplicationClass();
		List<ClassField> classFields = extractFields(sootclass);
		
		
		for (ClassField classfield : classFields){
			LeakedData data = new LeakedData();
			data.setClazz(clazz);
			data.setType(classfield.getType());
			data.setName(classfield.getName());
			data.setOutgoing(r.isOutgoing());
			data.setApk(apkName);
			data.makeId();
			
			write(data);
			numProccessed++;

		}
		return numProccessed;
	}

	
	private void write(LeakedData data){
		try {

			DB db = getDatabaseClient().getDB(MONGO_DB);
			DBCollection dbCollection = db.getCollection(MONGO_COLL);
			JacksonDBCollection<LeakedData, String> coll = JacksonDBCollection.wrap(dbCollection, LeakedData.class,
			        String.class);
			
			coll.insert(data);
		} catch (MongoException e){
			logger.warn("Duplicate key " + data.toString());
		}
	}

	
	/**
	 * Extract all of the fields from a class
	 * @param clazz
	 * @return	List of extracted fields 
	 */
	public List<ClassField> extractFields(SootClass clazz){
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
			logger.debug("\ttype=" + type + " name=" + name );
		}
		return extractedFields;
	}
	
	private void processMap(String localName, boolean outgoing,List<PreloadValue> preloaded ){
		for (PreloadValue p : preloaded){
			if (p.localName.equals(localName)){
				p.data.setOutgoing(outgoing);
				p.data.makeId();
				logger.info("From map: " + p.data.getName());
				write(p.data);
			}
		}
	}
	class PreloadValue {
		public String localName;
		public LeakedData data;
	}
	private void analyzeUnit(Unit u, final Body b, final List<PreloadValue> preloaded){
		
			u.apply(new AbstractStmtSwitch() {
				
				/**
				 * If a map is used the values would have been set previously so track them
				 */
				 public void caseInvokeStmt(InvokeStmt stmt)
				    {

					 Value v = stmt.getInvokeExprBox().getValue();
					 if (v instanceof JInterfaceInvokeExpr){
						 JInterfaceInvokeExpr jv = (JInterfaceInvokeExpr) v;

						 if (jv.getMethodRef().getSignature().equals("<java.util.Map: java.lang.Object put(java.lang.Object,java.lang.Object)>")){

							 if (jv.getBaseBox().getValue() instanceof JimpleLocal){
								 JimpleLocal local = (JimpleLocal)jv.getBaseBox().getValue();
								 String localName = local.getName();
								 for (int i=0; i<jv.getArgCount(); i++){
									 ValueBox ab = jv.getArgBox(i);
									 if (ab.getValue() instanceof StringConstant){
										 String key = ((StringConstant)ab.getValue()).value;
										 LeakedData ld = new LeakedData();
										 ld.setApk(apkName);
										 ld.setClazz("java.util.Map");
										 ld.setName(key);
										 ld.setType("java.lang.String");

										 PreloadValue p = new PreloadValue();
										 p.localName = localName;
										 p.data = ld;
										 preloaded.add(p);
									 }
								 }
							 }
						 }
					 }
					
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
							 logger.debug("Class=" + b.getMethod().getDeclaringClass().getName() + " method="+ b.getMethod().getName());

							 ObjectExtractionQuery oe = extractionPoints.get(sig);
							 List<ValueBox> boxes = v.getUseBoxes(); //get from here
							 
							 ValueBox vb = boxes.get(oe.getPosition());
							 
							 if (vb != null){
								Value boxVal = vb.getValue();
								String c=null;
								if (boxVal instanceof JimpleLocal){
									String localName = ((JimpleLocal) boxVal).getName();
									 Type argType = vb.getValue().getType(); //Is this the class or a reference?
									 c = argType.toString();
									 if (c.equals("java.util.Map")){
										 processMap(localName, oe.isOutgoing(), preloaded); //This is the sink
										 preloaded.clear();
									 }
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
									 result.setOutgoing(oe.isOutgoing());
									 extractedObjects.add(result);
								}
							 }
						 }
					 
					 }
					 
					
			    }
				public void defaultCase(Object o){
					Stmt s = (Stmt)o;
					if (s.toString().contains("<java.util.Map: java.lang.Object put(java.lang.Object,java.lang.Object)>") ){
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
		if (PackManager.v().getPack("jtp").get("jtp.myInstrumenter") == null){
		
        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {

			@Override
			protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {

					SootMethod method = b.getMethod();
					List<PreloadValue> preloaded = new ArrayList<PreloadValue>();
					//logger.debug("Class=" + method.getDeclaringClass().getName() + " method="+ method.getName());
					final PatchingChain<Unit> units = b.getUnits();
					for(Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
						final Unit u = iter.next();
						analyzeUnit(u,b,preloaded);

					}
				}
		}));
		}
        PackManager.v().runPacks();
	}
}
