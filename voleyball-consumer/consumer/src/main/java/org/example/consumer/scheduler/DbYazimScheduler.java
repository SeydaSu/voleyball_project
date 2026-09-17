package org.example.consumer.scheduler;

import org.example.consumer.model.MacSkor;
import org.example.consumer.repository.MacSkorRepository;
import org.example.consumer.service.RedisService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class DbYazimScheduler {

    private static final String MAC_ID = "mevcut-mac";

    private final RedisService redisService;
    private final MacSkorRepository macSkorRepository;

    public DbYazimScheduler(RedisService redisService, MacSkorRepository macSkorRepository) {
        this.redisService = redisService;
        this.macSkorRepository = macSkorRepository;
    }

    @Scheduled(fixedRate = 600000)
    public void araSkorKaydet() {
        if (!redisService.macVarMi(MAC_ID)) {
            System.out.println("Aktif maç yok, kaydedilecek bir şey yok.");
            return;
        }

        Map<Object, Object> redisVeri = redisService.skorGetir(MAC_ID);

        MacSkor kayit = new MacSkor();
        kayit.setMacId(MAC_ID);
        kayit.setSetNo(Integer.parseInt((String) redisVeri.get("setNo")));
        kayit.setSkorA(Integer.parseInt((String) redisVeri.get("skorA")));
        kayit.setSkorB(Integer.parseInt((String) redisVeri.get("skorB")));
        kayit.setDurum("DEVAM_EDIYOR");
        kayit.setKayitZamani(LocalDateTime.now());

        macSkorRepository.save(kayit);
        System.out.println("Ara skor DB'ye kaydedildi: " + kayit.getSkorA() + "-" + kayit.getSkorB());
    }
}