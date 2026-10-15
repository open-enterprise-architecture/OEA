package io.oea.core.repository;

import io.oea.core.domain.auth.TotpCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {

}
