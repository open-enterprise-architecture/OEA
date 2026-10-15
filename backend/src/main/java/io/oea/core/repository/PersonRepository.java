package io.oea.core.repository;

import io.oea.core.domain.person.Person;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

	boolean existsByEmailIgnoreCase(String email);

	List<Person> findAllByOrderById();

}
