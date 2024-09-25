package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static org.apache.tomcat.jni.Lock.unlock;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
    private  static final ExecutorService executorService= Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) throws InterruptedException {
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicExpire(id);
        if (shop==null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }
    public Shop queryWithLogicExpire(Long id){
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        RedisData data = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data1 = (JSONObject) data.getData();
        Shop shop = JSONUtil.toBean(data1, Shop.class);
        LocalDateTime expireTime = data.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        String localKey = CACHE_SHOP_KEY+id;
        boolean b = tryLock(localKey);
        if(b){
            try {
                executorService.submit(()->{
                    this.saveShop2Redie(id,20L);
                });
            }catch (Exception e){
                throw new RuntimeException(e);
            }finally {
                delectLock(localKey);
            }
        }
        return shop;
    }
    public Shop queryWithMutex(Long id) throws InterruptedException {
        String shop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        if(StrUtil.isNotBlank(shop)){
            Shop bean = JSONUtil.toBean(shop, Shop.class);
            return bean;
        }
        if(shop != null)return null;
        Shop byId = null;
        try {
            if (!tryLock(CACHE_SHOP_KEY+id)) {
                Thread.sleep(500);
                return queryWithMutex(id);
            }
            byId = getById(id);
            if(byId==null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"", CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            String jsonStr = JSONUtil.toJsonStr(byId);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            delectLock(CACHE_SHOP_KEY+id);
        }
        return byId;
    }
    public Shop queryWithPassThrough(long id){
        String shop = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        if(StrUtil.isNotBlank(shop)){
            Shop bean = JSONUtil.toBean(shop, Shop.class);
            return bean;
        }
        if(shop != null)return null;
        Shop byId = getById(id);
        if(byId==null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,"", CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        String jsonStr = JSONUtil.toJsonStr(byId);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return byId;
    }
    private void delectLock(String key) {
        stringRedisTemplate.delete(key);
    }

    private boolean tryLock(String id) {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(CACHE_SHOP_KEY + id, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    public void saveShop2Redie(Long id,Long expireSeconds){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        System.out.println(JSONUtil.toJsonStr(redisData));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    @Override
    public Result update(Shop shop) {
        if(shop.getId()==null){
            return Result.fail("店铺不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
