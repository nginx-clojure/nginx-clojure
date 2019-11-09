/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure.net;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import nginx.clojure.Coroutine;
import nginx.clojure.SuspendExecution;
import nginx.clojure.java.NginxJavaRingHandler;

public class MySQLJdbcConroutineTestSet4JavaHandler implements NginxJavaRingHandler {

	
	public MySQLJdbcConroutineTestSet4JavaHandler() {
		try {
            // The newInstance() call is a work around for some
            // broken Java implementations

            Class.forName("com.mysql.jdbc.Driver").newInstance();
        } catch (Exception ex) {
            // handle the error
        }
	}
	
	private Connection createConnection() throws SQLException, SuspendExecution {
		try {
		    Connection conn =
		       DriverManager.getConnection("jdbc:mysql://mysql-0:3306/nctest", "nginxclojure", "111111");

		    // Do something with the Connection

		   return conn;
		} catch (SQLException ex) {
		    // handle any errors
		    System.out.println("SQLException: " + ex.getMessage());
		    System.out.println("SQLState: " + ex.getSQLState());
		    System.out.println("VendorError: " + ex.getErrorCode());
		    throw ex;
		}
	}

	/* (non-Javadoc)
	 * @see nginx.clojure.java.NginxJavaRingHandler#invoke(java.util.Map)
	 */
	@Override
	public Object[] invoke(Map<String, Object> request) throws IOException, SuspendExecution {
		
		try (Connection conn = createConnection(); 
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select 1");) {
			rs.next();
			return new Object[] {200, null, "Good, MySQL! Result:" + rs.getInt(1)};
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}
	
	public static class JdbcJavaRunner implements Runnable {
		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run() throws SuspendExecution {
			MySQLJdbcConroutineTestSet4JavaHandler h = new MySQLJdbcConroutineTestSet4JavaHandler();
			try {
				h.invoke(null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String[] args) {
		Coroutine co = new Coroutine(new JdbcJavaRunner());
		co.resume();
	}

}
