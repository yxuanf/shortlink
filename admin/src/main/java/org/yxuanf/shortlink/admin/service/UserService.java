package org.yxuanf.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.yxuanf.shortlink.admin.dao.entity.UserDO;
import org.yxuanf.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户接口
 */
public interface UserService extends IService<UserDO> {
    UserRespDTO getUserByUsername(String username);
}
