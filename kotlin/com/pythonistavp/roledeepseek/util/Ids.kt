package com.pythonistavp.roledeepseek.util

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
