package com.anrolsp.seckill.service;


import com.anrolsp.seckill.dao.OrderDao;
import com.anrolsp.seckill.dao.StockDao;
import com.anrolsp.seckill.dao.UserDao;
import com.anrolsp.seckill.pojo.Order;
import com.anrolsp.seckill.pojo.Stock;
import com.anrolsp.seckill.pojo.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private StockDao stockDao;

    @Autowired
    private OrderDao orderDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * description: 判断是否超时、校验md5、校验库存、扣除库存、创建订单
     *
     * @Param: 商品id
     * @return 订单id
     */
    @Override
    public int kill(Integer id, Integer sid, String md5) {
//        //超时需要每次定义一个Redis key，测试的时候比较麻烦，注释掉了
//        //判断秒杀是否超时
//        if (!stringRedisTemplate.hasKey("kill"+ id)){
//            throw new RuntimeException("秒杀时间已过！");
//        }
        //校验md5
        String md5Key = "KEY_"+ id + "_" + sid;
        if (md5 == null || !md5.equals(stringRedisTemplate.opsForValue().get(md5Key)))
            throw new RuntimeException("不存在对应的md5！");

        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        return createOrder(stock);
    }


    /**
     * description: 判断是否超时、校验md5、校验库存、扣除库存、创建订单
     * 
      * @Param: 商品id
     * @return 订单id
     */
    @Override
    public int kill(Integer id) {
        //超时需要每次定义一个Redis key，测试的时候比较麻烦，注释掉了
        //判断秒杀是否超时
        if (!stringRedisTemplate.hasKey("kill"+ id)){
            throw new RuntimeException("秒杀时间已过！");
        }
        //校验库存
        Stock stock = checkStock(id);
        //扣除库存
        updateSale(stock);
        //创建订单
        return createOrder(stock);
    }

    /**
     * description: 校验库存
      * @Param: 商品id
     * @return Stock对象
     */
    private Stock checkStock(Integer id){
        Stock stock = stockDao.checkStock(id);
        if (stock.getSale().equals(stock.getCount())) {
            throw new RuntimeException("库存不足！！");
        }
        return stock;
    }
    
    /**
     * description: 扣除库存
      * @Param: Stock商品对象
     * @return 
     */
    private void updateSale(Stock stock){

        //stock.setSale(stock.getSale() - 1);
        //使用数据库层面的version字段实现CAS，避免ABA问题，需要通过版本号和销量来更新销量
        //有的线程会无法更新sale（版本号不对），因此要处理无法更新的异常情况，失败的事务返回0
        int result = stockDao.updateSale(stock);
        if (result == 0){
            throw new RuntimeException("商品购买失败！");
        }
    }

    /**
     * description: 创建订单
     *
      * @Param: Stock商品对象
     * @return 订单id
     */
    private Integer createOrder(Stock stock){
        Order order = new Order();
        //mybatis会自动生成主键，我们不需要设置主键值
        order.setSid(stock.getId()).setName(stock.getName()).setCreateDate(new Date());
        orderDao.createOrder(order);
        return order.getId();
    }

    /**
     * description: 用户点击秒杀按钮，根据用户id和商品id生成md5
     *
     * @Param: 商品id，用户sid
     * @return md5加密
     */
    public String getMd5(Integer id, Integer sid){
        //验证用户的合法性
        User user = userDao.getUserById(sid);
        if (user == null)
            throw new RuntimeException("用户不存在！");
        log.info("用户信息:[{}]", user.toString());
        Stock stock = stockDao.checkStock(id);
        if (stock == null)
            throw new RuntimeException("商品不存在!");
        log.info("商品信息:[{}]", stock.toString());
        //设置md5Key作为redis的key
        String key = "KEY_"+ id + "_" + sid;
        //生成md5,!@#*&是salt，防止被轻易破解
        String md5Value = DigestUtils.md5DigestAsHex((key+"!@#*&").getBytes());
        //存入Redis中
        stringRedisTemplate.opsForValue().set(key, md5Value, 120, TimeUnit.SECONDS);
        log.info("md5写入[{}] [{}]", key, md5Value);
        return "md5:" + key;
    }

}
