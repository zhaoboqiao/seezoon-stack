package com.seezoon.generator.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.seezoon.generator.constants.InputType;
import com.seezoon.generator.constants.QueryType;
import com.seezoon.generator.constants.TemplateType;
import com.seezoon.generator.constants.db.ColumnDataType;
import com.seezoon.generator.constants.db.ColumnExtra;
import com.seezoon.generator.constants.db.ColumnKey;
import com.seezoon.generator.constants.db.DefaultColumns;
import com.seezoon.generator.dao.GeneratorDao;
import com.seezoon.generator.dto.db.DbTable;
import com.seezoon.generator.dto.db.DbTableColumn;
import com.seezoon.generator.dto.plan.ColumnPlan;
import com.seezoon.generator.dto.plan.TablePlan;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

/**
 * 根据DB信息获得生成方案
 *
 * @author hdf
 */
@Log
@RequiredArgsConstructor
@Service
public class SystemTablePlanMaker {

    /**
     * DB 及表字段的分隔符
     */
    private static final String DB_DELIMITER = "_";
    private static final String[] DEFAULT_NOT_UPDATE_COLUMNS = {"id", "create_by", "create_date"};
    private static final String[] DEFAULT_NOT_LIST_COLUMNS = {"id", "create_by", "create_time", "update_by", "remarks"};

    private final GeneratorDao generatorDao;

    public TablePlan getPlan(String tableName) {
        Assert.hasText(tableName, "tableName must not empty");
        List<DbTable> dbTables = generatorDao.findTable(tableName);
        Assert.notEmpty(dbTables, String.format("can't find tableName:%s", tableName));
        DbTable dbTable = dbTables.get(0);
        List<DbTableColumn> dbTableColumns = generatorDao.findColumnByTableName(tableName);
        return createDefaultTablePlan(dbTable, dbTableColumns);
    }

    private TablePlan createDefaultTablePlan(DbTable dbTable, List<DbTableColumn> dbTableColumns) {
        TablePlan tablePlan = new TablePlan();
        tablePlan.setTableName(dbTable.getName());
        tablePlan.setMenuName(dbTable.getComment());
        // 放入默认的模块名和功能名
        List<String> moduleAndFuntion = extractModuleAndFuntion(dbTable.getName());
        tablePlan.setMenuName(moduleAndFuntion.get(0));
        tablePlan.setFunctionName(moduleAndFuntion.get(1));
        tablePlan.setTemplateType(TemplateType.CRUD);
        tablePlan.setClassName(CaseUtils.toCamelCase(dbTable.getName(), true, DB_DELIMITER.toCharArray()));
        tablePlan.setColumnPlans(this.createColumnPlan(tablePlan, dbTableColumns));
        return tablePlan;
    }

    private List<ColumnPlan> createColumnPlan(TablePlan tablePlan, List<DbTableColumn> dbTableColumns) {
        List<ColumnPlan> columnPlans = new ArrayList<>();
        dbTableColumns.forEach((v) -> {
            // @formatter:off
            ColumnPlan columnPlan = ColumnPlan.builder()
                    .dbColumnName(v.getName())
                    .columnComment(v.getComment())
                    .columnKey(StringUtils.isNotEmpty(v.getColumnKey()) ? ColumnKey.valueOf(v.getColumnKey())
                            : ColumnKey.NONE)
                    .extra(StringUtils.isNotEmpty(v.getExtra()) ? ColumnExtra.valueOf(v.getExtra()) : ColumnExtra.none)
                    .columnType(v.getColumnType())
                    .dataType(ColumnDataType.parse(v.getDataType()))
                    .maxLength(v.getMaxlength())
                    .javaFieldName(CaseUtils.toCamelCase(v.getName(), false, DB_DELIMITER.toCharArray()))
                    .nullable(v.getNullable())
                    .sort(v.getSort())
                    .build();
            // @formatter:on
            // 自增不可插入
            columnPlan.setInsert(!ColumnExtra.auto_increment.equals(columnPlan.getExtra()));
            columnPlan.setUpdate(!ArrayUtils.contains(DEFAULT_NOT_UPDATE_COLUMNS, columnPlan.getDbColumnName()));
            columnPlan.setList(!ArrayUtils.contains(DEFAULT_NOT_LIST_COLUMNS, columnPlan.getDbColumnName()));
            // ID 默认为隐藏域
            if (DefaultColumns.id.name().equals(columnPlan.getDbColumnName())) {
                columnPlan.setInputType(InputType.HIDDEN);
                columnPlan.setList(false);
            }
            // 默认文本域
            if (DefaultColumns.remarks.name().equals(columnPlan.getDbColumnName())) {
                columnPlan.setInputType(InputType.TEXTAREA);
                columnPlan.setList(false);
            }
            // 时间框
            if (Date.class.getSimpleName().equals(columnPlan.getDataType().javaType())) {
                columnPlan.setInputType(InputType.DATE);
                tablePlan.setImportDate(true);
            }
            // jdbcType = LONGVARCHAR的为大文本
            if (ColumnDataType.TEXT.jdbcType().equals(columnPlan.getDataType().jdbcType())) {
                columnPlan.setInputType(InputType.TEXTAREA);
                // 列表默认也不展示
                columnPlan.setList(false);
                tablePlan.setHasBlob(true);
            }
            // 数值字段
            if (!DefaultColumns.id.name().equals(columnPlan.getDbColumnName())) {
                if (ArrayUtils.contains(new String[] {Integer.class.getSimpleName(), Long.class.getSimpleName(),
                    Short.class.getSimpleName()}, columnPlan.getDataType().javaType())) {
                    columnPlan.setInputType(InputType.INTEGRAL_NUMBER);
                } else if (ArrayUtils.contains(new String[] {Float.class.getSimpleName(), Double.class.getSimpleName(),
                    BigDecimal.class.getSimpleName()}, columnPlan.getDataType().javaType())) {
                    columnPlan.setInputType(InputType.DECIMAL);
                    tablePlan.setImportBigDecimal(true);
                }
            }
            // 有索引并且不在默认字段内
            if ((ColumnKey.MUL.equals(columnPlan.getColumnKey()) || ColumnKey.UNI.equals(columnPlan.getColumnKey()))
                && !Arrays.stream(DefaultColumns.values()).map((defaultColumn) -> defaultColumn.name())
                    .collect(Collectors.toList()).contains(columnPlan.getDbColumnName())) {
                columnPlan.setSearch(true);
                columnPlan.setQueryType(QueryType.EQUAL);
                tablePlan.setHasSearch(true);
            }
            columnPlans.add(columnPlan);
        });
        return columnPlans;
    }

    /**
     * 通过表名提取模块名和功能名，按{@link SystemTablePlanMaker#DB_DELIMITER} 拆分为2个，只拆第一个分隔符
     *
     * @param tableName
     *            not null
     * @return
     */
    private List<String> extractModuleAndFuntion(String tableName) {
        String[] splitedTableName = tableName.split(DB_DELIMITER, 2);
        return List.of(splitedTableName[0], splitedTableName.length == 2 ? splitedTableName[1] : splitedTableName[0]);
    }
}
