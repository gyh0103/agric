package com.example.ty.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 病害知识库条目。后台可维护，识别照片时作为参考注入提示词，
 * 智能问农也会引用这里的名称与防治要点。
 */
@Entity
@Table(name = "disease_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DiseaseProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 60) private String crop;
    @Column(nullable = false, length = 100) private String name;
    /** 别名 / 俗称 / 英文代号，逗号分隔 */
    @Column(length = 200) private String aliases;
    @Column(columnDefinition = "TEXT") private String symptoms;
    @Column(columnDefinition = "TEXT") private String advice;
    /** 轻度 / 中度 / 重度 */
    @Column(length = 20) private String severity;
    @Column(nullable = false) private Boolean enabled;
    @Column(nullable = false) private Integer sortOrder;
}
