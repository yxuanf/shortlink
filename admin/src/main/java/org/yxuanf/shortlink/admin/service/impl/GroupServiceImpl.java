package org.yxuanf.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.admin.common.biz.user.UserContext;
import org.yxuanf.shortlink.admin.dao.entity.GroupDO;
import org.yxuanf.shortlink.admin.dao.mapper.GroupMapper;
import org.yxuanf.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import org.yxuanf.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import org.yxuanf.shortlink.admin.remote.ShortLinkRemoteService;
import org.yxuanf.shortlink.admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import org.yxuanf.shortlink.admin.service.GroupService;
import org.yxuanf.shortlink.admin.toolkit.RandomGenerator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 短链接分组实现层
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final ShortLinkRemoteService shortLinkRemoteService;

    /**
     * 新增短链接分组
     *
     * @param groupName 请求参数
     */
    @Override
    public void saveGroup(String groupName) {
        this.saveGroup(UserContext.getUsername(), groupName);
    }

    @Override
    public void saveGroup(String username, String groupName) {
        String gid;
        do {
            gid = RandomGenerator.generateRandom();
        } while (hasGid(username, gid));
        GroupDO groupDO = GroupDO.builder().
                gid(gid)
                .name(groupName)
                .username(username).sortOrder(0).build();
        baseMapper.insert(groupDO);
    }

    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        LambdaQueryWrapper<GroupDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(GroupDO::getDelFlag, 0).eq(GroupDO::getUsername, UserContext.getUsername())
                .orderByDesc(List.of(GroupDO::getSortOrder, GroupDO::getUpdateTime));
        List<GroupDO> groupDOList = baseMapper.selectList(lqw);
        //   获取所有分组gid的数量
        List<ShortLinkGroupCountRespDTO> gids = shortLinkRemoteService.listGroupShortLinkCount(
                groupDOList.stream().map(GroupDO::getGid).toList()).getData();
        // 不包含短链接数量的查询结果
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOList, ShortLinkGroupRespDTO.class);
        // 转换为map(gid:count)
        Map<String, Integer> countMap = gids.stream().collect(
                Collectors.toMap(ShortLinkGroupCountRespDTO::getGid, ShortLinkGroupCountRespDTO::getShortLinkCount));
//        shortLinkGroupRespDTOList.forEach(each -> {
//            Optional<ShortLinkGroupCountRespDTO> first = gids
//                    .stream()
//                    .filter(item -> Objects.equals(item.getGid(), each.getGid())).findFirst();
//            first.ifPresent(item -> each.setShortLinkCount(first.get().getShortLinkCount()));
//        });
        // 添加短链接各个分组的数量
        return shortLinkGroupRespDTOList.stream()
                .peek(item -> item.setShortLinkCount(countMap.getOrDefault(item.getGid(), 0))).toList();
    }

    /**
     * 根据分组id,修改分组名
     */
    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<GroupDO> luq = new LambdaUpdateWrapper<>();
        luq.eq(GroupDO::getUsername, UserContext.getUsername()).eq(GroupDO::getGid, requestParam.getGid()).eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = BeanUtil.copyProperties(requestParam, GroupDO.class);
        baseMapper.update(groupDO, luq);
    }

    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> luq = new LambdaUpdateWrapper<>();
        luq.eq(GroupDO::getUsername, UserContext.getUsername()).eq(GroupDO::getGid, gid).eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        // 软删除
        groupDO.setDelFlag(1);
        baseMapper.update(groupDO, luq);
    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(each -> {
            GroupDO groupDO = GroupDO.builder().sortOrder(each.getSortOrder()).build();
            LambdaUpdateWrapper<GroupDO> luw = new LambdaUpdateWrapper<>();
            luw.eq(GroupDO::getUsername, UserContext.getUsername()).eq(GroupDO::getGid, each.getGid()).eq(GroupDO::getDelFlag, 0);
            baseMapper.update(groupDO, luw);
        });
    }

    /**
     * @param gid 查询的gid
     * @return true:存在重复gid，false: gid 不重复
     */
    private boolean hasGid(String username, String gid) {
        LambdaQueryWrapper<GroupDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(GroupDO::getGid, gid).eq(GroupDO::getUsername, Optional.ofNullable(username).orElse(UserContext.getUsername()));
        GroupDO hasGroupFlag = baseMapper.selectOne(lqw);
        return hasGroupFlag != null;
    }
}
