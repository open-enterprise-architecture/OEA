package io.oea.core.repository;

import io.oea.core.domain.settings.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Short> {

}
