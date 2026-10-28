package io.oea;

import io.oea.app.recovery.AdminRecoverCommand;
import io.oea.audit.verify.AuditVerifyCommand;
import io.oea.config.migration.MigrateCommand;
import io.oea.config.secrets.ReencryptCommand;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OeaBackendApplication {

	/**
	 * Entry point. {@code migrate} as first argument runs the one-shot migration step and
	 * exits; {@code audit verify} verifies the audit hash chain and exits; {@code secrets
	 * reencrypt} re-encrypts stored secrets with the current key and exits; {@code admin recover}
	 * restores the local system admin account after a complete lockout and exits; {@code serve} or no
	 * command starts the application.
	 */
	public static void main(String[] args) {
		String command = (args.length > 0 && !args[0].startsWith("-")) ? args[0] : "serve";
		String[] options = (args.length > 0 && !args[0].startsWith("-")) ? Arrays.copyOfRange(args, 1, args.length)
				: args;
		switch (command) {
			case "migrate" -> System.exit(MigrateCommand.run(options));
			case "audit" -> {
				if (options.length == 0 || !"verify".equals(options[0])) {
					System.err.println("Usage: audit verify [--checkpoints=<file>]");
					System.exit(2);
				}
				System.exit(AuditVerifyCommand.run(Arrays.copyOfRange(options, 1, options.length)));
			}
			case "secrets" -> {
				if (options.length == 0 || !"reencrypt".equals(options[0])) {
					System.err.println("Usage: secrets reencrypt");
					System.exit(2);
				}
				System.exit(ReencryptCommand.run(Arrays.copyOfRange(options, 1, options.length)));
			}
			case "admin" -> {
				if (options.length == 0 || !"recover".equals(options[0])) {
					System.err.println("Usage: admin recover");
					System.exit(2);
				}
				System.exit(AdminRecoverCommand.run(Arrays.copyOfRange(options, 1, options.length)));
			}
			case "serve" -> SpringApplication.run(OeaBackendApplication.class, options);
			default -> {
				System.err.println("Unknown command '" + command + "'; use 'serve', 'migrate', 'audit verify', 'secrets reencrypt' or 'admin recover'");
				System.exit(2);
			}
		}
	}

}
