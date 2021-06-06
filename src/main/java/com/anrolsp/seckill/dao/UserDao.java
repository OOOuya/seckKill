package com.anrolsp.seckill.dao;

import com.anrolsp.seckill.pojo.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDao {

    User getUserById(Integer id);
}
