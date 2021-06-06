package com.anrolsp.seckill.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService{

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public int getUserCount(Integer id) {
        //生成rediskey
        String limitKey = "KEY_" + id+"COUNT";
        String userCount = stringRedisTemplate.opsForValue().get(limitKey);
        if (userCount == null){
            stringRedisTemplate.opsForValue().set(limitKey, "1", 3600, TimeUnit.SECONDS);
        }else
            stringRedisTemplate.boundValueOps(limitKey).increment();
        return Integer.parseInt(userCount);
    }
}
