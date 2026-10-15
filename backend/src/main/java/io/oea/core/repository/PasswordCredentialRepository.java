package io.oea.core.repository;

import io.oea.core.domain.auth.PasswordCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {

	/** The active password of a system admin account. */
	@Query("""
			select p from PasswordCredential p, LocalCredential c
			where c.id = p.credentialId and c.systemAdminAccountId = :accountId
			  and c.type = 'password' and c.status = 'active'""")
	Optional<PasswordCredential> findActiveForSystemAdmin(@Param("accountId") UUID accountId);

}
