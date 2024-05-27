package org.yxuanf.shortlink.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.admin.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.admin.common.enums.UserErrorCodeEnum;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dao.mapper.UserMapper;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;
import org.yxuanf.shortlink.admin.service.UserService;

/**
 * 用户接口实现
 */
@Service
public class UserSeriveImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {
    /**
     * 根据用户名字返回用户信息
     */
    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(UserDO::getUsername, username).select();
        UserDO userDO = baseMapper.selectOne(lqw);
        if(userDO == null){
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }
}
