package com.tongji.relation.processor;

import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.tongji.counter.service.UserCounterService;
import com.tongji.relation.service.RelationService;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

/**
 * 关系事件处理器。
 * 职责：消费关系事件后，以 following 主表为准对齐粉丝镜像、列表缓存和用户计数；重复或延迟事件只会触发幂等对齐。
 */
@Service
public class RelationEventProcessor {
    private final RelationMapper mapper;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;
    private final RelationService relationService;
    private final DefaultRedisScript<Long> releaseLockScript;
    private static final Logger log = LoggerFactory.getLogger(RelationEventProcessor.class);

    public RelationEventProcessor(RelationMapper mapper, StringRedisTemplate redis, UserCounterService userCounterService, RelationService relationService) {
        this.mapper = mapper;
        this.redis = redis;
        this.userCounterService = userCounterService;
        this.relationService = relationService;
        this.releaseLockScript = new DefaultRedisScript<>();
        this.releaseLockScript.setResultType(Long.class);
        this.releaseLockScript.setScriptText("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void processAfterCommit(RelationEvent evt) {
        try {
            process(evt);
        } catch (RuntimeException e) {
            log.error("Failed to apply committed relation event {} for {} -> {}", evt.type(), evt.fromUserId(), evt.toUserId(), e);
        }
    }

    /**
     * 处理关系事件：入库、更新缓存、刷新计数，并进行幂等去重。
     * @param evt 关系事件
     */
    public void process(RelationEvent evt) {
        if (!"FollowCreated".equals(evt.type()) && !"FollowCanceled".equals(evt.type())) {
            return;
        }
        String dk = "dedup:rel:" + evt.type() + ":" + evt.fromUserId() + ":" + evt.toUserId() + ":" + (evt.id() == null ? "0" : String.valueOf(evt.id()));
        if (Boolean.TRUE.equals(redis.hasKey(dk))) {
            return;
        }
        String lockKey = dk + ":processing";
        String eventLockToken = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, eventLockToken, Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            if (Boolean.TRUE.equals(redis.hasKey(dk))) return;
            throw new IllegalStateException("Relation event is already being processed: " + dk);
        }
        Map<String, String> userLocks = new LinkedHashMap<>();
        try {
            if (Boolean.TRUE.equals(redis.hasKey(dk))) return;

            // Different events can touch the same user's counters. Lock both users in a
            // stable order so a stale count query cannot overwrite a newer reconciliation.
            List<Long> userIds = new ArrayList<>(List.of(evt.fromUserId(), evt.toUserId()));
            userIds = userIds.stream().distinct().sorted(Comparator.naturalOrder()).toList();
            for (Long userId : userIds) {
                String userLockKey = "rel:sync:user:" + userId;
                String token = UUID.randomUUID().toString();
                Boolean userLocked = redis.opsForValue().setIfAbsent(userLockKey, token, Duration.ofSeconds(30));
                if (!Boolean.TRUE.equals(userLocked)) {
                    throw new IllegalStateException("Could not acquire relation reconciliation lock for user " + userId);
                }
                userLocks.put(userLockKey, token);
            }

            // Events can be delayed or replayed. The following table is the source of truth.
            boolean active = mapper.existsFollowing(evt.fromUserId(), evt.toUserId()) > 0;
            if (active) {
                long mirrorId = evt.id() == null ? ThreadLocalRandom.current().nextLong(Long.MAX_VALUE) : evt.id();
                mapper.insertFollower(mirrorId, evt.toUserId(), evt.fromUserId(), 1);
            } else {
                mapper.cancelFollower(evt.toUserId(), evt.fromUserId());
            }

            // Drop both pages so the next read is rebuilt from the canonical following table.
            redis.delete(java.util.List.of("uf:flws:" + evt.fromUserId(), "uf:fans:" + evt.toUserId()));
            relationService.invalidateLocalCaches(evt.fromUserId(), evt.toUserId());

            syncCounters(evt.fromUserId());
            if (evt.toUserId() != evt.fromUserId()) syncCounters(evt.toUserId());
            redis.opsForValue().set(dk, "1", Duration.ofMinutes(10));
        } finally {
            List<Map.Entry<String, String>> acquired = new ArrayList<>(userLocks.entrySet());
            for (int i = acquired.size() - 1; i >= 0; i--) {
                Map.Entry<String, String> entry = acquired.get(i);
                redis.execute(releaseLockScript, List.of(entry.getKey()), entry.getValue());
            }
            redis.execute(releaseLockScript, List.of(lockKey), eventLockToken);
        }
    }

    private void syncCounters(long userId) {
        userCounterService.setRelationCounts(
                userId,
                mapper.countFollowingActive(userId),
                mapper.countFollowerActive(userId)
        );
    }
}
