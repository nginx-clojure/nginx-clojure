package nginx.clojure.jersey.spring.example;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Path("students")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StudentResource {

	@Autowired
	private StudentService studentService;

	public StudentResource() {
	}

	@POST
	public Student create(Student student) {
		return studentService.save(student);
	}

	@PUT
	@Path("{id}")
	public Student update(@PathParam("id") String id, Student student) {
		if (studentService.find(student.getId()) == null) {
			return null;
		}
		return studentService.save(student);
	}

	@GET
	@Path("{id}")
	public Student find(@PathParam("id") String id) {
		return studentService.find(id);
	}

	@DELETE
	@Path("{id}")
	public Response delete(@PathParam("id") String id) {
		if (studentService.remove(id) == null) {
			return Response.status(302).build();
		} else {
			return Response.status(201).build();
		}
	}

}
