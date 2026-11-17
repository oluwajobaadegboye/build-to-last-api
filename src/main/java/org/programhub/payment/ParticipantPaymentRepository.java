package org.programhub.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ParticipantPaymentRepository extends JpaRepository<ParticipantPayment, Integer> {
    List<ParticipantPayment> findByParticipantIdOrderByRecordedAtAsc(Integer participantId);

    List<ParticipantPayment> findByProgramIdOrderByRecordedAtAsc(String programId);

    @Query("SELECT COALESCE(SUM(CASE WHEN p.kind = 'refund' THEN -p.amount ELSE p.amount END), 0) FROM ParticipantPayment p WHERE p.participantId = :pid")
    BigDecimal totalForParticipant(@Param("pid") Integer participantId);

    /** [participantId, total] rows for a page of participants. */
    @Query("SELECT p.participantId, SUM(CASE WHEN p.kind = 'refund' THEN -p.amount ELSE p.amount END) FROM ParticipantPayment p WHERE p.participantId IN :ids GROUP BY p.participantId")
    List<Object[]> totalsFor(@Param("ids") Collection<Integer> participantIds);
}
