package com.andersonlin.moneybook.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 分类实体
 *
 * @param type      0 = 支出，1 = 收入
 * @param icon      图标（emoji 字符）
 * @param isDefault 是否为内置默认分类（默认分类不可删除）
 * @param sortOrder 显示顺序，新分类排在最后
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: Int,
    val icon: String,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}
