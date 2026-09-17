package org.example.consumer.model;

import lombok.Data;

@Data
public class SkorEventDto {
    private String eventId;
    private String macId;
    private String olayTipi;
    private String takim;
    private Integer setNo;
    private Integer skorA;
    private Integer skorB;
    private String kazananTakim;
    private String zaman;
}