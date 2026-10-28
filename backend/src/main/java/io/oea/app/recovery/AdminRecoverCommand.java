package io.oea.app.recovery;

import io.oea.audit.AuditWriter;
import io.oea.config.secrets.SecretCipher;
import io.oea.config.secrets.SecretsConfiguration;
import io.oea.config.startup.ConfigurationGuards;
import io.oea.core.service.PasswordHasher;
import io.oea.core.service.TotpAlgorithm;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code oea admin recover} (UC-02 E4, REQ-173 AC4; design D5 of us-019-admin-safeguards):
 * restores the local system admin account with the runtime configuration of the backend
 * container ({@code spring.datasource.*}, key-encryption key). The password and the TOTP code
 * are read from the terminal, never from the command line. Exit codes: 0 recovered, 1 refused
 * (nothing changed), 2 usage or connection error.
 */
public final class AdminRecoverCommand {

	static final int CODE_ATTEMPTS = 3;

	/** Reading from the operator; a {@code null} answer means the input ended. */
	interface Terminal {

		String readSecret(String prompt);

		String readLine(String prompt);

		void println(String line);

	}

	private AdminRecoverCommand() {
	}

	public static int run(String... args) {
		Console console = System.console();
		return run((console != null) ? consoleTerminal(console) : streamTerminal(
				new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out), args);
	}

	static int run(Terminal terminal, String... args) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(RecoverConfiguration.class)
			.web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.logStartupInfo(false)
			.properties(ConfigurationGuards.MODE_PROPERTY + "=admin-recover")
			.run(args)) {
			AdminRecovery recovery = new AdminRecovery(context.getBean(JdbcTemplate.class),
					new TransactionTemplate(context.getBean(PlatformTransactionManager.class)),
					context.getBean(SecretCipher.class), new PasswordHasher(),
					new AuditWriter(context.getBean(JdbcTemplate.class)), Clock.systemUTC());
			return recover(recovery, terminal, Clock.systemUTC());
		}
		catch (Exception ex) {
			terminal.println("ERROR " + ex.getMessage());
			return 2;
		}
	}

	static int recover(AdminRecovery recovery, Terminal terminal, Clock clock) {
		Optional<AdminRecovery.Account> account = recovery.account();
		if (account.isEmpty()) {
			terminal.println("ERROR The instance is not bootstrapped: there is no system admin account to recover. "
					+ "Complete the setup at /setup with the setup token instead; nothing was changed.");
			return 1;
		}
		terminal.println("Recovering the local system admin account '" + account.get().loginName() + "' (status "
				+ account.get().status() + ").");
		String password = readPassword(terminal);
		if (password == null) {
			terminal.println("ERROR Input ended; nothing was changed.");
			return 1;
		}
		AdminRecovery.NewFactor factor = null;
		if (recovery.setupPhaseEnded()) {
			factor = enrollFactor(account.get().loginName(), terminal, clock);
			if (factor == null) {
				terminal.println("ERROR The second factor was not confirmed; nothing was changed.");
				return 1;
			}
		}
		int revoked = recovery.recover(account.get(), password, factor);
		terminal.println("OK The account '" + account.get().loginName() + "' can log in again"
				+ ((factor != null) ? " with the new password and the new authenticator" : " with the new password")
				+ "; " + revoked + " session(s) revoked. Clear this terminal.");
		return 0;
	}

	/** Asks until two equal passwords of 12 to 128 characters are entered; {@code null} if the input ends. */
	private static String readPassword(Terminal terminal) {
		while (true) {
			String password = terminal.readSecret("New password (12 to 128 characters): ");
			if (password == null) {
				return null;
			}
			if (password.length() < PasswordHasher.MINIMUM_LENGTH || password.length() > PasswordHasher.MAXIMUM_LENGTH) {
				terminal.println("The password must have 12 to 128 characters.");
				continue;
			}
			String repeated = terminal.readSecret("Repeat the password: ");
			if (repeated == null) {
				return null;
			}
			if (!password.equals(repeated)) {
				terminal.println("The passwords differ.");
				continue;
			}
			return password;
		}
	}

	/** Shows a new secret once and accepts it after a valid code; {@code null} after three wrong codes. */
	private static AdminRecovery.NewFactor enrollFactor(String loginName, Terminal terminal, Clock clock) {
		String secret = TotpAlgorithm.newSecret();
		terminal.println("The setup phase has ended, so the second factor is replaced as well.");
		terminal.println("Add this secret to your authenticator app: " + secret);
		terminal.println("otpauth://totp/" + URLEncoder.encode("OEA:" + loginName, StandardCharsets.UTF_8)
			.replace("+", "%20") + "?secret=" + secret + "&issuer=OEA&algorithm=SHA1&digits=6&period="
				+ TotpAlgorithm.PERIOD_SECONDS);
		for (int attempt = 1; attempt <= CODE_ATTEMPTS; attempt++) {
			String code = terminal.readLine("Code from the authenticator app: ");
			if (code == null) {
				return null;
			}
			OptionalLong step = TotpAlgorithm.matchingStep(secret, code.strip(), clock.instant());
			if (step.isPresent()) {
				return new AdminRecovery.NewFactor(secret, step.getAsLong());
			}
			terminal.println("The code is not valid.");
		}
		return null;
	}

	private static Terminal consoleTerminal(Console console) {
		return new Terminal() {

			@Override
			public String readSecret(String prompt) {
				char[] value = console.readPassword("%s", prompt);
				return (value != null) ? new String(value) : null;
			}

			@Override
			public String readLine(String prompt) {
				return console.readLine("%s", prompt);
			}

			@Override
			public void println(String line) {
				console.printf("%s%n", line);
			}

		};
	}

	/** Without a console (e.g. {@code docker compose exec -T}): one answer per line of standard input. */
	static Terminal streamTerminal(BufferedReader in, PrintStream out) {
		return new Terminal() {

			@Override
			public String readSecret(String prompt) {
				return readLine(prompt);
			}

			@Override
			public String readLine(String prompt) {
				out.print(prompt);
				out.flush();
				try {
					String line = in.readLine();
					out.println();
					return line;
				}
				catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			}

			@Override
			public void println(String line) {
				out.println(line);
			}

		};
	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
			DataSourceTransactionManagerAutoConfiguration.class })
	@Import(SecretsConfiguration.class)
	static class RecoverConfiguration {

	}

}
