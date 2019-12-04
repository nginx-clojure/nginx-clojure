package nginx.clojure.jersey.spring.example;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class StudentService {

	private AtomicInteger idCounter = new AtomicInteger();
	private ConcurrentHashMap<String, Student> studentStore = new ConcurrentHashMap<>();
	
	public StudentService() {
	}
	
	public Student save(Student student) {
		if (student.getId() == null) {
			student.setId(idCounter.incrementAndGet()+"");
		}
		studentStore.put(student.getId(), student);
		return student;
	}
	
	public Student find(String id) {
		return studentStore.get(id);
	}
	
	public Student remove(String id) {
		return studentStore.remove(id);
	}

}
