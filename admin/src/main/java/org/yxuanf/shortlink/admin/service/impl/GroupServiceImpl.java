package org.yxuanf.shortlink.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.admin.dao.entity.GroupDO;
import org.yxuanf.shortlink.admin.dao.mapper.GroupMapper;
import org.yxuanf.shortlink.admin.service.GroupService;

/**
 * 短链接分组实现层
 */
@Service
@Slf4j
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {
}
