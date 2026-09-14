package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.FavoriteCardsStore
import com.guet.liang.stockchat.data.buildRiskMapSnapshot
import com.guet.liang.stockchat.model.RiskMapSnapshot

/** Page-facing port for the saved-card exposure summary. */
internal fun riskMapSnapshot(): RiskMapSnapshot = buildRiskMapSnapshot(FavoriteCardsStore.all())
