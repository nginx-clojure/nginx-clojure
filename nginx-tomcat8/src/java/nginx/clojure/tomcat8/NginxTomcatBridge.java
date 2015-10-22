/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.tomcat8;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Map;

import nginx.clojure.MiniConstants;
import nginx.clojure.NginxClojureRT;
import nginx.clojure.bridge.NginxBridge;
import nginx.clojure.java.NginxJavaRequest;

import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.security.SecurityClassLoad;
import org.apache.catalina.startup.Bootstrap;
import org.apache.catalina.startup.Catalina;
import org.apache.coyote.http11.NginxDirectProtocol;
import org.apache.coyote.http11.NginxEndpoint;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class NginxTomcatBridge extends Catalina implements NginxBridge {


	protected NginxDirectProtocol nginxDirectProtocol;
	
	protected static final org.apache.juli.logging.Log log =
	        org.apache.juli.logging.LogFactory.getLog( NginxTomcatBridge.class );
	
	protected boolean ignoreNginxFilter = false;
	
	protected boolean dispatch = false;
	
	protected ClassLoader bootLoader;
	
	protected void initWithoutStartServer() {

        initDirs();

        // Before digester - it may be needed
        initNaming();

        // Create and execute our Digester
        Digester digester = createStartDigester();

        InputSource inputSource = null;
        InputStream inputStream = null;
        File file = null;
        try {
            file = configFile();
            inputStream = new FileInputStream(file);
            inputSource = new InputSource(file.toURI().toURL().toString());
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("catalina.configFail", file), e);
            }
        }
        if (inputStream == null) {
            try {
                inputStream = getClass().getClassLoader()
                    .getResourceAsStream(getConfigFile());
                inputSource = new InputSource
                    (getClass().getClassLoader()
                     .getResource(getConfigFile()).toString());
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("catalina.configFail",
                            getConfigFile()), e);
                }
            }
        }

        // This should be included in catalina.jar
        // Alternative: don't bother with xml, just create it manually.
        if (inputStream == null) {
            try {
                inputStream = getClass().getClassLoader()
                        .getResourceAsStream("server-embed.xml");
                inputSource = new InputSource
                (getClass().getClassLoader()
                        .getResource("server-embed.xml").toString());
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("catalina.configFail",
                            "server-embed.xml"), e);
                }
            }
        }


        if (inputStream == null || inputSource == null) {
            if  (file == null) {
                log.warn(sm.getString("catalina.configFail",
                        getConfigFile() + "] or [server-embed.xml]"));
            } else {
                log.warn(sm.getString("catalina.configFail",
                        file.getAbsolutePath()));
                if (file.exists() && !file.canRead()) {
                    log.warn("Permissions incorrect, read permission is not allowed on the file.");
                }
            }
            return;
        }

        try {
            inputSource.setByteStream(inputStream);
            digester.push(this);
            digester.parse(inputSource);
        } catch (SAXParseException spe) {
            log.warn("Catalina.start using " + getConfigFile() + ": " +
                    spe.getMessage());
            return;
        } catch (Exception e) {
            log.warn("Catalina.start using " + getConfigFile() + ": " , e);
            return;
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                // Ignore
            }
        }

        getServer().setCatalina(this);
        getServer().setCatalinaHome(new File(Bootstrap.getCatalinaHome()));
        getServer().setCatalinaBase(new File(Bootstrap.getCatalinaBase()));

        // Stream redirection
        initStreams();
    
	}
	
	@Override
	public void boot(Map<String, String> properties, ClassLoader loader) {
		if (nginxDirectProtocol != null) {
			return;
		}
		
		bootLoader = loader;
        long t1 = System.nanoTime();
        
        /**
         * Hack for tomcat TomcatURLStreamHandlerFactory which uses 
         *        URL.setURLStreamHandlerFactory(this);
         * to register URLStreamHandlerFactory. But if we have two tomcat instances it will fail.
         */
		try {
			Field urlFactory = URL.class.getDeclaredField("factory");
			Object base = NginxClojureRT.UNSAFE.staticFieldBase(urlFactory);
			NginxClojureRT.UNSAFE.putObject(base, NginxClojureRT.UNSAFE.staticFieldOffset(urlFactory), null);
		} catch (Throwable e) {
			e.printStackTrace();
		} 
        
        String tomcatHome = System.getProperty(Globals.CATALINA_HOME_PROP);
        if (tomcatHome == null) {
        	NginxClojureRT.getLog().error("system." + Globals.CATALINA_HOME_PROP + " should be set!");
        	return;
        }
	    org.apache.juli.logging.Log log=
	            org.apache.juli.logging.LogFactory.getLog( Catalina.class );
	    log.info("tomcatHome=" + tomcatHome + ", conf=" + this.configFile);
		try {
			ClassLoader catalinaLoader = this.getClass().getClassLoader();
			Thread.currentThread().setContextClassLoader(catalinaLoader);
	        SecurityClassLoad.securityClassLoad(catalinaLoader);
			this.initWithoutStartServer();
			Field protocolHandlerField = Connector.class.getDeclaredField("protocolHandler");
			protocolHandlerField.setAccessible(true);
			Connector httpConnector = null;
			Service httpService = null;
			Server server = this.getServer();
			for (Service s : server.findServices()) {
				for (Connector c : s.findConnectors()) {
					if (c.getProtocol().equals("HTTP/1.1")) {
						httpConnector = c;
						httpService = s;
						nginxDirectProtocol = new NginxDirectProtocol();
						nginxDirectProtocol.setAdapter(c.getProtocolHandler().getAdapter());
						c.setProtocol(NginxDirectProtocol.class.getName());
						protocolHandlerField.set(c, nginxDirectProtocol);
					}else {
						s.removeConnector(c);
					}
				}
			}
			
			
	        try {
	            getServer().init();
	        } catch (LifecycleException e) {
	            if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE")) {
	                throw new java.lang.Error(e);
	            } else {
	                log.error("Catalina.start", e);
	            }
	        }
			
			this.start();
			nginxDirectProtocol = (NginxDirectProtocol) httpConnector.getProtocolHandler();
			
	        long t2 = System.nanoTime();
	        if(log.isInfoEnabled()) {
	            log.info("Initialization processed in " + ((t2 - t1) / 1000000) + " ms");
	        }
	        
	        String ignoreNginxFilterStr = properties.get("ignoreNginxFilter");
	        if (ignoreNginxFilterStr != null) {
	        	ignoreNginxFilter = Boolean.parseBoolean(ignoreNginxFilterStr);
	        }
	        
	        String dispatchStr = properties.get("dispatch");
	        if (dispatchStr != null) {
	        	dispatch = Boolean.parseBoolean(dispatchStr);
	        }
		} catch (Throwable t) {
			NginxClojureRT.getLog().error("can not start tomcat server", t);
			return;
		}
	}
	
	@Override
	public ClassLoader getClassLoader() {
		return bootLoader;
	}
	
	int i = 0;

	@Override
	public Object[] handle(NginxJavaRequest req) throws IOException {
		NginxEndpoint nginxEndpoint = nginxDirectProtocol.getEndpoint();
		req.get(MiniConstants.BODY);
		req.get(MiniConstants.REMOTE_ADDR);
		nginxEndpoint.accept(req, ignoreNginxFilter, dispatch);
		return null;
	}

}
