package com.example.ty.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 作物档案。后台可维护，识别面板与问答均从这里读取，
 * 不再把作物和识别任务写死在代码里。
 */
@Entity
@Table(name = "crop_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CropProfile {

    @Id @Column(length = 40) private String id;
    @Column(nullable = false, length = 60) private String name;
    /** 别名，逗号分隔，用于匹配地块里填写的品种名 */
    @Column(length = 200) private String aliases;
    /** 大田 / 设施 / 果园 */
    @Column(length = 30) private String category;
    /** 生育期，逗号分隔 */
    @Column(length = 200) private String stages;
    /** 支持的识别任务，格式 id:名称，逗号分隔，如 leaf:叶片症状 */
    @Column(length = 300) private String visionTasks;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(nullable = false) private Boolean enabled;
    @Column(nullable = false) private Integer sortOrder;
}
