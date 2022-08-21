/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.wave;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nginx.clojure.asm.ClassReader;
import nginx.clojure.asm.tree.analysis.Analyzer;
import nginx.clojure.wave.MethodDatabase.ClassEntry;
import nginx.clojure.wave.MethodDatabase.FuzzyLazyClassEntry;
import nginx.clojure.wave.MethodDatabase.LazyClassEntry;

public class MethodDatabaseUtil {

	public final static Pattern FUZZY_CLASS_PATTERN = Pattern.compile("(\\d+)");

	public MethodDatabaseUtil() {
	}
	
	//eg. load(db, "nginx/clojure/wave/coroutine-method-db.txt")
	public static void load(MethodDatabase db, String resource) throws IOException {
		InputStream in = null;
		try {
			in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
			if (in == null) {
				throw new IOException("can not load resource [" + resource + "] from classpath");
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, MethodDatabase.UTF_8));
			db.getUserDefinedWaveConfigFiles().add(resource);
			String l = null;
			ClassEntry ce = null;
			LazyClassEntry lce = null;
			FuzzyLazyClassEntry flce = null;
			int lc = 0;
			String clz = null;
			while ((l = r.readLine()) != null) {
				lc++;
				l = l.trim();
				if (l.startsWith("class:")) {
					lce = null;
					flce = null;
					clz = l.substring("class:".length());
					ce = db.getClasses().get(clz);
					if (ce == null) {
						ce = buildClassEntryFamily(db, clz);
						if (ce == null) {
							db.warn("file %s line %d : not found class: %s", resource , lc,  clz);
							ce = null;
							continue;
						}
					}
				}else if (l.startsWith("lazyclass:")) {
					clz = l.substring("lazyclass:".length());
					ce = null;
					flce = null;
					lce = db.getLazyClasses().get(clz);
					if (lce == null) {
						db.getLazyClasses().put(clz, lce = new LazyClassEntry(resource));
					}
				}else if (l.startsWith("fuzzyclass:")) {
					clz = l.substring("fuzzyclass:".length());
					ce = null;
					lce = null;
					flce = new FuzzyLazyClassEntry(Pattern.compile(clz), resource);
					db.getFuzzlyLazyClasses().add(flce);
				}else if (l.startsWith("retransform:")) {
					db.getRetransformedClasses().add(l.substring("retransform:".length()).trim());
					ce = null;
					lce = null;
					flce = null;
				}else if (l.startsWith("filter:")) {
					db.getFilters().add(l.substring("filter:".length()).trim());
					ce = null;
					lce = null;
					flce = null;
				}else if (l.length() == 0 || (ce == null && lce == null && flce == null) || l.charAt(0) == '#'){
					continue;
				}else {
					String[] ma = l.split(":");
					String m = ma[0];
					Integer st = MethodDatabase.SUSPEND_NORMAL;
					if (ma.length > 1) {
						if (MethodDatabase.SUSPEND_NORMAL_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_NORMAL;
						}else if (MethodDatabase.SUSPEND_NONE_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_NONE;
						}else if (MethodDatabase.SUSPEND_JUST_MARK_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_JUST_MARK;
						}else if (MethodDatabase.SUSPEND_BLOCKING_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_BLOCKING;
						}else if (MethodDatabase.SUSPEND_FAMILY_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_FAMILY;
						}else if (MethodDatabase.SUSPEND_SKIP_STR.equals(ma[1])) {
							st = MethodDatabase.SUSPEND_SKIP;
						}else {
							db.warn("file %s line %d : unknown suspend type: %s , we just set to 'normal'", resource , lc, ma[1]);
							st = MethodDatabase.SUSPEND_NORMAL;
						}
					}
					if (lce != null) {
						Map<String, Integer> methods = lce.getMethods();
						Integer ost = methods.get(m);
						if (ost == null || st.intValue() > ost.intValue()) {
							methods.put(m, st);
						}else {
							st = ost;
						}
						if (db.meetTraceTargetClassMethod(clz, m)) {
							db.info("meet traced method %s.%s, suspend type = %s", clz, m, MethodDatabase.SUSPEND_TYPE_STRS[st]);
						}
						continue;
					}else if (flce != null) {
						Map<String, Integer> methods = flce.getMethods();
						Integer ost = methods.get(m);
						if (ost == null || st.intValue() > ost.intValue()) {
							methods.put(m, st);
						}else {
							st = ost;
						}
						if (db.meetTraceTargetClassMethod(clz, m)) {
							db.info("meet traced method %s.%s, suspend type = %s", clz, m, MethodDatabase.SUSPEND_TYPE_STRS[st]);
						}
						continue;
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
						Integer ost = ce.getMethods().get(m);
						if (ost == null || st.intValue() > ost.intValue()) {
							ce.set(m, st);
						}else {
							st = ost;
						}
						if (db.meetTraceTargetClassMethod(clz, m)) {
							db.info("meet traced method %s.%s, suspend type = %s", clz, m, MethodDatabase.SUSPEND_TYPE_STRS[st]);
						}
					}
				}
			}
			if (db.isDebugEnabled()) {
				MethodDatabase.getLog().debug("total filters:" + db.getFilters());
			}
		}finally {
			if (in != null) {
				in.close();
			}
		}
		
		
	}
	
	public static String toFuzzyString(Pattern p, String s, String rep) {
		Matcher m = p.matcher(s);
		int start = 0;
		StringBuilder rt = new StringBuilder();
		while (m.find()) {
			rt.append(Matcher.quoteReplacement(s.substring(start, m.start())));
			rt.append(rep);
			start = m.end();
		}
		if (rt.length() == 0){
			return null;
		}
		if (start == 0) {
			return null;
		}
		if (start < s.length()) {
			rt.append(Matcher.quoteReplacement(s.substring(start)));
		}
		return rt.toString();
	}
	
	public static ClassEntry buildClassEntryFamily(MethodDatabase db, ClassReader r) {
		ClassEntry ce = db.getClasses().get(r.getClassName());
		if (ce == null) {
			CheckInstrumentationVisitor civ = db.checkClass(r);
			return buildClassEntryFamily(db, civ);
		}
		return ce;
	}
	
	@SuppressWarnings("rawtypes")
	public static Analyzer buildAnalyzer(MethodDatabase db) {
		 return new TypeAnalyzer(new TypeInterpreter(db));
//		SimpleVerifier sv = new TypeInterpreter(db);//new SimpleVerifier();
//		sv.setClassLoader(Thread.currentThread().getContextClassLoader());
//		return new TypeAnalyzer(sv);
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
		String clz = civ.getName();
		LazyClassEntry lce = db.getLazyClasses().get(clz);
		
		if (lce != null) {
			db.debug("rebuild wave info for lazy class %s", clz);
			for (Entry<String, Integer> lme : lce.getMethods().entrySet()) {
				String m = lme.getKey();
				Integer st = lme.getValue();
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
						db.warn("file %s lazy class %s: none of methods matched regex: %s ,ignored", lce.getResource() , clz, m);
					}
				}else if (ce.getMethods().get(m) == null) {
					db.warn("file %s lazy class %s:  : unknown method: %s ,ignored", lce.getResource() , clz, m);
					continue;
				}else {
					Integer ost = ce.getMethods().get(m);
					if (ost == null || st.intValue() > ost.intValue()) {
						ce.set(m, st);
					}
				}
			}
		}
		
		if (FUZZY_CLASS_PATTERN.matcher(clz).find()) {
			for (FuzzyLazyClassEntry flce : db.getFuzzlyLazyClasses()) {
				if (flce.getPattern().matcher(clz).find()) {
					db.debug("rebuild wave info for fuzzylazy class %s, pattern %s", clz, flce.getPattern().toString());
					for (Entry<String, Integer> lme : flce.getMethods().entrySet()) {
						String m = lme.getKey();
						Integer st = lme.getValue();
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
								db.warn("file %s fuzzylazy class %s: none of methods matched regex: %s ,ignored", flce.getResource() , clz, m);
							}
						}else if (ce.getMethods().get(m) == null) {
							db.warn("file %s fuzzylazy class %s:  : unknown method: %s ,ignored", flce.getResource() , clz, m);
							continue;
						}else {
							Integer ost = ce.getMethods().get(m);
							if (ost == null || st.intValue() > ost.intValue()) {
								ce.set(m, st);
							}
						}
					}
				}
			}
		}
		
		String superName = ce.getSuperName();
		db.recordSuspendableMethods(clz, ce);
		
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
					if (sciv == null) {
						return null;
					}
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
