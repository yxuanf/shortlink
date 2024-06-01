package org.yxuanf.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.yxuanf.shortlink.project.common.convention.exception.ServiceException;
import org.yxuanf.shortlink.project.dao.entity.ShortLinkDO;
import org.yxuanf.shortlink.project.dao.mapper.ShortLinkMapper;
import org.yxuanf.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.project.dto.req.ShortLinkPageReqDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import org.yxuanf.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import org.yxuanf.shortlink.project.service.ShortLinkService;
import org.yxuanf.shortlink.project.toolkit.HashUtil;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkMapper shortLinkMapper;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        // 生成短链接后缀
        String shortLinkSuffix = generateSuffix(requestParam);
        // 生成完成短链接
        String fullShortUrl = requestParam.getDomain() + "/" + shortLinkSuffix;
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .domain(requestParam.getDomain())
                .originUrl(requestParam.getOriginUrl())
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreatedType())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .shortUri(shortLinkSuffix)
                .enableStatus(0)
                .fullShortUrl(fullShortUrl)
                .build();
        try {
            // 作为兜底策略
            /*
             * 短链接入库成功，但是并没有添加到布隆过滤器中
             * 实际上入库，但布隆过滤器显示短链不存在，此时再次插入该短链不就越过布隆过滤器，然后被唯一索引给拦截了
             */
            baseMapper.insert(shortLinkDO);
            // 唯一索引冲突，查询数据库是否存在
        } catch (DuplicateKeyException ex) {
            // todo 误判短链接如何处理?
            // 1、短链接确实真实存在于缓存中
            // 2、短链接不一定存在于缓存中（redis宕机）
            LambdaQueryWrapper<ShortLinkDO> lqw = new LambdaQueryWrapper<>();
            lqw.eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO hasShortLink = baseMapper.selectOne(lqw);
            // 数据库中确实存在，抛异常
            if (hasShortLink != null) {
                log.warn("短链接{}重复入库", fullShortUrl);
                throw new ServiceException("短链接重复生成");
            }
        }
        // 将完整短链接加入到布隆过滤器
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(shortLinkDO.getFullShortUrl())
                .originUrl(shortLinkDO.getOriginUrl())
                .gid(shortLinkDO.getGid())
                .build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        LambdaQueryWrapper<ShortLinkDO> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus, 0)
                .eq(ShortLinkDO::getDelFlag, 0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        IPage<ShortLinkDO> result = baseMapper.selectPage(requestParam, lqw);
        return result.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        QueryWrapper<ShortLinkDO> qw = new QueryWrapper<>();
        // 需要保证gid唯一
        qw.select("gid as gid, count(*) as shortLinkCount")
                .in("gid", requestParam)
                .eq("enable_status", 0)
                .groupBy("gid");
        List<Map<String, Object>> result = baseMapper.selectMaps(qw);
        return BeanUtil.copyToList(result, ShortLinkGroupCountRespDTO.class);
    }

    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        // 获取原始链接
        String originUrl = requestParam.getOriginUrl();
        String domain = requestParam.getDomain();
        int customGenerateCount = 0;
        String shortUri = null;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接频繁生成，请稍后重试");
            }
            // 防止hash冲突
            originUrl += System.currentTimeMillis();
            // 创建短链接
            shortUri = HashUtil.hashToBase62(originUrl);
            // 若布隆过滤器容量接近饱和，误判概率显著加大（将不存在的数据判定为存在）
            if (!shortUriCreateCachePenetrationBloomFilter.contains(domain + "/" + shortUri)) {
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
    }
}
