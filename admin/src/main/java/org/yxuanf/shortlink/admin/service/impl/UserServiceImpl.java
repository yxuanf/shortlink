package org.yxuanf.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.admin.common.convention.exception.ClientException;
import org.yxuanf.shortlink.admin.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum;
import org.yxuanf.shortlink.admin.dto.resp.UserLoginRespDTO;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dao.mapper.UserMapper;
import org.yxuanf.shortlink.admin.dto.req.UserLoginReqDTO;
import org.yxuanf.shortlink.admin.dto.req.UserRegisterReqDTO;
import org.yxuanf.shortlink.admin.dto.req.UserUpdateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;
import org.yxuanf.shortlink.admin.service.UserService;

import java.util.concurrent.TimeUnit;

import static org.yxuanf.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static org.yxuanf.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum.USER_NAME_EXIST;
import static org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum.USER_SAVE_ERROR;

/**
 * 用户接口实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 根据用户名字返回用户信息
     */
    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(lqw);
        if (userDO == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    /**
     * 判断用户是否被注册
     */
    @Override
    public Boolean hasUsername(String username) {
        // 存在返回true
        return userRegisterCachePenetrationBloomFilter.contains(username);
    }

    /**
     * 注册用户
     */
    @Override
    public void register(UserRegisterReqDTO requestParam) {
        // 用户名存在，抛出异常
        if (hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
        // 通过分布式锁，防止短时间内同一用户名恶意请求注册
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        try {
            if (lock.tryLock()) {
                // inserted < 1 新增用户失败
                if (inserted < 1) {
                    throw new ClientException(USER_SAVE_ERROR);
                }
                // 将用户名字添加到布隆过滤器中
                userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
                return;
            }
            // 未获得锁直接抛出异常
            throw new ClientException(USER_NAME_EXIST);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 更新用户
     */
    @Override
    public void update(UserUpdateReqDTO requestParam) {
        // TODO 验证当前用户名是否为登录用户，不一致返回错误
        LambdaUpdateWrapper<UserDO> luw = new LambdaUpdateWrapper<>();
        luw.eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), luw);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> lqw = new LambdaQueryWrapper<>();
        UserDO userDO;
        if (hasUsername(requestParam.getUsername())) {
            lqw.eq(UserDO::getUsername, requestParam.getUsername())
                    .eq(UserDO::getPassword, requestParam.getPassword())
                    .eq(UserDO::getDelFlag, 0);
            userDO = baseMapper.selectOne(lqw);
            if (userDO == null) {
                throw new ClientException("用户密码错误");
            }
        } else {
            throw new ClientException("用户不存在");
        }
        Boolean hasLogin = stringRedisTemplate.hasKey(USER_LOGIN_KEY + requestParam.getUsername());
        if (hasLogin != null && hasLogin) {
            throw new ClientException("用户已经登录");
        }
        // 生成uuid作为用户唯一标识
        String uuid = UUID.randomUUID().toString();
        // 将用户登录凭证存放在redis
        stringRedisTemplate.opsForHash().put(USER_LOGIN_KEY + requestParam.getUsername(), uuid, JSON.toJSONString(userDO));
        // redis 设置30min有效期
        stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 3600L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token) != null;
    }

    @Override
    public void logout(String username, String token) {
        // 退出登入前需保证用户已登录
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete(USER_LOGIN_KEY + username);
            return;
        }
        throw new ClientException("用户Token不存在或用户未登录");
    }
}
