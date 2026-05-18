package com.btl.transport.participant;

import com.btl.transport.common.enums.ParticipantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    Optional<Participant> findByBtlCode(String btlCode);

    Optional<Participant> findByPhone(String phone);

    long countByNeedsAttentionTrue();

    @Query("""
        SELECT p FROM Participant p
        LEFT JOIN FETCH p.hotel h
        WHERE (:status IS NULL OR p.status = :status)
          AND (:hotelId IS NULL OR h.id = :hotelId)
          AND (:needsAttention IS NULL OR p.needsAttention = :needsAttention)
          AND (:search IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.btlCode) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Participant> findWithFilters(
        @Param("status") ParticipantStatus status,
        @Param("hotelId") Integer hotelId,
        @Param("needsAttention") Boolean needsAttention,
        @Param("search") String search,
        Pageable pageable
    );
}
