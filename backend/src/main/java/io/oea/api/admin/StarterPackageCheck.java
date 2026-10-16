package io.oea.api.admin;

import io.oea.app.metamodel.PackageValidator;
import io.oea.app.metamodel.StarterPackages;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Every shipped starter package must be importable (design D6): a broken package fails the
 * startup and therefore the build tests, never the import of an administrator.
 */
@Component
class StarterPackageCheck implements InitializingBean {

	private final StarterPackages starterPackages;

	StarterPackageCheck(StarterPackages starterPackages) {
		this.starterPackages = starterPackages;
	}

	@Override
	public void afterPropertiesSet() {
		for (StarterPackages.Shipped shipped : this.starterPackages.shipped()) {
			try {
				PackageValidator.validate(MetamodelPackages.definition(MetamodelPackages.read(shipped.content())));
			}
			catch (RuntimeException ex) {
				throw new IllegalStateException("shipped starter package " + shipped.packageId() + " is invalid: "
						+ ex.getMessage(), ex);
			}
		}
	}

}
