package io.oea.core.repository;

import io.oea.core.domain.auth.IdpGroupMapping;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdpGroupMappingRepository extends JpaRepository<IdpGroupMapping, UUID> {

	/** Live (not deleted) mappings, optionally filtered by provider and role. */
	@Query("""
			SELECT m FROM IdpGroupMapping m WHERE m.deletedAt IS NULL
			AND (:providerId IS NULL OR m.authProviderId = :providerId) AND (:roleId IS NULL OR m.roleId = :roleId)
			ORDER BY m.createdAt""")
	List<IdpGroupMapping> findLive(UUID providerId, String roleId);

	@Query("""
			SELECT count(m) > 0 FROM IdpGroupMapping m WHERE m.deletedAt IS NULL AND m.issuer = :issuer
			AND m.groupIdentifier = :groupIdentifier AND m.roleId = :roleId AND m.id <> :exceptId""")
	boolean existsLive(String issuer, String groupIdentifier, String roleId, UUID exceptId);

	@Query("SELECT count(m) > 0 FROM IdpGroupMapping m WHERE m.deletedAt IS NULL AND m.authProviderId = :providerId")
	boolean existsLiveForProvider(UUID providerId);

}
