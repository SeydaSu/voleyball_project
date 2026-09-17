package org.example.consumer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.consumer.model.MacSkor;
import org.example.consumer.model.SkorEventDto;
import org.example.consumer.repository.MacSkorRepository;
import org.example.consumer.service.RedisService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SkorEventListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisService redisService;
    private final MacSkorRepository macSkorRepository;

    public SkorEventListener(RedisService redisService, MacSkorRepository macSkorRepository) {
        this.redisService = redisService;
        this.macSkorRepository = macSkorRepository;
        System.out.println(">>> SkorEventListener bean OLUŞTU <<<");
    }

    @KafkaListener(topics = "voleybol-skor-events", groupId = "voleybol-consumer-group")
    public void dinle(String mesajJson) {
        try {
            SkorEventDto event = objectMapper.readValue(mesajJson, SkorEventDto.class);

            if (redisService.eventDahaOnceIslendiMi(event.getEventId())) {
                System.out.println("Event zaten işlenmiş, atlanıyor: " + event.getEventId());
                return;
            }

            if ("SKOR_ARTIS".equals(event.getOlayTipi())) {
                skorArtisIsle(event);
            } else if ("MAC_BITTI".equals(event.getOlayTipi())) {
                macBittiIsle(event);
            } else {
                System.out.println("Bilinmeyen olayTipi: " + event.getOlayTipi());
            }
            redisService.eventIslendiOlarakIsaretle(event.getEventId());

        } catch (Exception e) {
            System.err.println("Mesaj işlenirken hata oluştu: " + e.getMessage());
        }
    }

    private void skorArtisIsle(SkorEventDto event) {
        redisService.skorGuncelle(
                event.getMacId(), event.getSetNo(), event.getSkorA(), event.getSkorB(), "DEVAM_EDIYOR"
        );
        System.out.println("Redis güncellendi: " + event.getSkorA() + "-" + event.getSkorB());
    }

    private void macBittiIsle(SkorEventDto event) {
        MacSkor kayit = new MacSkor();
        kayit.setMacId(event.getMacId());
        kayit.setSetNo(event.getSetNo());
        kayit.setSkorA(event.getSkorA());
        kayit.setSkorB(event.getSkorB());
        kayit.setDurum("BITTI");
        kayit.setKazananTakim(event.getKazananTakim());
        kayit.setKayitZamani(LocalDateTime.now());

        macSkorRepository.save(kayit);
        redisService.macSil(event.getMacId());

        System.out.println("Maç bitti, final skor DB'ye kaydedildi, Redis temizlendi.");
    }
}