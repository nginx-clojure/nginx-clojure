/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.wave;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import nginx.clojure.asm.ClassReader;
import nginx.clojure.wave.MethodDatabase.ClassEntry;

public class MethodDatabaseUtil {

	public MethodDatabaseUtil() {
	}
	
	//eg. load(db, "nginx/clojure/wave/coroutine-method-db.txt")
	public static void load(MethodDatabase db, String resource) throws IOException {
		InputStream in = null;
		try {
			in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
			BufferedReader r = new BufferedReader(new InputStreamReader(in));
			String l = null;
			ClassEntry ce = null;
			int lc = 0;
			while ((l = r.readLine()) != null) {
				lc++;
				l = l.trim();
				if (l.startsWith("class:")) {
					String clz = l.substring("class:".length());
					ce = db.getClasses().get(clz);
					if (ce == null) {
						ce = buildClassEntryFamily(db, clz);
						if (ce == null) {
							db.warn("file %s line %d : not found class: %s", resource , lc,  clz);
							ce = null;
							continue;
						}
					}
				}else if (l.length() == 0 || ce == null || l.charAt(0) == '#'){
					continue;
				}else if (l.startsWith("filter:")) {
					db.getFilters().add(l.substring("filter:".length()).trim());
				}else {
					String[] ma = l.split(":");
					String m = ma[0];
					Integer st = MethodDatabase.SUSPEND_NORMAL;
					if (ma.length > 1) {
						if (MethodDatabase.SUSPEND_NORMAL_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_NORMAL;
						}else if (MethodDatabase.SUSPEND_IGNORE_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_IGNORE;
						}else if (MethodDatabase.SUSPEND_NONE_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_NONE;
						}else if (MethodDatabase.SUSPEND_JUST_MARK_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_JUST_MARK;
						}else if (MethodDatabase.SUSPEND_BLOCKING_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_BLOCKING;
						}else if (MethodDatabase.SUSPEND_FAMILY_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_FAMILY;
						}else {
							db.warn("file %s line %d : unknown suspend type: %s , we just set to 'normal'", resource , lc, ma[1]);
							st = MethodDatabase.SUSPEND_NORMAL;
						}
					}
					if (m.charAt(0) == '/') { // regex pattern
						Pattern p = Pattern.compile(m.substring(1));
						boolean matched = false;
						for (Entry<String, Integer> me : ce.getMethods().entrySet()) {
							if (p.matcher(me.getKey()).find()) {
								me.setValue(st);
								matched = true;
							}
						}
						if (!matched) {
							db.warn("file %s line %d : none of methods matched regex: %s ,ignored", resource , lc, m);
						}
					}else if (ce.getMethods().get(m) == null) {
						db.warn("file %s line %d : unknown method: %s ,ignored", resource , lc, m);
						continue;
					}else {
						ce.set(ma[0], st);
					}
				}
			}
		}finally {
			if (in != null) {
				in.close();
			}
		}
		
		
	}
	
	public static ClassEntry buildClassEntryFamily(MethodDatabase db, ClassReader r) {
		ClassEntry ce = db.getClasses().get(r.getClassName());
		if (ce == null) {
			CheckInstrumentationVisitor civ = db.checkClass(r);
			return buildClassEntryFamily(db, civ);
		}
		return ce;
	}
	
	public static void mergeMethodsSuspendTypeFromSuper(ClassEntry ce, ClassEntry sce) {
		if (ce == null || sce == null) {
			return;
		}
		Map<String, Integer> sms = sce.getMethods();
		for (Entry<String, Integer> me : ce.getMethods().entrySet()) {
			Integer st = me.getValue();
			if (st == MethodDatabase.SUSPEND_NONE) {
				Integer sst = sms.get(me.getKey());
				if (sst != null && sst != MethodDatabase.SUSPEND_NONE) {
					me.setValue(sst);
				}
			}
		}
	}
	
	public static ClassEntry buildClassEntryFamily(MethodDatabase db, CheckInstrumentationVisitor civ) {
		ClassEntry ce = civ.getClassEntry();
		String superName = ce.getSuperName();
		db.recordSuspendableMethods(civ.getName(), ce);
		
		if (superName != null) {
			ClassEntry sce = db.getClasses().get(superName);
			if (sce == null) {
				CheckInstrumentationVisitor sciv = db.checkClass(superName);
				if (sciv == null) {
					db.error("super class %s can not visited", superName);
				}else {
					sce = buildClassEntryFamily(db, sciv);
					db.recordSuspendableMethods(superName, sce);
				}
			}
			
			mergeMethodsSuspendTypeFromSuper(ce, sce);
		}
		
		String[] interfaces = ce.getInterfaces();
		if (interfaces != null) {
			for (String itf : interfaces) {
				ClassEntry sce = db.getClasses().get(itf);
				if (sce == null) {
					CheckInstrumentationVisitor sciv = db.checkClass(itf);
					sce = buildClassEntryFamily(db, sciv);
					db.recordSuspendableMethods(itf, sce);
				}
				mergeMethodsSuspendTypeFromSuper(ce, sce);
			}
		}
		return ce;
	}
	
	public static ClassEntry buildClassEntryFamily(MethodDatabase db, String name) {
		ClassEntry ce = db.getClasses().get(name);
		if (ce == null) {
			CheckInstrumentationVisitor civ = db.checkClass(name);
			if (civ == null) {
				return null;
			}
			return buildClassEntryFamily(db, civ);
		}
		return ce;
	}


}
