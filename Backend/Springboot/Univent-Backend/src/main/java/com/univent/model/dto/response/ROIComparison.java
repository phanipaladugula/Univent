package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ROIComparison {
    private List<ROIData> rois;
    private String bestValueCollege;
    private String bestRoiCollege;
    private String bestPackageCollege;
}