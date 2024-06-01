package org.yxuanf.shortlink.admin.remote;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.RequestBody;
import org.yxuanf.shortlink.admin.common.convention.result.Result;
import org.yxuanf.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import org.yxuanf.shortlink.admin.remote.dto.req.ShortLinkPageReqDTO;
import org.yxuanf.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import org.yxuanf.shortlink.admin.remote.dto.resp.ShortLinkGroupCountRespDTO;
import org.yxuanf.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

import java.util.HashMap;
import java.util.List;

/**
 * 短链接中台远程调用服务
 */

public interface ShortLinkRemoteService {
    /**
     * 远程调用创建短链接
     *
     * @param requestParam 创建短链接请求参数
     * @return 创建短链接
     */
    default Result<ShortLinkCreateRespDTO> createShortLink(@RequestBody ShortLinkCreateReqDTO requestParam) {
        String resultBodyStr = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/create", JSON.toJSONString(requestParam));
        return JSON.parseObject(resultBodyStr, new TypeReference<>() {
        });
    }

    /**
     * 远程调用分页查询短链接
     *
     * @param requestParam 分页查询请求参数
     * @return 短链接分页返回结果
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageShortLink(ShortLinkPageReqDTO requestParam) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("gid", requestParam.getGid());
        requestMap.put("current", requestParam.getCurrent());
        requestMap.put("size", requestParam.getSize());
        // 发送http请求
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/page", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 查询短链接分组内数量
     *
     * @param requestParam 查询短链接分组内数量请求参数
     * @return 查询短链接分组内数量响应
     */
    default Result<List<ShortLinkGroupCountRespDTO>> listGroupShortLinkCount(List<String> requestParam) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("requestParam", requestParam);
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/count", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }
}
