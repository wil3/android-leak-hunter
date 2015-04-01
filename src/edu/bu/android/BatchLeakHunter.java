package edu.bu.android;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.mongojack.JacksonDBCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

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
import edu.bu.android.model.LeakedData;
import edu.bu.android.model.ObjectExtractionQuery;
import edu.bu.android.model.ObjectExtractionResult;


public class BatchLeakHunter {

	final static Logger logger = LoggerFactory.getLogger(BatchLeakHunter.class);
	
	private File apkSourceDir;
	private File foundDir;
	private File lostDir;
	private String androidJars;
	private String signaturePath;
	Hashtable<String,ObjectExtractionQuery> signatures;
	public BatchLeakHunter(File apkSourceDir, File processedDir, File lostDir, String androidJars, String signaturePath){
		this.apkSourceDir = apkSourceDir;
		this.foundDir = processedDir;
		this.lostDir = lostDir;
		this.androidJars = androidJars;
		this.signaturePath = signaturePath;
		setup();
	}

	private void setup(){
		this.signatures = loadSignatures(signaturePath);
	}
	
	public void run(){
		
		File[] files = apkSourceDir.listFiles();
		for (int i=0;i<files.length;i++){
			Options.v().set_android_jars(androidJars);
			
			Options.v().set_allow_phantom_refs(true);
			//prefer Android APK files// -src-prec apk
			Options.v().set_src_prec(Options.src_prec_apk);
			
			String apkPath = files[i].getAbsolutePath();
			logger.info("Processing " + apkPath);
			String apkName = files[i].getName();
			List<String> processDirs = new ArrayList<String>();
			processDirs.add(apkPath);
			Options.v().set_process_dir(processDirs);

			LeakHunter hunter = new LeakHunter(apkName, signatures);
			int numProcessed = hunter.process();
			
			try {
				
				if (numProcessed > 0) {
					FileUtils.moveFileToDirectory(files[i], foundDir, false);
				} else {
					FileUtils.moveFileToDirectory(files[i], lostDir, false);
				}
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
		}
	}
	
	
	private Hashtable<String,ObjectExtractionQuery> loadSignatures(String path){
		Hashtable<String, ObjectExtractionQuery> l = new Hashtable<String,ObjectExtractionQuery>();
		
		File file = new File(path);		
		
		try {
			ObjectMapper mapper = new ObjectMapper();

			List<String> lines = FileUtils.readLines(file);
			for (String line : lines){
				if (line.startsWith("#") || line.length()==0){
					continue;
				}
				ObjectExtractionQuery oe = mapper.readValue(line, ObjectExtractionQuery.class);
				logger.debug("Found outgoing=" + oe.isOutgoing() + " signature=" + oe.getSignature() + " position=" + oe.getPosition());
				l.put(oe.getSignature(), oe);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return l;
	}
	
/**
 * @param args
 */
	public static void main(String[] args) {
		//TODO need error checking, messy
		String apkSourceDirPath = args[0];
		File apkSourceDir = new File(apkSourceDirPath);
		
		String proccessedDirPath = args[1];
		File foundDir = new File(proccessedDirPath);
		File lostDir = new File(args[2]);
		
		String androidJars = args[3];
		String signaturePath = args[4];

		BatchLeakHunter instra = new BatchLeakHunter(apkSourceDir, foundDir, lostDir, androidJars, signaturePath);
		instra.run();
	}
}
