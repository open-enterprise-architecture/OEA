package io.oea.core.repository;

import io.oea.core.domain.auth.SystemAdminAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAdminAccountRepository extends JpaRepository<SystemAdminAccount, UUID> {

	Optional<SystemAdminAccount> findByLoginName(String loginName);

}
