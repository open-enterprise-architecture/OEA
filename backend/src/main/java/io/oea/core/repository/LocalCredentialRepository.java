package io.oea.core.repository;

import io.oea.core.domain.auth.LocalCredential;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, UUID> {

	List<LocalCredential> findBySystemAdminAccountIdAndTypeAndStatus(UUID systemAdminAccountId, String type,
			String status);

}
