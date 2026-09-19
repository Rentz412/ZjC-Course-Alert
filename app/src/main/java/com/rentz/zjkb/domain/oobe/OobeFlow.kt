package com.rentz.zjkb.domain.oobe

/** OOBE 向导的四个步骤，声明顺序即流程顺序（学校写死，无地址步）。 */
enum class OobeStep { Login, Permissions, Reminders, Done }

/**
 * OOBE 步骤机（纯 Kotlin，无 Android 依赖）。
 *
 * 只负责顺序、回退、入口步与进度标签；UI 按 [OobeStep] 渲染，不重复实现流程逻辑。
 */
object OobeFlow {

    val steps: List<OobeStep> = listOf(
        OobeStep.Login,
        OobeStep.Permissions,
        OobeStep.Reminders,
        OobeStep.Done,
    )

    /** 下一步；已在 [OobeStep.Done] 时返回 null。 */
    fun next(step: OobeStep): OobeStep? {
        val index = steps.indexOf(step)
        if (index < 0) return null
        return steps.getOrNull(index + 1)
    }

    /** 上一步；已在 [OobeStep.Login] 时返回 null（交给系统返回键退出）。 */
    fun previous(step: OobeStep): OobeStep? {
        val index = steps.indexOf(step)
        if (index <= 0) return null
        return steps[index - 1]
    }

    /** 自动进入向导时的起始步：无凭据 → 登录步；否则 → 权限步。 */
    fun startStep(hasCredentials: Boolean): OobeStep =
        if (!hasCredentials) OobeStep.Login else OobeStep.Permissions

    /** 进度「第 n 步 / 共 m 步」；完成步不显示进度，返回 null。 */
    fun progressOf(step: OobeStep): Pair<Int, Int>? {
        val index = steps.indexOf(step)
        if (index < 0 || step == OobeStep.Done) return null
        return (index + 1) to (steps.size - 1)
    }
}
