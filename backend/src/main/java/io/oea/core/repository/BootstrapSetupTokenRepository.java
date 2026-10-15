package io.oea.core.repository;

import io.oea.core.domain.auth.BootstrapSetupToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BootstrapSetupTokenRepository extends JpaRepository<BootstrapSetupToken, Short> {

	/** Locks the token row for the bootstrapping transaction (no parallel bootstrapping, REQ-015). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from BootstrapSetupToken t where t.id = :id")
	Optional<BootstrapSetupToken> findForUpdate(@Param("id") Short id);

}
