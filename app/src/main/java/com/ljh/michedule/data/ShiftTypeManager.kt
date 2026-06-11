package com.ljh.michedule.data

import com.ljh.michedule.data.db.ShiftTypeConfig
import com.ljh.michedule.data.db.ShiftTypeConfigDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShiftTypeManager(
    private val dao: ShiftTypeConfigDao,
    scope: CoroutineScope
) {
    private val _allTypes = MutableStateFlow<List<ShiftTypeConfig>>(emptyList())
    val allTypes: StateFlow<List<ShiftTypeConfig>> = _allTypes.asStateFlow()

    val primaryTypes: StateFlow<List<ShiftTypeConfig>> =
        _allTypes.map { list -> list.filter { it.isPrimary }.sortedBy { it.sortOrder } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val extraTypes: StateFlow<List<ShiftTypeConfig>> =
        _allTypes.map { list -> list.filter { it.isExtra }.sortedBy { it.sortOrder } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val cycleTypes: StateFlow<List<ShiftTypeConfig>> =
        _allTypes.map { list -> list.filter { it.isPrimary }.sortedBy { it.sortOrder } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val typeMap = MutableStateFlow<Map<String, ShiftTypeConfig>>(emptyMap())

    private val _partnerTypes = MutableStateFlow<Map<String, ShiftTypeConfig>>(emptyMap())
    val partnerTypesFlow: StateFlow<Map<String, ShiftTypeConfig>> = _partnerTypes.asStateFlow()

    init {
        scope.launch {
            dao.getAllFlow().collect { list ->
                _allTypes.value = list
                typeMap.value = list.associateBy { it.id }
            }
        }
        scope.launch {
            dao.getPartnerFlow().collect { list ->
                _partnerTypes.value = list.associateBy { it.id }
            }
        }
        scope.launch {
            if (dao.count() == 0) {
                dao.upsertAll(ShiftTypeConfig.DEFAULTS.map { it.copy(owner = "mine") })
            }
        }
    }

    fun getById(id: String): ShiftTypeConfig? =
        typeMap.value[id] ?: _partnerTypes.value[id] ?: ShiftTypeConfig.DEFAULTS.find { it.id == id }

    fun getByIdForPartner(id: String): ShiftTypeConfig? =
        _partnerTypes.value[id] ?: ShiftTypeConfig.DEFAULTS.find { it.id == id } ?: typeMap.value[id]

    fun cycleNext(currentId: String?): String? {
        val cycle = cycleTypes.value
        if (cycle.isEmpty()) return null
        if (currentId == null) return cycle.firstOrNull()?.id
        val idx = cycle.indexOfFirst { it.id == currentId }
        return if (idx < 0 || idx >= cycle.lastIndex) null
        else cycle[idx + 1].id
    }

    suspend fun save(config: ShiftTypeConfig) {
        dao.upsert(config.copy(owner = "mine"))
    }

    suspend fun deleteType(id: String) {
        dao.deleteById(id)
    }

    suspend fun deleteCustom(id: String) {
        dao.deleteCustom(id)
    }

    suspend fun nextSortOrder(): Int =
        (dao.maxSortOrder() ?: -1) + 1

    suspend fun reorder(configs: List<ShiftTypeConfig>) {
        configs.forEachIndexed { index, config ->
            dao.upsert(config.copy(sortOrder = index, owner = "mine"))
        }
    }

    suspend fun replacePartnerTypes(types: List<ShiftTypeConfig>) {
        dao.deleteAllPartner()
        dao.upsertAll(types.map { it.copy(owner = "partner") })
    }
}
