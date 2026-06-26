package com.shm.core.mapper;

import com.shm.common.model.entity.FailedSystemMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 失败消息 Mapper — 操作 failed_system_messages 表（Phase 14 死信处理）
 */
@Mapper
public interface FailedMessageMapper {

    @Insert("INSERT INTO failed_system_messages (message_type, target_uid, payload, retry_count, max_retries, status) " +
            "VALUES (#{messageType}, #{targetUid}, #{payload}, #{retryCount}, #{maxRetries}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FailedSystemMessage message);

    @Select("SELECT id, message_type, target_uid, payload, retry_count, max_retries, last_error, status, created_at, updated_at " +
            "FROM failed_system_messages " +
            "WHERE status = #{status} AND retry_count < max_retries " +
            "ORDER BY created_at ASC LIMIT #{limit}")
    List<FailedSystemMessage> selectForRetry(@Param("status") String status, @Param("limit") int limit);

    @Update("UPDATE failed_system_messages SET retry_count = #{retryCount}, status = #{status}, " +
            "last_error = #{lastError}, updated_at = NOW() WHERE id = #{id}")
    int updateRetry(FailedSystemMessage message);
}
