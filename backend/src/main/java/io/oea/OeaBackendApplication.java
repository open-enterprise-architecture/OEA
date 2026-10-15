package io.oea;

import io.oea.config.migration.MigrateCommand;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OeaBackendApplication {

	/**
	 * Entry point. {@code migrate} as first argument runs the one-shot migration step and
	 * exits; {@code serve} or no command starts the application.
	 */
	public static void main(String[] args) {
		String command = (args.length > 0 && !args[0].startsWith("-")) ? args[0] : "serve";
		String[] options = (args.length > 0 && !args[0].startsWith("-")) ? Arrays.copyOfRange(args, 1, args.length)
				: args;
		switch (command) {
			case "migrate" -> System.exit(MigrateCommand.run(options));
			case "serve" -> SpringApplication.run(OeaBackendApplication.class, options);
			default -> {
				System.err.println("Unknown command '" + command + "'; use 'serve' or 'migrate'");
				System.exit(2);
			}
		}
	}

}
