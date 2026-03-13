package eu.europa.ec.simpl.iam.service;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class AgentIdentityAttribute {
    private String code;
    private String name;
    private String creationTimestamp;
    private String description;
    private boolean assignableToRoles;
    private String id;
    private boolean used;
    private boolean right;
    private boolean enabled;
    private String updateTimestamp;
    private boolean assignedToParticipant;
}
