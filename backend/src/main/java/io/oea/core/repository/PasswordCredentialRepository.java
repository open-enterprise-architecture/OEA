package io.oea.core.repository;

import io.oea.core.domain.auth.PasswordCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {

}
