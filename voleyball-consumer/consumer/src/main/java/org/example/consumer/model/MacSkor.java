package org.example.consumer.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "mac_skor")
@Data
public class MacSkor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mac_id", nullable = false)
    private String macId;

    @Column(name = "set_no", nullable = false)
    private Integer setNo;

    @Column(name = "skor_a", nullable = false)
    private Integer skorA;

    @Column(name = "skor_b", nullable = false)
    private Integer skorB;

    @Column(nullable = false, length = 20)
    private String durum;

    @Column(name = "kazanan_takim", length = 1)
    private String kazananTakim;

    @Column(name = "kayit_zamani", nullable = false)
    private LocalDateTime kayitZamani = LocalDateTime.now();
}