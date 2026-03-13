package eu.europa.ec.simpl.iam;

public class AgentIdentityAttributesUtil {

    public static String getValidAgentIdentityAttributes() {
        return """
                [
                    {
                      "id": "0192de27-ddd0-70b0-947f-8da84e05ce46",
                      "code": "CONSUMER",
                      "name": "CONSUMER",
                      "description": "",
                      "assignableToRoles": true,
                      "enabled": true,
                      "creationTimestamp": "2024-10-30T16:00:07.000+00:00",
                      "updateTimestamp": "2024-10-30T16:00:18.000+00:00",
                      "used": true,
                      "right": true
                    },
                    {
                      "id": "0192de28-a858-7c7a-96a7-dab46c7b2db1",
                      "code": "RESEARCHER",
                      "name": "RESEARCHER",
                      "description": "",
                      "assignableToRoles": true,
                      "enabled": true,
                      "creationTimestamp": "2024-10-30T16:00:59.000+00:00",
                      "updateTimestamp": "2024-10-30T16:00:59.000+00:00",
                      "used": true,
                      "right": true
                    },
                    {
                      "id": "01938c38-848a-715a-8183-14da21110822",
                      "code": "SD_PUBLISHER",
                      "name": "SD_PUBLISHER",
                      "description": "",
                      "assignableToRoles": true,
                      "enabled": true,
                      "creationTimestamp": "2024-12-03T11:12:14.000+00:00",
                      "updateTimestamp": "2024-12-03T11:12:25.000+00:00",
                      "used": true,
                      "right": true
                    },
                    {
                      "id": "01938c38-dcbd-7a0c-b160-8b955b385086",
                      "code": "SD_CONSUMER",
                      "name": "SD_CONSUMER",
                      "description": "",
                      "assignableToRoles": true,
                      "enabled": true,
                      "creationTimestamp": "2024-12-03T11:12:37.000+00:00",
                      "updateTimestamp": "2024-12-03T11:12:37.000+00:00",
                      "used": true,
                      "right": true
                    }
                  ]
                """;
    }

    public static String getValidEmptyAgentIdentityAttributes() {
        return "[]";
    }
}
