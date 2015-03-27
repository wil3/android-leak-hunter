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
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
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
	
	public LeakHunter(Hashtable<String, ObjectExtractionQuery> extractionPoints){ 
		this.extractionPoints = extractionPoints;
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
	public void process(){
	
		try {
		Scene.v().loadNecessaryClasses();
		locateMethodCalls();
		for (ObjectExtractionResult r : extractedObjects){
			extractFieldsFromClassandStore(r);
		}
		} finally {
		closeDatabase();
		Scene.v().releaseActiveHierarchy();
		Scene.v().releaseCallGraph();
		Scene.v().releaseReachableMethods();
		Scene.v().releasePointsToAnalysis();
		Scene.v().releaseSideEffectAnalysis();
		G.reset();
		}
	}

	private void extractFieldsFromClassandStore(ObjectExtractionResult r){
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
			data.makeId();
			try {
				coll.insert(data);
			} catch (MongoException e){
				logger.warn(e.getMessage());
			}
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
									 result.setOutgoing(oe.isOutgoing());
									 extractedObjects.add(result);
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
		if (PackManager.v().getPack("jtp").get("jtp.myInstrumenter") == null){
		
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
		}
        PackManager.v().runPacks();
	}
}
