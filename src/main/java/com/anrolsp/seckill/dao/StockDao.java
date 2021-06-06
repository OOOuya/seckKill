package com.anrolsp.seckill.dao;

import com.anrolsp.seckill.pojo.Stock;
import org.springframework.stereotype.Repository;

@Repository
public interface StockDao {
    /**
     * description: 根据商品id返回库存对象
     *
     * @Param: 商品id
     * @return Stock对象
     */
    Stock checkStock(Integer id);

    /**
     * description: 根据商品id扣除库存
     *
     * @Param: 商品id
     * @return void
     */
    int updateSale(Stock stock);
}