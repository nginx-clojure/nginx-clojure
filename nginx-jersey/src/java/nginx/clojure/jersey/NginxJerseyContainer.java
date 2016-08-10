package nginx.clojure.jersey;

import static nginx.clojure.MiniConstants.BODY;
import static nginx.clojure.MiniConstants.HEADERS;
import static nginx.clojure.MiniConstants.NGX_HTTP_INTERNAL_SERVER_ERROR;
import static nginx.clojure.MiniConstants.REQUEST_METHOD;
import static nginx.clojure.MiniConstants.SCHEME;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.SecurityContext;

import nginx.clojure.NginxClojureRT;
import nginx.clojure.NginxHttpServerChannel;
import nginx.clojure.bridge.NginxBridge;
import nginx.clojure.java.NginxJavaRequest;

import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;

public class NginxJerseyContainer implements NginxBridge {

	protected ApplicationHandler appHandler;
	protected String appPath;
	protected Class[] appResources;
	protected ClassLoader bootLoader;
	
    protected ResourceConfig configure() {
        return new ResourceConfig(appResources);
    }

	@Override
	public void boot(Map<String, String> properties, ClassLoader loader) {
		appPath = properties.get("jersey.app.path");
		bootLoader = loader;
		if (appPath == null) {
			appPath = "";
		}
		
		String application = properties.get("jersey.app.Appclass");
		if (application != null) {
			Class appClz = null;
			try {
				appClz = loader.loadClass(application.trim());
			} catch (ClassNotFoundException e) {
				NginxClojureRT.log.warn("can not load jersey.app.Appclass %s", application, e);
				throw new RuntimeException("can not load jersey.app.Appclass " + e.getMessage(), e);
			}
			try {
				appHandler = new ApplicationHandler((Class<? extends Application>) appClz.newInstance());
			} catch (Exception e) {
				throw new RuntimeException("can not create jersey.app.Appclass" + e.getMessage(), e);
			} 
		}else {
			String res = properties.get("jersey.app.resources");
			List<Class> clzList = new ArrayList<Class>();
			if (res != null) {
				for (String clz : res.split(",") ) {
					try {
						clzList.add(loader.loadClass(clz.trim()));
					} catch (Throwable e) {
						NginxClojureRT.log.warn("can not load resource %s, skiping", clz, e);
					}
				}
				appResources = clzList.toArray(new Class[clzList.size()]);
			}
			
			if (appResources == null || appResources.length == 0){
				NginxClojureRT.log.warn("no resource defined, property %s is null", "jersey.app.resources");
			}
			appHandler = new ApplicationHandler(configure());
		}
		
	}
	
	@Override
	public ClassLoader getClassLoader() {
		return bootLoader;
	}

    protected SecurityContext getSecurityContext(final Principal principal, final boolean isSecure) {
        return new SecurityContext() {

            @Override
            public boolean isUserInRole(final String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return isSecure;
            }

            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public String getAuthenticationScheme() {
                return null;
            }
        };
    }
	
    public static class NginxReponseWriter implements ContainerResponseWriter {

		protected boolean suspend = false;
		protected NginxHttpServerChannel sc;
		
		public NginxReponseWriter() {
		}
		
		public NginxReponseWriter(NginxHttpServerChannel sc) {
			this.sc = sc;
		}
		
		@Override
		public void commit() {
			try {
				sc.close();
			} catch (IOException e) {
				NginxClojureRT.log.error("commit failure!", e);
			}
		}

		@Override
		public boolean enableResponseBuffering() {
			return true;
		}

		@Override
		public void failure(Throwable e) {
			NginxClojureRT.log.error("failure from jersey", e);
			try {
				sc.sendResponse(NGX_HTTP_INTERNAL_SERVER_ERROR);
			} catch (IOException e1) {
				NginxClojureRT.log.error("send error response failed!", e);
			}
		}

		@Override
		public void setSuspendTimeout(long timeOut, TimeUnit timeUnit)
				throws IllegalStateException {
		}

		@Override
		public boolean suspend(long timeOut, TimeUnit timeUnit, TimeoutHandler timeoutHandler) {
			return suspend = true;
		}

		@Override
		public OutputStream writeResponseStatusAndHeaders(long contentLength,
				ContainerResponse context) throws ContainerException {
			try {
				sc.sendHeader(context.getStatus(), context.getStringHeaders().entrySet(), true, false);
			} catch (IOException e) {
				throw new ContainerException("send header error!", e);
			}
			return new OutputStream() {
				
				@Override
				public void write(int b) throws IOException {
					write(new byte[]{(byte)b}, 0, 1);
				}
				
				@Override
				public void write(byte[] b, int off, int len)
						throws IOException {
					if (b == null) {
						throw new NullPointerException("byte[] can not be null");
					}
					if (off + len > b.length) {
						throw new IndexOutOfBoundsException("buffer space is too small, off + len > b.length");
					}
					sc.send(b, off, len, false, false);
				}
				
				@Override
				public void flush() throws IOException {
					sc.flush();
				}
				
				@Override
				public void close() throws IOException {
					sc.close();
				}
			};
		}
		
    }
    
	@Override
	public Object[] handle(NginxJavaRequest req) throws IOException {
		final NginxHttpServerChannel sc = req.hijack(false);
		Map<String, Object> headers = (Map<String, Object>) req.get(HEADERS);
		URI baseUri;
		try {
			baseUri = new URI(new StringBuilder((String) req.get(SCHEME))
					.append("://").append(headers.get("host")).append(appPath)
					.append(appPath.endsWith("/") ? "" : "/").toString());
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
		URI requestUri = baseUri.resolve(req.getVariable("request_uri"));
		
		ContainerRequest cr = new ContainerRequest(baseUri, requestUri,
				((String)req.get(REQUEST_METHOD)).toUpperCase(), getSecurityContext(null, false),
				new MapPropertiesDelegate());
		cr.setEntityStream((InputStream)req.get(BODY));
		MultivaluedMap<String, String> crHeaders = cr.getHeaders();
		for (Entry<String, Object> en : headers.entrySet()) {
			Object v = en.getValue();
			if (v == null) {
				continue;
			}
			if (v.getClass().isArray()) {
				crHeaders.put(en.getKey(), Arrays.asList((String[])v));
			}else {
				crHeaders.putSingle(en.getKey(), (String)v);
			}
		}
		
		NginxReponseWriter writer = new NginxReponseWriter(sc);
		cr.setWriter(writer);
		appHandler.handle(cr);
		
		if (!writer.suspend) {
			sc.close();
		}
		return null;
	}
}
