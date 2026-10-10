package com.rtoshield.repo;

import com.rtoshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigEntryRepository extends JpaRepository<ConfigEntry, String> {
}
