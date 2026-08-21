package com.windrunner.server.user.persistence;

import com.windrunner.server.user.domain.UserSetting;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserSettingRepository extends CrudRepository<UserSetting, String> {

    @Query("""
            SELECT id, user_id, key, value::text AS value, created_at, updated_at
            FROM user_setting
            WHERE user_id = :userId
            ORDER BY key ASC
            """)
    List<UserSetting> findByUserId(@Param("userId") String userId);

    @Query("""
            SELECT id, user_id, key, value::text AS value, created_at, updated_at
            FROM user_setting
            WHERE user_id = :userId
              AND key = :key
            """)
    List<UserSetting> findByUserIdAndKey(@Param("userId") String userId,
                                         @Param("key") String key);

    @Modifying
    @Query("""
            INSERT INTO user_setting (
                id,
                user_id,
                key,
                value
            )
            VALUES (
                :id,
                :userId,
                :key,
                CAST(:value AS jsonb)
            )
            ON CONFLICT (user_id, key)
            DO UPDATE SET value = EXCLUDED.value,
                          updated_at = NOW()
            """)
    void upsert(@Param("id") String id,
                @Param("userId") String userId,
                @Param("key") String key,
                @Param("value") String value);

    @Modifying
    @Query("""
            DELETE FROM user_setting
            WHERE user_id = :userId
              AND key = :key
            """)
    int delete(@Param("userId") String userId,
               @Param("key") String key);

    @Modifying
    @Query("""
            DELETE FROM user_setting
            WHERE user_id = :userId
            """)
    int deleteByUserId(@Param("userId") String userId);
}