package eu.europa.ec.simpl.iam.jwt;

import eu.europa.ec.simpl.iam.service.AgentIdentityAttribute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@AllArgsConstructor
@Getter
@Builder
@NoArgsConstructor
@ToString
public class IdentityAttribute {

    private String id;
    private String code;
    private String name;
    private boolean assignableToRoles;
    private boolean enabled;
    private LocalDateTime creationTimestamp;
    private LocalDateTime updateTimestamp;

    public static IdentityAttribute mapAgentIdentityAttributeToIdentityAttribute
            (final AgentIdentityAttribute agentIdentityAttribute) {
        final var formatter = DateTimeFormatter.ISO_DATE_TIME;
        return IdentityAttribute.builder()
                .id(agentIdentityAttribute.getId())
                .code(agentIdentityAttribute.getCode())
                .name(agentIdentityAttribute.getName())
                .assignableToRoles(agentIdentityAttribute.isAssignableToRoles())
                .enabled(agentIdentityAttribute.isEnabled())
                .creationTimestamp(LocalDateTime.parse(agentIdentityAttribute.getCreationTimestamp(), formatter))
                .updateTimestamp(LocalDateTime.parse(agentIdentityAttribute.getUpdateTimestamp(), formatter))
                .build();
    }
}
