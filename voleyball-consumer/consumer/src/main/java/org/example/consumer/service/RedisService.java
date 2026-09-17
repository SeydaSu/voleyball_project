package org.example.consumer.service;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RedisService {

    private static final String KEY_PREFIX = "mac:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisService(RedisTemplate<String, String> redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    private static final String PROCESSED_EVENTS_KEY = "processed-events";
    private static final long PROCESSED_EVENT_TTL_SECONDS = 86400; // 24 saat

    public boolean eventDahaOnceIslendiMi(String eventId) {
        Boolean eklendi = redisTemplate.opsForSet().isMember(PROCESSED_EVENTS_KEY, eventId);
        return Boolean.TRUE.equals(eklendi);
    }

    public void eventIslendiOlarakIsaretle(String eventId) {
        redisTemplate.opsForSet().add(PROCESSED_EVENTS_KEY, eventId);
        redisTemplate.expire(PROCESSED_EVENTS_KEY, PROCESSED_EVENT_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void skorGuncelle(String macId, Integer setNo, Integer skorA, Integer skorB, String durum){
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
        String key = KEY_PREFIX + macId;

        Map<String, String> alanlar = new HashMap<>();
        alanlar.put("setNo", String.valueOf(setNo));
        alanlar.put("skorA", String.valueOf(skorA));
        alanlar.put("skorB", String.valueOf(skorB));
        alanlar.put("durum", durum);
        alanlar.put("guncellemeZamani", java.time.Instant.now().toString());

        hashOps.putAll(key, alanlar);
    }

    public Map<Object, Object> skorGetir(String macId) {
        return redisTemplate.opsForHash().entries(KEY_PREFIX + macId);
    }

    public void macSil(String macId) {
        redisTemplate.delete(KEY_PREFIX + macId);
    }

    public boolean macVarMi(String macId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + macId));
    }

}