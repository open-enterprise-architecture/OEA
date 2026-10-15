package io.oea.core.repository;

import io.oea.core.domain.auth.AuthProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthProviderRepository extends JpaRepository<AuthProvider, UUID> {

	boolean existsByIssuer(String issuer);

	List<AuthProvider> findAllByOrderByName();

	List<AuthProvider> findAllByActiveTrueOrderByName();

}
