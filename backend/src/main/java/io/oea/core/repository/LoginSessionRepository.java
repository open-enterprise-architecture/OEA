package io.oea.core.repository;

import io.oea.core.domain.auth.LoginSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

	Optional<LoginSession> findByTokenHash(byte[] tokenHash);

}
