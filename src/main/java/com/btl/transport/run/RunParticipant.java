package com.btl.transport.run;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "run_participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunParticipant {

    @EmbeddedId
    private RunParticipantId id;

    @Column(name = "boarded")
    private Boolean boarded;

    @Column(name = "boarded_at")
    private OffsetDateTime boardedAt;
}
