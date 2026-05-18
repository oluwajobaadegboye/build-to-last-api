package com.btl.transport.run;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RunParticipantId implements Serializable {

    @Column(name = "run_id")
    private Integer runId;

    @Column(name = "participant_id")
    private Integer participantId;
}
