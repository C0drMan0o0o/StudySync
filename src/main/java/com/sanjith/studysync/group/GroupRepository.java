package com.sanjith.studysync.group;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<StudyGroup, Long> {

    @Query("SELECT DISTINCT g FROM StudyGroup g JOIN g.members filterMember LEFT JOIN FETCH g.members "
            + "WHERE filterMember.id = :userId")
    List<StudyGroup> findByMembersId(@Param("userId") Long userId);

    boolean existsByIdAndMembersId(Long groupId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM StudyGroup g WHERE g.id = :id")
    Optional<StudyGroup> findByIdForUpdate(@Param("id") Long id);
}
