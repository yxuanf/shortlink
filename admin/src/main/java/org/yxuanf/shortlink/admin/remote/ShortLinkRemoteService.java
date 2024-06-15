package org.yxuanf.shortlink.admin.remote;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.yxuanf.shortlink.admin.common.convention.result.Result;
import org.yxuanf.shortlink.admin.common.convention.result.Results;
import org.yxuanf.shortlink.admin.dto.req.ShortLinkBatchCreateReqDTO;
import org.yxuanf.shortlink.admin.dto.resp.ShortLinkBatchCreateRespDTO;
import org.yxuanf.shortlink.admin.remote.dto.req.*;
import org.yxuanf.shortlink.admin.remote.dto.resp.*;

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

    /**
     * 修改短链接
     *
     * @param requestParam 修改短链接请求参数
     */
    default Result<Void> updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/update", JSON.toJSONString(requestParam));
        return Results.success();
    }

    /**
     * 根据 URL 获取标题
     *
     * @param url 目标网站地址
     * @return 网站标题
     */
    default Result<String> getTitleByUrl(@RequestParam("url") String url) {
        String resultStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/title?url=" + url);
        return JSON.parseObject(resultStr, new TypeReference<>() {
        });
    }

    /**
     * 根据gid以及完整短链接,将短链接丢弃在回收站
     *
     * @param requestParam 请求参数
     */
    default void saveRecycleBin(ShortLinkRecycleBinSaveReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/save", JSON.toJSONString(requestParam));
    }


    /**
     * 查询当前用户的所有分组，根据其创建的分组信息
     * 分页查询回收站短链接
     *
     * @param requestParam 分页查询请求参数
     * @return 短链接分页返回结果
     */
    default Result<IPage<ShortLinkPageRespDTO>> pageRecycleBinShortLink(ShortLinkRecycleBinPageReqDTO requestParam) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("gidList", requestParam.getGidList());
        requestMap.put("current", requestParam.getCurrent());
        requestMap.put("size", requestParam.getSize());
        // 发送http请求
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/page", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 从回收站恢复短链接
     *
     * @param requestParam 恢复短链接请求参数
     */
    default void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/recover", JSON.toJSONString(requestParam));
    }

    /**
     * 移除短链接
     *
     * @param requestParam 短链接移除请求参数
     */
    default void removeRecycleBin(@RequestBody RecycleBinRemoveReqDTO requestParam) {
        HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/recycle-bin/remove", JSON.toJSONString(requestParam));
    }

    /**
     * 批量创建短链接
     *
     * @param requestParam 批量创建短链接请求参数
     * @return 短链接批量创建响应
     */

    default Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(@RequestBody ShortLinkBatchCreateReqDTO requestParam) {
        String result = HttpUtil.post("http://127.0.0.1:8001/api/short-link/v1/create/batch", JSON.toJSONString(requestParam));
        return JSON.parseObject(result, new TypeReference<Result<ShortLinkBatchCreateRespDTO>>() {
        });
    }

    /**
     * 访问单个短链接指定时间内监控数据
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     * @param startDate    开始时间
     * @param endDate      结束时间
     * @return 短链接监控信息
     */
    default Result<ShortLinkStatsRespDTO> oneShortLinkStats(@RequestParam("fullShortUrl") String fullShortUrl,
                                                            @RequestParam("gid") String gid,
                                                            @RequestParam("enableStatus") Integer enableStatus,
                                                            @RequestParam("startDate") String startDate,
                                                            @RequestParam("endDate") String endDate) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("fullShortUrl", fullShortUrl);
        requestMap.put("gid", gid);
        requestMap.put("enableStatus", enableStatus);
        requestMap.put("startDate", startDate);
        requestMap.put("endDate", endDate);
        // 发送http请求
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 访问分组短链接指定时间内监控数据
     *
     * @param gid       分组标识
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @return 分组短链接监控信息
     */
    default Result<ShortLinkStatsRespDTO> groupShortLinkStats(@RequestParam("gid") String gid,
                                                              @RequestParam("startDate") String startDate,
                                                              @RequestParam("endDate") String endDate) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("gid", gid);
        requestMap.put("startDate", startDate);
        requestMap.put("endDate", endDate);
        // 发送http请求
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats/group", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 访问单个短链接指定时间内监控访问记录数据
     *
     * @param fullShortUrl 完整短链接
     * @param gid          分组标识
     * @param startDate    开始时间
     * @param endDate      结束时间
     * @param current      当前页
     * @param size         一页数据量
     * @return 短链接监控访问记录信息
     */
    default Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(@RequestParam("fullShortUrl") String fullShortUrl,
                                                                                       @RequestParam("gid") String gid,
                                                                                       @RequestParam("startDate") String startDate,
                                                                                       @RequestParam("endDate") String endDate,
                                                                                       @RequestParam("enableStatus") Integer enableStatus,
                                                                                       @RequestParam("current") Long current,
                                                                                       @RequestParam("size") Long size) {

        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("fullShortUrl", fullShortUrl);
        requestMap.put("gid", gid);
        requestMap.put("enableStatus", enableStatus);
        requestMap.put("startDate", startDate);
        requestMap.put("endDate", endDate);
        requestMap.put("current", current);
        requestMap.put("size", size);
        // 发送http请求
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats/access-record", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }

    /**
     * 访问分组短链接指定时间内监控访问记录数据
     *
     * @param gid       分组标识
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @param current   当前页
     * @param size      一页数据量
     * @return 分组短链接监控访问记录信息
     */
    default Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(@RequestParam("gid") String gid,
                                                                                            @RequestParam("startDate") String startDate,
                                                                                            @RequestParam("endDate") String endDate,
                                                                                            @RequestParam("current") Long current,
                                                                                            @RequestParam("size") Long size) {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("gid", gid);
        requestMap.put("startDate", startDate);
        requestMap.put("endDate", endDate);
        requestMap.put("current", current);
        requestMap.put("size", size);
        // 发送http请求
        String resultPageStr = HttpUtil.get("http://127.0.0.1:8001/api/short-link/v1/stats/access-record/group", requestMap);
        return JSON.parseObject(resultPageStr, new TypeReference<>() {
        });
    }
}

