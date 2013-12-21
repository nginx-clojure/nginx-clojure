/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.nio.charset.Charset;

public   interface RequestVarFetcher {
	
	public Object fetch(long r, Charset encoding);

}

