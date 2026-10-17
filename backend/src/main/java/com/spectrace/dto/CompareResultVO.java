package com.spectrace.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompareResultVO {
    private List<CompareDeviceVO> devices;
    private List<CompareGroupVO> groups;
}
