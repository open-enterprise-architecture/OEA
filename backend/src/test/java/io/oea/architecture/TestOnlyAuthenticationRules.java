package io.oea.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

/**
 * Build-time checks of ADR-036 §7 against test-only authentication code. Package names are
 * parameters so the rules can be checked against fixtures.
 */
final class TestOnlyAuthenticationRules {

	/** Packages main code must never depend on. */
	static final String[] TEST_ONLY_PACKAGES = { "org.testcontainers..", "org.keycloak..", "dasniko..",
			"io.oea.testsupport..", "org.springframework.security.test.." };

	static final String STUB_NAME_PATTERN = ".*(Stub|Fake|Mock|Dummy|Test)(Jwt|Token|Auth|Idp|Issuer|Login).*";

	/** Security beans that must never depend on a Spring profile. */
	static final Set<String> SECURITY_BEAN_TYPES = Set.of("SecurityFilterChain", "JwtDecoder", "AuthenticationProvider",
			"AuthenticationManager", "OAuth2AuthorizedClientService");

	private static final String PROFILE = "org.springframework.context.annotation.Profile";

	private static final String BEAN = "org.springframework.context.annotation.Bean";

	private TestOnlyAuthenticationRules() {
	}

	static ArchRule mainCodeDoesNotDependOnTestOnlyLibraries(String... forbiddenPackages) {
		return noClasses().should()
			.dependOnClassesThat()
			.resideInAnyPackage(forbiddenPackages)
			.because("ADR-036 §7: no test-only authentication code in the production artifact");
	}

	static ArchRule mainCodeHasNoStubClassNames() {
		return noClasses().should()
			.haveNameMatching(STUB_NAME_PATTERN)
			.because("ADR-036 §7: token or login stubs must not exist in main code");
	}

	static ArchRule securityBeansAreNotProfileConditional() {
		return methods().that()
			.areAnnotatedWith(BEAN)
			.and(returnTypeNamedIn(SECURITY_BEAN_TYPES))
			.should(notBeProfileConditional())
			.allowEmptyShould(true)
			.because("ADR-036 §7: security configuration must be identical in every profile");
	}

	static ArchRule integrationTestsDoNotUseMockAuthentication(String stepsPackage, String... mockAuthPackages) {
		return classes().that(areIntegrationTests(stepsPackage))
			.should()
			.onlyDependOnClassesThat(DescribedPredicate.not(residesInAny(mockAuthPackages)))
			.allowEmptyShould(true)
			.because("ADR-036 §5: integration tests and steps authenticate through the real identity provider");
	}

	private static DescribedPredicate<JavaMethod> returnTypeNamedIn(Set<String> simpleNames) {
		return DescribedPredicate.describe("return a security bean type " + simpleNames,
				(method) -> simpleNames.contains(method.getRawReturnType().getSimpleName()));
	}

	private static ArchCondition<JavaMethod> notBeProfileConditional() {
		return new ArchCondition<>("not be annotated with @Profile (directly or on the declaring class)") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				boolean conditional = method.isAnnotatedWith(PROFILE) || method.getOwner().isAnnotatedWith(PROFILE);
				events.add(new SimpleConditionEvent(method, !conditional,
						method.getFullName() + " is a profile-conditional security bean"));
			}
		};
	}

	private static DescribedPredicate<JavaClass> areIntegrationTests(String stepsPackage) {
		return DescribedPredicate.describe("are @SpringBootTest classes or step definitions",
				(javaClass) -> javaClass.isAnnotatedWith("org.springframework.boot.test.context.SpringBootTest")
						|| javaClass.getPackageName().startsWith(stepsPackage));
	}

	private static DescribedPredicate<JavaClass> residesInAny(String... packagePrefixes) {
		return DescribedPredicate.describe("reside in " + String.join(", ", packagePrefixes), (javaClass) -> {
			for (String prefix : packagePrefixes) {
				if (javaClass.getPackageName().startsWith(prefix)) {
					return true;
				}
			}
			return false;
		});
	}

}
