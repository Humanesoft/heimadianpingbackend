package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
    @Override
    public Result queryList() {
        List<String> s = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_KEY, 0, -1);
        if(!s.isEmpty()){
            List<ShopType> arr = new ArrayList<>();
            for(String tmp:s){
                ShopType bean = JSONUtil.toBean(tmp, ShopType.class);
                arr.add(bean);
            }
            return Result.ok(arr);
        }
        List<ShopType> sort = query().orderByAsc("sort").list();
        if(sort==null){
            return Result.fail("店铺类型不存在");
        }
        for(ShopType type:sort){
            String jsonStr = JSONUtil.toJsonStr(type);
            stringRedisTemplate.opsForList().leftPush(RedisConstants.CACHE_SHOP_KEY,jsonStr);
        }
        return Result.ok(sort);
    }
}
