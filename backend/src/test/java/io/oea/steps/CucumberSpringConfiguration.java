package io.oea.steps;

import io.oea.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Application context shared by all step definitions: the real application on the test database. */
@CucumberContextConfiguration
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class CucumberSpringConfiguration {

}
