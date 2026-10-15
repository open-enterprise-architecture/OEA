package io.oea.core.repository;

import io.oea.core.domain.person.PersonIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonIdentityRepository extends JpaRepository<PersonIdentity, UUID> {

	boolean existsByIssuerAndSubject(String issuer, String subject);

	Optional<PersonIdentity> findByIssuerAndSubject(String issuer, String subject);

	boolean existsByAuthProviderId(UUID authProviderId);

	List<PersonIdentity> findByPersonIdOrderByLinkedAt(Long personId);

}
