package org.yxuanf.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.admin.common.biz.user.UserContext;
import org.yxuanf.shortlink.admin.common.convention.exception.ClientException;
import org.yxuanf.shortlink.admin.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dao.mapper.UserMapper;
import org.yxuanf.shortlink.admin.dto.req.UserLoginReqDTO;
import org.yxuanf.shortlink.admin.dto.req.UserRegisterReqDTO;
import org.yxuanf.shortlink.admin.dto.req.UserUpdateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserLoginRespDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;
import org.yxuanf.shortlink.admin.service.UserService;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.yxuanf.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static org.yxuanf.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum.*;

/**
 * 用户接口实现
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupServiceImpl groupService;

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
        // 利用布隆过滤器，存在返回true
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
        // 通过分布式锁，防止短时间内同一用户名恶意请求注册
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());
        if (!lock.tryLock()) {
            throw new ClientException(USER_NAME_EXIST);
        }
        try {
            int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
            // inserted < 1 新增用户失败
            if (inserted < 1) {
                throw new ClientException(USER_SAVE_ERROR);
            }
            // 将用户名字添加到布隆过滤器中
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
            // 用户在注册时创建一个默认分组
            groupService.saveGroup(requestParam.getUsername(), "默认分组");
            return;
        } catch (DuplicateKeyException ex) {
            throw new ClientException(USER_EXIST);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 更新用户
     */
    @Override
    public void update(UserUpdateReqDTO requestParam) {
        //  验证当前用户名是否为登录用户，不一致返回错误
        if (!UserContext.getUsername().equals(requestParam.getUsername())) {
            throw new ClientException(USER_UPDATE_ERROR);
        }
        LambdaUpdateWrapper<UserDO> luw = new LambdaUpdateWrapper<>();
        luw.eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), luw);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY + requestParam.getUsername());
        // 如果用户信息存在，直接返回token
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登录错误"));
            return new UserLoginRespDTO(token);
        }
        LambdaQueryWrapper<UserDO> lqw = new LambdaQueryWrapper<>();
        UserDO userDO;
        if (hasUsername(requestParam.getUsername())) {
            lqw.eq(UserDO::getUsername, requestParam.getUsername());
            UserDO hasUser = baseMapper.selectOne(lqw);
            if (hasUser == null) {
                throw new ClientException("用户不存在");
            } else {
                lqw.eq(UserDO::getPassword, requestParam.getPassword()).eq(UserDO::getDelFlag, 0);
                userDO = baseMapper.selectOne(lqw);
                if (userDO == null) {
                    throw new ClientException("密码错误!请输入正确的密码");
                }
            }
        } else {
            throw new ClientException("用户不存在");
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
