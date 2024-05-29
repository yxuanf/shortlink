package org.yxuanf.shortlink.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.yxuanf.shortlink.admin.service.GroupService;

@RestController
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;
}
