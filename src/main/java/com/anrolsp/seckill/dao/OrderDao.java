package com.anrolsp.seckill.dao;

import com.anrolsp.seckill.pojo.Order;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderDao {
    /**
     * description: 通过
     * 
      * @Param: null
     * @return 
     */
    void createOrder(Order order);
}
