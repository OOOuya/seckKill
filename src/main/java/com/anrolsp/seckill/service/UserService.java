package com.anrolsp.seckill.service;

import org.springframework.stereotype.Service;


/**
 * description: 和User有关的业务服务操作
 * @return
 */
public interface UserService {
    /**
     * description: 通过用户id，生成key，value是用户的访问次数。
     *              如果不存在key，生成key，默认值1
     *              存在key，调用incr自增
      * @Param: null
     * @return
     */
    int getUserCount(Integer id);
}
