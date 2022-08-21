package nginx.clojure.jersey.spring.example;

import static org.junit.Assert.assertEquals;

import java.io.File;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import nginx.clojure.embed.NginxEmbedServer;

public class StudentResourceTest {

	WebTarget target;
	
	@Before
	public void setUp() throws Exception {
		String confPath = new File("nginx-work-dir/conf/nginx.conf").getAbsolutePath();
		System.setProperty("user.dir", new File("nginx-work-dir").getAbsolutePath());
		POSIX posix = POSIXFactory.getPOSIX();
		posix.chdir("nginx-work-dir");
		NginxEmbedServer.getServer().start(confPath);
		Client c = ClientBuilder.newClient();
		c.register(new JacksonFeature());
		target = c.target("http://localhost:8080/api");
	}

	@After
	public void tearDown() throws Exception {
		NginxEmbedServer.getServer().stop();
	}

	@Test
	public void testCreateAndFindStudent() {
		Response response = target.path("students").request("application/json")
        .post(Entity.entity(new Student(null, "Tom", "A"), MediaType.valueOf("application/json")));
		assertEquals(200, response.getStatus());
		Student s = (Student) response.readEntity(Student.class);
		assertEquals("Tom", s.getName());
		
		response = target.path("students").path(s.getId()).request("application/json").get();
		assertEquals(200, response.getStatus());
		assertEquals("A", response.readEntity(Student.class).getGrade());
	}

}
