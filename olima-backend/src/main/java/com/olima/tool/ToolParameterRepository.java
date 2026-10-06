package com.olima.tool;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolParameterRepository extends JpaRepository<ToolParameterEntity, UUID> {}
