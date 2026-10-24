package com.spectrace.dto;

import lombok.Data;

/** 系列发现得到的机型项。releaseDate 由 Agent 给出（YYYY-MM-DD），不确定为 null。 */
@Data
public class SeriesModelDTO {
    private String name;
    private String releaseDate;
}
