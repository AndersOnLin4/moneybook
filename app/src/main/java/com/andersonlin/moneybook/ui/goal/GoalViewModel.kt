package com.andersonlin.moneybook.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andersonlin.moneybook.data.model.Goal
import com.andersonlin.moneybook.data.repository.GoalRepository
import com.andersonlin.moneybook.data.repository.LedgerRepository
import com.andersonlin.moneybook.data.saving.SavingDepositService
import com.andersonlin.moneybook.util.formatCents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

sealed interface GoalEvent {
    data class ShowMessage(val message: String) : GoalEvent
}

/** 存钱目标：设置目标金额与截止日期，自动计算每月需存，进度可视化；存入一笔自动记「储蓄」支出 */
class GoalViewModel(
    private val goalRepository: GoalRepository,
    private val savingDepositService: SavingDepositService,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    val goals: StateFlow<List<Goal>> = goalRepository.getAllGoals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<GoalEvent>()
    val events = _events.asSharedFlow()

    fun addGoal(name: String, icon: String, targetCents: Long, deadlineEpochDay: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emit(GoalEvent.ShowMessage("目标名称不能为空"))
            return
        }
        if (targetCents <= 0) {
            emit(GoalEvent.ShowMessage("请输入有效目标金额"))
            return
        }
        viewModelScope.launch {
            goalRepository.add(
                Goal(
                    name = trimmed,
                    icon = icon,
                    targetCents = targetCents,
                    deadlineEpochDay = deadlineEpochDay
                )
            )
            _events.emit(GoalEvent.ShowMessage("已创建目标「$trimmed」"))
        }
    }

    fun deposit(goal: Goal, cents: Long) {
        if (cents <= 0) {
            emit(GoalEvent.ShowMessage("请输入有效金额"))
            return
        }
        viewModelScope.launch {
            val ledgerId = ledgerRepository.getActiveLedgerId()
            savingDepositService.deposit(goal, cents, ledgerId)
            _events.emit(
                GoalEvent.ShowMessage(
                    "已存入 ${formatCents(cents)}，并记了一笔「储蓄」支出"
                )
            )
        }
    }

    fun delete(goal: Goal) {
        viewModelScope.launch {
            goalRepository.delete(goal)
            _events.emit(GoalEvent.ShowMessage("已删除目标「${goal.name}」"))
        }
    }

    private fun emit(event: GoalEvent) {
        viewModelScope.launch { _events.emit(event) }
    }
}

/** 目标进度信息（UI 展示用） */
data class GoalProgress(
    val goal: Goal,
    val percent: Float,
    val remainingCents: Long,
    val monthlyNeedCents: Long?,
    val achieved: Boolean
)

fun Goal.toProgress(now: LocalDate = LocalDate.now()): GoalProgress {
    val percent = if (targetCents > 0) {
        (savedCents.toFloat() / targetCents).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remaining = (targetCents - savedCents).coerceAtLeast(0L)
    val achieved = savedCents >= targetCents
    val monthlyNeed = if (achieved) {
        null
    } else {
        val deadline = LocalDate.ofEpochDay(deadlineEpochDay)
        val nowYm = YearMonth.from(now)
        val deadYm = YearMonth.from(deadline)
        val months = ((deadYm.year - nowYm.year) * 12 + (deadYm.monthValue - nowYm.monthValue))
            .coerceAtLeast(1)
        (remaining + months - 1) / months // 向上取整
    }
    return GoalProgress(
        goal = this,
        percent = percent,
        remainingCents = remaining,
        monthlyNeedCents = monthlyNeed,
        achieved = achieved
    )
}
