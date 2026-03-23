package com.keder.zply

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FavoriteViewModel : ViewModel() {
    // 즐겨찾기 된 houseId들을 담아두는 Set (중복 방지)
    private val _favoriteSet = MutableLiveData<Set<Long>>(emptySet())
    val favoriteSet: LiveData<Set<Long>> get() = _favoriteSet

    // 별을 누를 때마다 추가/삭제를 토글해주는 함수
    fun toggleFavorite(houseId: Long) {
        val currentSet = _favoriteSet.value?.toMutableSet() ?: mutableSetOf()
        if (currentSet.contains(houseId)) {
            currentSet.remove(houseId) // 이미 있으면 삭제 (빈 별)
        } else {
            currentSet.add(houseId)    // 없으면 추가 (채워진 별)
        }
        _favoriteSet.value = currentSet // 모든 화면에 변경사항 즉시 알림!
    }
}