package com.anrolsp.seckill.service;

import org.springframework.stereotype.Service;


public interface OrderService {
    /**
     * description: 处理秒杀的下单方法，并返回订单的id
     *
     * @Param: 商品id
     * @return 订单的id
     */
    int kill(Integer id);

    int kill(Integer id, Integer sid, String md5);
    /**
     * description: 用户点击秒杀按钮，根据用户id和商品id生成md5
     *
     * @Param: 商品id，用户sid
     * @return md5加密
     */
    String getMd5(Integer id, Integer sid);

}
