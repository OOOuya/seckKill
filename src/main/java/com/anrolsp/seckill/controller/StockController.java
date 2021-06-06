package com.anrolsp.seckill.controller;


import com.anrolsp.seckill.service.OrderService;
import com.anrolsp.seckill.service.UserService;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("stock")
@Slf4j
public class StockController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;
    /**
     * description: 通过 乐观锁+接口限流+限时+隐藏接口+频率限制 实现秒杀
     * @param 商品id 用户id md5
     * @return String类型的商品id
     */
    @GetMapping("killtokenmd5limit")
    public String killtokenmd5limit(Integer id, Integer sid, String md5){
        if (!rateLimiter.tryAcquire(3, TimeUnit.SECONDS)){
            throw new RuntimeException("秒杀被限流！请重试！");
        }
        System.out.println("秒杀商品的id： " + id);
        //进行单用户访问限制
        int userCount = userService.getUserCount(sid);
        log.info("用户到目前位置的访问次数[{}]", userCount);
        if (userCount > 10){
            return "购买失败，超过访问频率限制！";
        }
        try{
            int killId = orderService.kill(id,sid, md5);
            return "秒杀成功，订单id: " + String.valueOf(killId);
        }catch(Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }


    /**
     * description: 通过 乐观锁+接口限流+限时+隐藏接口 实现秒杀
     * @param 商品id 用户id md5
     * @return String类型的商品id
     */
    @GetMapping("killtokenmd5")
    public String killtokenmd5(Integer id, Integer sid, String md5){
        if (!rateLimiter.tryAcquire(3, TimeUnit.SECONDS)){
            throw new RuntimeException("秒杀被限流！请重试！");
        }
        System.out.println("秒杀商品的id： " + id);
        try{
            int killId = orderService.kill(id,sid, md5);
            return "秒杀成功，订单id: " + String.valueOf(killId);
        }catch(Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }

    /**
     * description: 通过 乐观锁+接口限流+限时 实现秒杀
     * @param 商品id
     * @return String类型的商品id
     */
    @GetMapping("killtoken")
    public String killtoken(Integer id){
        //接口限流
        if (!rateLimiter.tryAcquire(3, TimeUnit.SECONDS)){
            throw new RuntimeException("秒杀被限流！请重试！");
        }
        System.out.println("秒杀商品的id： " + id);
        try{
            int killId = orderService.kill(id);
            return "秒杀成功，订单id: " + String.valueOf(killId);
        }catch(Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }
    /**
     * description: 通过 乐观锁 实现秒杀商品
     * @param id 商品id
     * @return String类型的商品id
     */

    @GetMapping("kill")
    public String kill(Integer id){
        System.out.println("秒杀商品的id： " + id);
        try{
            int killId = orderService.kill(id);
            return "秒杀成功，订单id: " + String.valueOf(killId);
        }catch(Exception e){
            e.printStackTrace();
            return e.getMessage();
        }

    }

    //令牌桶实例
    private RateLimiter rateLimiter = RateLimiter.create(40);
    /**
     * description: 令牌桶的测试方法
     *
     * @Param: 商品id
     * @return
     */
    @RequestMapping("sale")
    public String sale(Integer id){
        log.info("等待的时间" + rateLimiter.acquire());
        if (!rateLimiter.tryAcquire(2, TimeUnit.SECONDS)){
            System.out.println("当前请求被限流，无法处理后续秒杀逻辑..");
            return "抢购失败";
        }
        System.out.println("处理业务....");
        return "抢购成功";
    }

    /**
     * description: 用户点击秒杀按钮，根据用户id和商品id生成md5
     *
     * @Param: 商品id，用户sid
     * @return md5加密
     */
    @GetMapping("md5")
    public String md5(Integer id, Integer sid){
        String md5;
        try{
            //getMd5生成失败抛出异常
            md5 = orderService.getMd5(id, sid);
        }catch(Exception e){
            e.printStackTrace();
            return "md5获取失败!" + e.getMessage();
        }
        return "获取md5信息："+md5;
    }

}
