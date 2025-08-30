package com.hollowengineai.mod.actions

/**
 * Базовый класс для действий НПС от LLM
 * 
 * Представляет действие, которое может быть сгенерировано ИИ
 * и выполнено системой через ActionExecutor
 */
data class Action(
    /**
     * Тип действия
     */
    val type: ActionType,
    
    /**
     * Цель действия (может быть null для некоторых действий)
     */
    val target: String? = null,
    
    /**
     * Параметры действия
     */
    val parameters: Map<String, Any> = emptyMap()
) {
    
    /**
     * Получить параметр по ключу с приведением типа
     */
    inline fun <reified T> getParameter(key: String): T? {
        return parameters[key] as? T
    }
    
    /**
     * Получить параметр по ключу с значением по умолчанию
     */
    inline fun <reified T> getParameter(key: String, defaultValue: T): T {
        return getParameter(key) ?: defaultValue
    }
    
    /**
     * Проверить, есть ли параметр с данным ключом
     */
    fun hasParameter(key: String): Boolean {
        return parameters.containsKey(key)
    }
    
    /**
     * Получить описание действия для логирования
     */
    fun getDescription(): String {
        val targetStr = target?.let { " (target: $it)" } ?: ""
        val paramsStr = if (parameters.isNotEmpty()) {
            " with params: ${parameters.keys.joinToString(", ")}"
        } else ""
        
        return "${type.name}$targetStr$paramsStr"
    }
    
    companion object {
        /**
         * Создать Action из строки типа (для обратной совместимости)
         */
        fun fromString(typeString: String, target: String? = null, parameters: Map<String, Any> = emptyMap()): Action {
            val actionType = try {
                ActionType.valueOf(typeString.uppercase())
            } catch (e: IllegalArgumentException) {
                ActionType.CUSTOM // Fallback для неизвестных типов
            }
            return Action(actionType, target, parameters)
        }
    }
}