package com.spectrace.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompareGroupVO {
    private Long groupId;
    private String groupName;
    private List<CompareParamVO> params;
}
