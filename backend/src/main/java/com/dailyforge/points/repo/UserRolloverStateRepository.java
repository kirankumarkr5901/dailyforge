package com.dailyforge.points.repo;

import com.dailyforge.points.domain.UserRolloverState;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRolloverStateRepository extends JpaRepository<UserRolloverState, UUID> {
}
