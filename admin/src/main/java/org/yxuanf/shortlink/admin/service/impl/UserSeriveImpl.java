package org.yxuanf.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.admin.common.convention.exception.ClientException;
import org.yxuanf.shortlink.admin.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dao.mapper.UserMapper;
import org.yxuanf.shortlink.admin.dto.req.UserRegisterReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;
import org.yxuanf.shortlink.admin.service.UserService;

import static org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum.USER_NAME_EXIST;
import static org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum.USER_SAVE_ERROR;

/**
 * 用户接口实现
 */
@Service
@RequiredArgsConstructor
public class UserSeriveImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;

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

    @Override
    public Boolean hasUsername(String username) {
        return userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        // 用户名存在，抛出异常
        if (hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
        // inserted < 1 新增用户失败
        if (inserted < 1) {
            throw new ClientException(USER_SAVE_ERROR);
        }
        // 将用户名字添加到布隆过滤器中
        userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
    }


}
