package com.todo.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION REDIS — RedisConfig
 * ══════════════════════════════════════════════════════════════
 *
 *  Configure le RedisTemplate utilisé par RedisTaskCacheAdapter.
 *
 *  POURQUOI CONFIGURER LE TEMPLATE MANUELLEMENT ?
 *  → Spring Boot auto-configure un RedisTemplate<Object, Object>
 *    avec la sérialisation Java par défaut (JdkSerializationRedisSerializer)
 *  → Problèmes de la sérialisation Java native :
 *    - Binaire : illisible dans redis-cli
 *    - Couplage fort : si on change le nom d'une classe Java → corruption du cache
 *    - Non portable : incompatible avec des clients non-Java
 *  → Solution : JSON via Jackson (lisible, portable, versionnable)
 *
 *  SÉRIALISATION CHOISIE :
 *  → Clés (String) : StringRedisSerializer → texte brut (ex: "task:uuid")
 *  → Valeurs (Object) : GenericJackson2JsonRedisSerializer → JSON avec type
 *
 *  JSON AVEC TYPE INCLUS :
 *  → GenericJackson2JsonRedisSerializer ajoute "@class" dans le JSON :
 *    { "@class": "com.todo.domain.model.Task", "id": {...}, "title": "..." }
 *  → Permet la désérialisation sans savoir à l'avance le type exact
 *  → TRADE-OFF : le JSON est couplé au nom de classe Java
 *    → Renommer Task doit s'accompagner d'une migration du cache
 *    → Acceptable car le cache a un TTL (5 min) — il expire naturellement
 *
 *  MODULES JACKSON :
 *  → JavaTimeModule : sérialise LocalDate, Instant, etc. en ISO 8601
 *    Ex: LocalDate.of(2026, 6, 30) → "2026-06-30"
 *        Instant.now() → "2026-05-24T10:30:00.000Z"
 *  → WRITE_DATES_AS_TIMESTAMPS = false : format ISO string (pas de timestamp numérique)
 *
 *  CONNEXION REDIS :
 *  → RedisConnectionFactory est auto-configuré par Spring Boot
 *    à partir de spring.data.redis.host/port dans application.yml
 *  → En local : Redis standalone sur localhost:6379 (docker-compose.yml)
 *  → En AWS : Redis Cluster (ElastiCache) — la factory change mais le template non
 * ══════════════════════════════════════════════════════════════
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate configuré pour JSON avec les types Java inclus.
     *
     * @param connectionFactory auto-injecté par Spring Boot (from application.yml config)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ── Sérialisation des CLÉS : String simple ───────────────────
        // Ex: "task:550e8400-e29b-41d4-a716-446655440000"
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // ── Sérialisation des VALEURS : JSON avec type ───────────────
        // Permet à Jackson de désérialiser vers le bon type Java
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // Initialise le template après configuration
        template.afterPropertiesSet();
        return template;
    }

    /**
     * ObjectMapper spécialisé pour Redis.
     *
     * SÉPARÉ DE L'ObjectMapper HTTP pour deux raisons :
     * 1. La configuration "type inclus" (@class) est nécessaire pour Redis
     *    mais indésirable dans les réponses HTTP REST (pollution du JSON client)
     * 2. On peut configurer différemment selon le contexte
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Support des types Java 8+ date/time (LocalDate, Instant...)
        mapper.registerModule(new JavaTimeModule());

        // Format ISO 8601 pour les dates (pas de timestamps numériques)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Inclure le type Java dans le JSON pour permettre la désérialisation
        // Ex: { "@class": "com.todo.domain.model.Task", ... }
        // activateDefaultTyping = "ajouter le type à tous les objets non-finaux"
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY  // le type est une propriété JSON (@class)
        );

        return mapper;
    }
}
